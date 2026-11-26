# VisionSpace 1.1.0 Production Readiness 计划

- 创建：2026-11-26（M4 实机验收收口后）
- 重构：2026-11-26 → 按工作包（WP）组织，原始十项待办一条不删、证据一条不丢
- 触发来源：`docs/plans/2026-11-24-postgresql-migration.md` 附录 F（F.10 实机发现的 7 个问题、F.11 未覆盖与上线前置、F.12 三条判定）
- 代码基线：`main@491f73a`（PR #166 已合并，PostgreSQL 18 迁移正式收口）
- 适用范围：**1.1.0 首次正式上线**。本计划**独立于** PostgreSQL 迁移收口——迁移本体已通过实机验收并合并，本计划里的每一条都不属于数据库迁移范围。

## 0. 边界（先说清楚不做什么）

| 不做 | 理由 |
|---|---|
| 不引入 pgvector、不做新的 RAG 功能 | 迁移计划 §8 已把它划到下一轮；1.1.0 不带向量检索 |
| 不扩大数据库迁移范围、不改 Flyway 基线 | `V1__baseline.sql` 已冻结（ADR-0004 §1）；迁移本体已收口 |
| 不重复已完成的 PostgreSQL 全量审计 | 审计结论在迁移计划附录 A–F 与 ADR-0004 里，本计划直接引用，不重跑 |
| 不改 API / JSON / 前端契约 | 迁移期已冻结契约；本计划的改动不得改变对外接口形状 |
| 不在本计划内合并 PR、打 Tag、发 Release、放生产流量 | 每一项都需要用户单独授权（见 §4） |

## 1. 判定口径

| 判定 | 含义 | 当前 |
|---|---|---|
| MERGE_READY | 代码可以并进 `main`（与部署、上线无关） | 各 WP 的 PR 各自独立判定 |
| RELEASE_READY | 按手册部署到 `121.199.77.168` 能正常提供服务 | **有条件**（F.12：① 模型额度 ② app.env 补端点键 ③ 存储 domain） |
| PRODUCTION_READY | 可以对外接真实用户 | **否** |

三条判定互不替代：MERGE_READY 不含任何部署承诺；RELEASE_READY 只说"按手册装得上"；PRODUCTION_READY 要求域名、证书、真实存储、账号额度与运维手段全部到位。

## 2. 工作包总览

| 工作包 | 主题 | 覆盖的原始待办 | 主要交付物 | 授权级别 |
|---|---|---|---|---|
| **WP-01** | 部署基础设施收口 | #3 配置侧、#4、#6、#7、#5 配置侧 | 部署模板目录、prod 配置与守卫、生产库初始化方案、模板一致性门禁 | **本轮已授权**：代码、配置模板、部署文档整改 + 隔离验证 |
| **WP-02** | Agent 性能与可靠性专项 | #1、#2 | 首帧延迟根因报告、SSE 首帧前失败的错误处理 | 待授权（独立 PR） |
| **WP-03** | 生产环境与运维保障 | #8、#9、#5 实机侧、#3 外部侧 | 域名与正式证书、定时备份与监控、真实存储桶接入 | 待授权（含付费云资源） |
| **WP-04** | 微信小程序真机验收 | #10 | 小程序凭据、真机验收清单与证据 | 待授权（依赖小程序侧资源） |
| **Release Gate** | 1.1.0 最终发布验收 | 全部 | 最终 HEAD 全量验收 + 实机回归 + 三条判定更新 | 待授权（Tag / Release / 放流量单列） |

依赖关系：

```
WP-01（模板 + 生产库初始化）
   ├──> WP-03（域名证书 / 定时备份 / 真实桶都按 WP-01 的模板铺）
   └──> Release Gate

WP-02（Agent 专项，与 WP-01/03 无耦合，可并行）

WP-03（正式域名 + 证书）
   └──> WP-04（小程序合法域名必须是正式 HTTPS 域名）
            └──> Release Gate
```

## 3. 原始十项待办 → 工作包映射（证据与完成标准保留）

> 下表的「现状（实机实测）」逐字来自迁移计划附录 F，未改写；工作包只是重新归口。

| 原 # | 事项 | 现状（实机实测） | 工作包 |
|---|---|---|---|
| 1 | Agent 首帧延迟专项定位 | 首帧 28–30 秒（一次 65.6 秒）；同供应商 `curl -N` 直连 1.5 秒出首包 → 延迟在应用内、模型调用之前；同期日志只有 `Agent 流式对话请求` 一行，无工具/节点级日志可归因 | WP-02 |
| 2 | SSE 首帧前失败的错误处理 | 模型在首帧前失败时客户端收到 **HTTP 500 空体**（`HttpMessageNotWritableException`，响应已声明 `text/event-stream`）；`AgentController`/`GlobalExceptionHandler` 相对 `main` 无改动 → 既有行为 | WP-02 |
| 3 | 模型供应商额度与配置 | DashScope 免费额度耗尽（`403 AllocationQuota.FreeTierOnly`），兼容模式与原生端点同样 403；`app.env` 缺 `SPRING_AI_OPENAI_BASE_URL` 时会打 `api.openai.com`（大陆不可达） | 配置侧 → WP-01；额度与供应商 → WP-03 |
| 4 | 部署模板完整性 | 实机 `app.env` 24 键；`.env.example` 用的是 compose 口径（`BACKEND_PORT`/`POSTGRES_*`/`REDIS_PASSWORD`），原生部署用 `SPRING_DATASOURCE_*`/`SPRING_DATA_REDIS_PASSWORD`；缺 `SPRING_MVC_ASYNC_REQUEST_TIMEOUT=300000`（与默认 30 秒异步超时撞车） | WP-01 |
| 5 | 真实 COS/OSS 与 domain 配置校验 | 验收期用 moto 顶 S3；存储配置缺 `domain` 时产出 `null` 前缀 URL（`nullacceptance//space/...`），预览失败 | 配置与文档侧 → WP-01；真实桶与域名 → WP-03 |
| 6 | 8081 回环绑定 | 应用绑 `*:8081`；外网直连实测不可达（公网入口只放行 `80`/`22`），但纵深防御有缺口 | WP-01 |
| 7 | 验收数据清理及生产库初始化 | 验收库 `vision_space` 里留着 4 个账号、1 个空间、38 张图、10 条 agent session；恢复库已删、moto 已停 | WP-01（**新建独立空库**，验收库保留备份、不删） |
| 8 | 公网域名、正式 HTTPS 证书 | 只有自签证书（CN=`visionspace.space`，30 天），`:80` 是 nginx 默认站点；`:443` 未开 | WP-03 |
| 9 | Redis 持久化、定时备份及监控 | Redis 8.0.5：`requirepass`、`maxmemory 64mb`、`noeviction`、`appendonly no`；PG 备份只演练了手工一次（248 KB dump） | WP-03 |
| 10 | 微信小程序凭据与真机验收（若纳入首发） | 验收机 `app.env` 无 `VISION_SPACE_WX_APPID`/`VISION_SPACE_WX_SECRET`，微信登录链路本轮绕开 | WP-04 |

## 4. 授权边界（外部操作单独列，需逐项授权）

以下操作**不在任何工作包的默认授权范围内**，必须由用户单独批准后才执行：

| 编号 | 外部操作 | 涉及 | 前置条件 | 归属 |
|---|---|---|---|---|
| A1 | 更新验收机 `121.199.77.168` 的 `/opt/visionspace/app.env`（补模型端点键等）并重启应用 | 生产配置变更、真实主机 | WP-01 合并后 + 变更前后各一次 readiness 记录 | WP-01 落地 |
| A2 | 在验收机创建独立生产库 `vision_space_prod`、跑 Flyway、切应用连接串 | 真实数据库写入 | WP-01 脚本先在隔离环境验证通过 + 验收库已备份 | WP-01 落地 |
| A3 | 清空或删除验收库 `vision_space` 的验收数据 | 数据清理 | **本轮明确不授权**；只保留备份 | — |
| A4 | 开真实 COS/OSS 桶与公开域名（付费资源） | 云服务付费 | 桶名、区域、公开域名、成本上限确认 | WP-03 |
| A5 | DNS 解析变更 + certbot 签发正式证书 | DNS、证书 | 备案状态确认 + 域名归属确认 | WP-03 |
| A6 | DashScope 额度充值或更换模型供应商 key | 付费 / 凭据 | 预算与供应商决定 | WP-03 |
| A7 | 变更 Redis 持久化策略、启用定时备份与告警 | 生产运维配置 | 备份落盘位置与保留策略确认 | WP-03 |
| A8 | 写入微信小程序 `AppID`/`AppSecret`、配合法域名、真机验收 | 小程序凭据 | 微信后台权限 + 备案 | WP-04 |
| A9 | 创建 v1.1.0 Tag、发布 GitHub Release、开放生产流量 | 发布动作 | Release Gate 全绿 + 用户显式授权 | Release Gate |

规则：口令与密钥只经环境变量 / `app.env`（600）注入，**不进 argv、不进日志、不进仓库**；任何脚本在需要超级用户时用 `sudo -u postgres`（走 peer 认证，不传口令）。

## 5. WP-01 部署基础设施收口

### 5.1 范围

把「照手册在干净机器上装一遍」这件事从**散文**变成**仓库里的模板 + 机器可校验的一致性门禁**，并给出生产数据库的初始化方案（独立空库）。本 WP 只动仓库内文件与隔离环境，**不碰真实主机**。

覆盖：原 #3（配置侧）、#4、#5（配置与文档侧）、#6、#7。

### 5.2 交付物

| 文件 | 作用 |
|---|---|
| `deploy/README.md` | 键名对照表（compose 口径 / 原生口径 / Spring 属性名）、发布顺序、授权边界、故障排查 |
| `deploy/app.env.example` | 原生部署环境变量模板（全部必需键 + 可选键），占位说明 |
| `deploy/visionspace.service` | systemd unit 模板（回环绑定、JVM 参数、`EnvironmentFile` 600） |
| `deploy/nginx-visionspace.conf` | nginx 站点模板（80 跳转 / 静态 / `/api` 反代 / SSE 长连接） |
| `deploy/postgresql-production.conf` | PG 18 低资源参数片段（与迁移计划《2026-11-24 MySQL→PostgreSQL 迁移计划》§5.9 对齐） |
| `deploy/init-production-db.sh` | **独立空库**初始化：建角色、建库、装扩展、写授权；不碰任何既有库 |
| `deploy/flyway-migrate.sh` | 对目标库执行 Flyway `migrate` + `validate`（口令走 `FLYWAY_PASSWORD`） |
| `deploy/postflight-check.sh` | 部署后自检：监听面、readiness、运行角色、Flyway 状态 |
| `deploy/backup-db.sh` | `pg_dump -Fc` 备份（产物 600）；只读，不删旧备份 |
| `scripts/tests/test-deploy-templates.py` | **模板一致性门禁**：键名对照表由机器校验，模板与代码漂移即红 |
| `scripts/tests/test-production-db-init.py` | **生产库初始化隔离验证**：Docker PG 18.7 上跑通「验收库 → 独立空库」，并证明验收库未受影响 |

代码与配置改动：

| 改动 | 理由（对应实机发现） |
|---|---|
| `application-prod.yml`：`spring.ai.openai.base-url` 默认指向 DashScope 兼容模式、`chat.options.model` 默认 `qwen-plus` | 原 #3：缺 `SPRING_AI_OPENAI_BASE_URL` 时打 `api.openai.com`，大陆不可达，同步调用挂到超时、SSE 一个字节都回不来 |
| `application-prod.yml`：`server.address` 默认 `127.0.0.1`（compose 显式覆盖为 `0.0.0.0`） | 原 #6：应用绑 `*:8081`，纵深防御缺口 |
| `application-prod.yml`：`spring.mvc.async.request-timeout` 默认 `300000` | 原 #4：默认 30 秒异步超时与 SSE 首帧正面撞车。**这是止血兜底，不是首帧延迟的解决方案**——根因定位归 WP-02 |
| `ProductionSecretsGuard`：把「模型端点」与「模型名」纳入生产必填校验 | 原 #3：手册漏键没有任何机器拦截，缺了要等到线上才发现 |
| `docker-compose.yml`：backend 显式 `SERVER_ADDRESS=0.0.0.0` | 容器内必须监听所有接口才能被端口映射；宿主机侧仍只发布到 `127.0.0.1` |
| `.env.example` / `deploy/app.env.example`：两套口径的键名对照表与模板 | 原 #4：`.env.example` 是 compose 口径，原生部署照抄必然缺键 |
| `docs/plans/2026-11-24-aliyun-deploy.md`：改为引用 `deploy/` 模板，补独立空库流程与验收库保留策略 | 原 #7 / 原 #4 |
| `docs/adr/0005-production-deployment-contract.md` | 把「prod profile 的监听面、模型端点、模板一致性」写成长期契约 |

### 5.3 生产数据库初始化方案（对应要求：优先独立空库、保留验收库备份）

现状：验收库 `vision_space` 里留着 4 个账号、1 个空间、38 张图、10 条 agent session（迁移计划 F.5/F.11）。这批数据**不是**生产数据，但混在唯一那个库里，一旦直接当生产库用就等于把测试数据带上线。

方案（三步，全部可回退）：

1. **备份**：`deploy/backup-db.sh` 对 `vision_space` 做一次 `pg_dump -Fc`（运行角色），落到 `/opt/visionspace/backup/`，权限 `600`，**保留不删**。它是「验收期证据 + 回退锚点」，即使之后清库也要留着。
2. **独立空库**：用 `deploy/init-production-db.sh` 建 `vision_space_prod`（owner `vision_migrator`，`REVOKE CREATE ON SCHEMA public FROM PUBLIC`，`GRANT USAGE ON SCHEMA public TO vision_app`，`CREATE EXTENSION pgcrypto`，`ALTER DATABASE ... SET timezone='Asia/Shanghai'`）。**不 drop、不 truncate、不改 `vision_space`**。
3. **建表与切换**：`deploy/flyway-migrate.sh` 对 `vision_space_prod` 跑 `migrate` + `validate`（31 张业务表 + `flyway_schema_history`）；应用连接串切到 `vision_space_prod`，readiness UP 后再清理验收库（清理属 A3，本轮不授权）。

边界：`vision_space` 与 `vision_space_prod` **并存**是本阶段的合法状态；验收库的清理由 A3 单独授权，WP-01 不代劳。

### 5.4 验收标准

| 项 | 标准 |
|---|---|
| 模板一致性 | `python -I scripts/tests/test-deploy-templates.py` 通过：守卫要求的每个键都在 `deploy/app.env.example` 与 `.env.example` 里出现；模板里没有未登记的键；没有重复键；模板不含真实值 |
| 生产库初始化 | `python -I scripts/tests/test-production-db-init.py` 通过：隔离 PG 18.7 上「验收库（有数据）→ 独立空库」成功；空库业务表数与迁移目录一致（从 `V1__baseline.sql` 数出来，不写死 31）+ `flyway_schema_history` 一行；`vision_app` 可 `SELECT`；`vision_app` 执行 DDL 报 SQLSTATE `42501`；**验收库行数与表数不变**；干净集群里脚本自建角色且新口令能登录 |
| 后端回归 | `python -I scripts/drill-isolated-run.py --test ProductionSecretsGuardTest` 通过；守卫新增必填项有对应测试 |
| 容器路径 | `scripts/container-verification.ps1` 在 prod profile 下仍能起（compose 的 `SERVER_ADDRESS` 覆盖生效、模型端点键已注入） |
| 最终 HEAD | CI 六个作业全绿（不复用旧 HEAD 结果） |
| 文档 | 部署手册的步骤可被模板逐条对应；键名对照表与模板一致（由门禁保证） |

### 5.5 PR 边界与授权边界

- **PR 边界**：只含 `deploy/`、`scripts/tests/`（两个新门禁）、`scripts/container-verification.ps1`（注入新键，让容器口径也带上模型端点）、`application-prod.yml`、`ProductionSecretsGuard(+Test)`、`docker-compose.yml`、`.env.example`、`SECRETS.md`（守卫必填项与报错文案变更的说明）、`docs/adr/0005`、`docs/plans/*`（部署手册与本计划）。不含真实主机变更、真实云资源、DNS、证书、凭据轮换、验收库删除、Tag/Release/生产流量。
- **授权边界**：本 WP 的**代码、模板、文档、隔离验证**已获授权；A1–A3 未获授权，不得执行。

### 5.6 本 WP 明确不做

- 不改存储候选校验去强制 `domain`（会改 API 行为；真实桶接入前，"私有桶直连 endpoint 还是必须公开域名"还没有定论）→ 只在部署前置清单里把 `domain` 登记为生产必填，并在 WP-03 用真实桶验证。
- 不引入新的 Flyway 迁移（基线已冻结）。
- 不在验收机上做任何写操作。

### 5.7 本轮实施结果（2026-11-26）

| 项 | 结果 |
|---|---|
| 部署模板 | `deploy/` 九个文件入库：环境变量模板、systemd、nginx、PG 参数、初始化脚本、迁移脚本、自检脚本、备份脚本、说明 |
| 模板一致性门禁 | `python -I scripts/tests/test-deploy-templates.py` → PASS（守卫 13 个必填项在两套模板与 compose 里都有；模板无未登记键；关键默认值齐全） |
| 生产库初始化隔离验证 | `python -I scripts/tests/test-production-db-init.py` → PASS：独立空库业务表数与迁移目录一致 + Flyway 历史 1 行；`vision_app` 可读、建表被拒且 SQLSTATE 为 `42501`；**验收库行数/表数/开关行一条没变**；对已存在的库再跑一次按预期拒绝；干净集群里角色由脚本创建、新口令能登录 |
| 守卫回归 | `ProductionSecretsGuardTest` 8 项通过（新增「未替换的 `<占位>` 必须拒绝启动」「显式空值按 `(blank)` 报告」两个用例；夹具改为断言覆盖守卫的全部键，守卫以后新增必填项不会再悄悄逃过测试） |
| 秘密扫描 | `python -I scripts/tests/verify-secret-scan.py` → 1153 个候选文件、0 命中 |
| 仓库护栏 | `check-repo-hygiene.py`、`test-pg-test-coverage-map.py` → PASS（132 个基线测试类全部有下落） |
| 独立评审 | Standards 轴与 Spec 轴各跑一遍子代理评审（对照 `CODING_STANDARDS.md` 与本计划 WP-01 / ADR-0005），结论已全部处理，见下方「评审发现与处置」 |
| 未做（等授权） | 验收机 `app.env` 更新（A1）、实机建独立生产库与切连接串（A2）、验收库清理（A3，本轮明确不授权） |
| 待补 | 最终 HEAD 的 CI 六个作业全绿（见 §11） |

评审发现与处置（2026-11-26 第二轮修订）：

| 发现 | 处置 |
|---|---|
| 守卫新增的两个模型键在 `application-prod.yml` 里有默认值，永远不会是「missing」，语义自相矛盾 | 保留默认值（可用的默认值比强制配置更稳），把守卫语义改清楚：它挡的是**显式置空**；报错区分 `(missing)` / `(blank)`。ADR-0005 §2、SECRETS.md §一同步改写 |
| 角色口令被插值进 SQL 文本 | 改为环境变量 + psql `\getenv`，口令不进 SQL 文本、不进 argv；隔离验证里新增「脚本输出不含口令」「新口令能登录」两条断言 |
| `postflight-check.sh` 的 DDL 负例会真的建表（权限失效时留下对象） | 整段包进 `BEGIN; … ROLLBACK;`，并加 `ON_ERROR_STOP=1` |
| `postflight-check.sh` 写死「31 张表」 | 期望值改为从迁移目录数 `CREATE TABLE`，加了 V2 迁移不用改脚本 |
| `postflight-check.sh` 头注释写「不修改任何状态」 | 改成「只读检查」，并说明 DDL 负例在事务里回滚 |
| `init-production-db.sh` 的时区与库注释未校验就进 SQL | 时区卡字符集、库注释转义并限长 |
| `deploy/README.md` 声称口令放在 `/root/.vs-secrets/*.pw`，且 `flyway.conf` 存口令 | 改成事实：`flyway.conf` 只放 URL 与用户，口令走 `FLYWAY_PASSWORD` 环境变量 |
| `deploy/README.md` 缺授权边界与故障排查 | 补 §6 授权边界表与 §7 故障排查表 |
| 备份步骤只在文档里，没有落地的脚本 | 新增 `deploy/backup-db.sh`（`pg_dump -Fc`、600、只读不清理） |
| 隔离验证里的表数写死 31、DDL 负例只断言「permission denied」、异常退出码 99、角色创建分支从未被执行 | 期望值从 `V1__baseline.sql` 数出；断言 SQLSTATE `42501`；异常退出码改 2（仓库约定）；新增干净集群容器专门跑「角色由脚本创建」分支 |
| `FLYWAY_PASSWORD=<迁移角色口令>` 这类示例把口令写进命令行（会进 shell 历史） | 文档与脚本提示统一改成 `read -rs` 读进环境变量 |

落地时的两个注意点：

1. `application-prod.yml` 给模型端点与模型名都带了可达的默认值（DashScope 兼容模式 + `qwen-plus`），所以**验收机上现有的 `app.env` 不会因为这次改动而起不来**；但显式写进 `app.env` 仍然是应该做的（A1）。
2. `prod` profile 的业务端口默认改成绑回环。验收机上的 nginx 反代目标是 `127.0.0.1:8081`，不受影响；但如果将来有别的机器直连业务端口，需要显式设置 `SERVER_ADDRESS`。

## 6. WP-02 Agent 性能与可靠性专项

覆盖原 #1、#2。与 WP-01/03 无耦合，可并行。

### 6.1 两种延迟必须分开定义与分开测（对应要求）

| 指标 | 定义 | 为什么必须分开 |
|---|---|---|
| `T_status` | 客户端发出请求 → 收到**第一个 SSE 帧**（任何帧，含状态帧、心跳帧） | 这是「链路是否活着」的信号 |
| `T_content` | 客户端发出请求 → 收到**第一段有效模型内容**（首个非空文本 delta） | 这才是用户感知的「多久有反应」 |

迁移计划 F.10 记录的 28–30 秒是「客户端收到首帧」的口径，**尚未判定它属于 `T_status` 还是 `T_content`**——这正是 WP-02 要先回答的第一个问题。埋点与统计必须分别给出 `T_status` 与 `T_content` 的 P50/P95，不能只报一个「首帧延迟」。

### 6.2 首帧延迟根因定位（对应要求：不能只加超时）

阶段切分（每一段都要打点，输出耗时）：

```
T0 请求进入 → T1 会话/线程建立(DB) → T2 附件绑定(DB+对象存储) → T3 MCP 初始化
   → T4 检索/工具准备 → T5 提示词装配 → T6 模型请求发出 → T7 模型首字节
   → T8 首段有效内容 → T9 done
```

- 交付物：分段埋点 + 一份归因报告（28–30 s 落在哪一段、占多少、其余段各多少）。
- 对照实验：同供应商 `curl -N` 直连 1.5 秒出首包（M4 已测），说明 28–30 s 发生在**应用内、模型调用之前**——需要用埋点把它收敛到具体阶段，而不是靠"模型慢"解释。
- 验收标准：
  1. 给出 28–30 s 的**具体归因**（阶段 + 数值），不是猜测；
  2. 给出 `T_status` / `T_content` 各自的 P50/P95 基线（在 2 vCPU / 1.7 GB 同款机器上复测，或明确说明口径差异）；
  3. 提出修复方案并验证；**增加超时时间只能作为兜底，不得作为解决方案**——`SPRING_MVC_ASYNC_REQUEST_TIMEOUT` 已在 WP-01 作为止血默认值写入模板，WP-02 必须说明它掩盖了什么、修复后可以收回到多少；
  4. 埋点本身不把敏感信息写进日志（沿用 F.9 的结论：日志只出现属性名）。

### 6.3 SSE 首帧前失败的错误处理

现状：模型在首帧前失败 → 客户端收到 **HTTP 500 空体**（响应已声明 `text/event-stream`，`HttpMessageNotWritableException`），前端拿不到原因。

- 要求：首帧前失败必须让客户端拿到**可解析**的错误——优先在响应提交前把可失败的前置动作做完（失败走 JSON 错误体）；响应已提交的场景走 SSE `error` 帧（`messageType=error`）。
- 验收标准：
  1. 故障注入用例（模型在首帧前抛错）→ 客户端收到 JSON 错误体或 SSE `error` 帧，**不是空体 500**；
  2. 前端能显示失败原因（不是"出错了"这种无信息文案）；
  3. 既有 SSE 正常路径无回归（帧序、末帧 `done` 不变）。

### 6.4 PR 边界与授权边界

- **PR 边界**：Agent 服务端代码 + 前端错误展示 + 对应测试；不改数据库、不改部署模板、不动真实主机上的模型配置。
- **授权边界**：故障注入只用测试替身，不消耗真实模型额度；如需真实供应商复测，走 A6。

## 7. WP-03 生产环境与运维保障

覆盖原 #8、#9、#5 实机侧、#3 外部侧。

| 子项 | 内容 | 验收标准 | 需要的授权 |
|---|---|---|---|
| 域名与证书 | DNS 解析到位、certbot 签发正式证书、强制 HTTPS、`:80` 只做跳转 | `curl -I https://<域名>` 返回 301/200；证书链核验（有效期、签发者、SAN）；小程序与 Web 都能用 | A5 |
| Redis 持久化 | AOF 或 RDB + 周期快照定案 | 重启后会话/检查点不丢；`maxmemory` 与 `noeviction` 策略不被破坏；磁盘增量在预算内 | A7 |
| 定时备份 | 每日 `pg_dump -Fc` + 保留 7 天 + 异地一份 | 定时任务真实跑通一次（不是手工跑一次）；备份文件可恢复（按 F.7 的演练口径再跑一遍）；保留期清理生效 | A7 |
| 备份失败告警 | 备份/恢复失败的发现手段 | 失败能被发现（告警通道或至少非零退出 + 明确日志），不是"跑完就算" | A7 |
| 监控基线 | 按 F.6/F.8 的指标建立日常基线 | 至少覆盖：JVM RSS、PG RSS/连接数、Redis 内存、整机 swap、OOM 计数、readiness | A7 |
| 真实对象存储 | 真实 COS/OSS 桶 + 公开 `domain` 配好 | 端到端上传 → 预览 → 缩略图 → 删除，真实请求留证；`picture.url` 以真实域名开头，不出现 `null` 前缀 | A4 |
| 模型供应商 | 额度充足或换供应商 | 真实一轮对话返回内容；记录 30 天额度余量；`base-url` / `model` 与实际供应商一致 | A6 |

风险提示：`Redis appendonly` 从 `no` 改开会增加内存与磁盘写入；1.7 GB 机器上必须按 F.8 的 RSS 基线复测后再定。

## 8. WP-04 微信小程序真机验收

覆盖原 #10。

- 前置：小程序 `AppID`/`AppSecret` 就位（A8）、正式域名与证书就位（WP-03）、微信后台配置 request / upload / download 合法域名（必须 HTTPS 且已备案）。
- 清单：登录、浏览、上传、预览、Agent 对话、失败提示（额度不足/凭据错误时的文案）、越权访问表现。
- 验收标准：真机逐条留证（截图/录屏 + 服务端请求日志），失败项有明确根因与处置结论。
- **独立发布条件**：小程序可以滞后于 Web 首发——只要 Web 侧 WP-01/02/03 闭合、Release Gate 通过，就可以先发 Web；小程序作为 1.1.0 的**可选增量**，其缺口不阻塞 Web 首发，但要写进发布说明。

## 9. Release Gate：1.1.0 最终发布验收

输入：各 WP 的结论。未闭合项必须二选一：**闭合**，或**明确降级**（例如小程序不随 1.1.0 发布）。

| 检查 | 内容 |
|---|---|
| 代码 | 最终 HEAD 上 CI 六个作业全绿（security / browser / container / acceptance / evidence / SRF merge gate），**不复用旧 HEAD 结果**；每个 WP 的 PR 独立评审通过 |
| 迁移基线 | Flyway `validate` / `info` 绿；`V1__baseline.sql` 与已发布版本一致（ADR-0004 §1 的冻结要求） |
| 实机回归 | 按 `deploy/postflight-check.sh` 全项通过：监听面、readiness、运行角色最小权限、备份恢复一次、关键业务路径 |
| 三条判定 | 更新迁移计划附录 F 的 F.12 与本计划 §1，给出 MERGE_READY / RELEASE_READY / PRODUCTION_READY 的最终结论与依据 |
| 发布动作 | 创建 v1.1.0 Tag、发布 GitHub Release、开放生产流量——**全部单列授权（A9）**，Release Gate 通过不等于自动发布 |

Web-only 首发路径：若小程序（WP-04）或真实存储（WP-03 的存储子项）未闭合，允许先发 Web，但生产存储必须是真实桶（不能用 moto 替身上线），且发布说明里写明小程序不在首发范围。

## 10. 复验要求

任何工作包做完改动后：

- 涉及后端代码：重跑 `pwsh -File ./scripts/acceptance.ps1`（工作树内 `python -I scripts/drill-isolated-run.py --full`），并让 CI 在最终 HEAD 上全绿；
- 涉及部署配置：在隔离环境重跑对应门禁（WP-01 的两个 `scripts/tests/test-*.py`），并在 `121.199.77.168` 上复跑对应实机项（需 A1/A2 授权），证据按 `docs/plans/evidence/2026-11-25-m4/` 的同一格式归档；
- 全部闭合后更新迁移计划附录 F 的 F.12 与本计划 §1，再申请 PRODUCTION_READY 与放生产流量的授权。

## 11. 本轮授权范围（2026-11-26）

- 已授权：计划完善（本文件）、WP-01 的代码、配置模板、部署文档整改，以及隔离验证（本地 Docker，不连真实主机）。
- 未授权：A1–A9 全部外部操作；v1.1.0 Tag；GitHub Release；生产流量；删除验收数据库；其余三个工作包的实现。
