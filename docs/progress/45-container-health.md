# #45 最小权限容器与健康部署

工单：[45](https://github.com/yuwen773/VisionSpace/issues/45)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26)。
分支：`codex/45-container-health`。本文只记录实际执行到的证据，未执行的部分单列在「未验证范围」。

## 改动结构

| 位置 | 作用 |
| --- | --- |
| `Dockerfile` | 非 root 专用 UID 10001 运行；`/app` 与 jar 属主 root、755（运行身份可读可遍历、不可写）；可写目录仅 `/app/tmp`（770，属主 app）；HEALTHCHECK 走管理端口的 readiness；EXPOSE 只声明业务端口；`-XX:MaxRAMPercentage=75`（容器内存边界，工单未要求，属部署稳健性默认，可按需移除） |
| `docker-compose.yml` | MySQL/Redis 不再发布任何宿主端口，只留在 `internal: true` 网络（无外联出口）；backend 端口只绑 `127.0.0.1`（LAN 不能再直连应用，放宽须运维显式改）；三方 healthcheck + `depends_on: service_healthy`；应用改用受限账号 `vision_app`（非 root）；必需秘密全部 `:?required` 显式失败；profile 默认 `prod`；MySQL 探活用 `healthcheck` 账号真正认证执行 `SELECT 1`（`mysqladmin ping` 在拒绝访问时仍返回 0，不能证明账号可用） |
| `docker/mysql/init/01-create-app-user.sh` | 首次初始化创建 `vision_app`（业务 DML 权限）与 `healthcheck`（仅连接权）账号；口令来自容器环境变量，不落盘；嵌入 SQL 前转义反斜杠与单引号；**不开启 `set -u`**（脚本被 entrypoint source 进同一 shell，nounset 会让 entrypoint 的 `_mysql_passfile` 在 `$1` 处失败）；探活账号按实际客户端主机 `127.0.0.1` 创建 |
| `docker-compose.host-access.yml` | 显式宿主访问覆盖文件：仅 `127.0.0.1` + 非默认端口（13306 / 16379）；默认栈不含任何宿主 DB/Redis 发布 |
| `application-prod.yml` | 管理端点独立端口 9081 且只绑 127.0.0.1；只暴露 health；`show-details/show-components: never`；readiness 组纳入 `db,redis`；Hikari `initialization-fail-timeout: 30000` 提供有限启动重试窗口：依赖延迟最多等 30 秒，耗尽即明确异常退出；Redis 健康检查超时 1 秒，故障时 readiness 秒级返回 503；MyBatis 改为 `NoLoggingImpl` |
| `application.yml` | 补上 `vision-space.vision-recognition.base-url/model` 的非秘密默认值（可用环境变量覆盖）。这两个键此前只在被忽略的 `application-local/server.yml` 里，prod 启动会因占位符无法解析而失败 |
| `pom.xml` | 新增 `spring-boot-starter-actuator`（唯一依赖变更） |
| `scripts/container-verification.ps1` | 本票专用隔离验证：唯一 Compose project + 随机回环端口 + 随机秘密；启动顺序为先 mysql/redis → 以 root 导入业务表（复用 #27 的 DDL 提取约定；建表数校验改为「提取数 == 源文件声明数」，表结构演进不再阻塞）→ 再启动 backend；A1–A4 断言逐条产出证据；失败时保留三方日志与 `compose ps`，清理前也保留完整日志 |
| `scripts/tests/test-container-verification.ps1` | 容器验证脚本的控制流回归（8 个场景，含预期拒绝），不需要 Docker，随 `acceptance.ps1` 的 `runner-tests` 关卡执行 |
| `.github/workflows/container-verification.yml` | 在 `ubuntu-24.04` 上实际构建并运行隔离候选；成功或失败都上传 `container-verification-evidence` |
| `HttpHealthEndpointTest` | 健康端点契约回归：业务健康不含依赖细节；actuator health 为 UP 且不泄露组件与连接信息 |
| `scripts/acceptance.ps1` | `runner-tests` 关卡追加执行容器验证脚本的控制流回归 |
| `.gitignore` | 追加 `.env.secrets*`（密码代理文件，含真实值，禁止提交） |

## 运行时验收（2026-10-28）

本机 Docker daemon 不可用（`docker info` 连 `dockerDesktopLinuxEngine` 命名管道失败，机器可用内存约 1.5 GB），因此把同一脚本作为独立 workflow 在 GitHub Actions 的 `ubuntu-24.04` 上实际构建并启动隔离候选。这不是 `compose config` 静态检查，也不是「镜像构建成功」：

| 项 | 值 |
| --- | --- |
| 工作流 | [Container candidate verification run 40645057983](https://github.com/yuwen773/VisionSpace/actions/runs/40645057983) |
| 提交 | `1c5b222` |
| 环境 | `ubuntu-24.04` runner，Docker + Compose；MySQL 8.0.46、Redis 7.4.12 |
| 结果 | 26 项断言 **ALL PASS**，退出 0 |
| 产物 | `container-verification-evidence`（backend/mysql/redis 完整日志、schema.sql、各步骤日志） |

实际通过项：

| 组 | 断言 |
| --- | --- |
| 建表 | `schema-bootstrap`（22 张表） |
| A1 身份与写权限 | `A1-backend-nonroot-uid`（uid 10001）、`A1-tmp-writable`、`A1-app-readonly`、`A1-root-readonly` |
| A2 端口与网络 | `A2-db-redis-not-published`、`A2-backend-loopback-only`、`A2-internal-network-isolated`（`Internal=true`）、`A2-db-redis-not-published-any` |
| A3 配置与日志 | `A3-prod-profile-active`、`A3-health-no-secrets`、`A3-no-sql-param-logging`、`A3-no-secret-in-logs` |
| A4 健康与依赖故障 | `A4-healthy-before-failure`、`A4-readiness-down-on-mysql-stop`、`A4-recovers-after-mysql-restart`、`A4-readiness-down-on-redis-stop`、`A4-recovers-after-redis-restart`、`A4-late-connection-attempt-observed`、`A4-late-same-process-waiting`、`A4-late-mysql-stopped`、`A4-late-dependency-recovers`、`A4-late-same-process-recovered`、`A4-exhausted-retries-fail-clearly`、`A4-exhausted-mysql-stopped`、`A4-exhausted-redis-stopped` |

同一提交的验收基线（[run 40645058004](https://github.com/yuwen773/VisionSpace/actions/runs/40645058004)）也通过：原生隔离 MySQL/Redis、后端全部测试与打包、前端安装/格式/类型/测试/构建、容器验证脚本的 8 个控制流回归、资源清理。

## 验收对照

| 工单验收项 | 证据 | 结果 |
| --- | --- | --- |
| 45.1 数据库/Redis 默认内部网络，宿主访问需显式受限配置 | A2 组实测：两个服务无任何端口发布，`internal` 网络 `Internal=true`，backend 只发布 `127.0.0.1:<随机端口>`。`docker-compose.host-access.yml` 以 `compose config` 核对：mysql `127.0.0.1:13306`、redis `127.0.0.1:16379`，仅回环 + 非默认端口 | 已覆盖（默认栈为运行证据；覆盖文件为静态渲染核对，未实际启动） |
| 45.2 非 root、可写目录受限、生产 profile 明确、敏感 SQL 参数日志关闭 | A1：实际 `id -u` 为 10001，`/app/tmp` 可写，`/app` 与 `/` 写入被拒；A3：容器内 `SPRING_PROFILES_ACTIVE=prod`，触发真实注册写库后日志无 `Preparing:`/`Parameters:`，响应与日志均不含注入秘密 | 已覆盖（运行证据） |
| 45.3 数据库、Redis 和应用就绪/健康可判断，依赖延迟或故障有有限重试和明确失败结果 | A4：停 MySQL/Redis 后 readiness 转 unhealthy 并在恢复后回到 healthy；只停 MySQL 重启 backend，同一进程进入 Hikari 连接阶段、在 30 秒窗口内恢复依赖后回到 healthy（实测从观察到连接到 healthy 约 18 秒），进程未被替换；依赖持续缺失时应用在 150 秒上限内以非 0 退出（实测约 21 秒），且依赖保持停止 | 已覆盖（运行证据） |
| 45.4 以隔离候选运行证据验证，不以配置文本或镜像构建成功代替 | 上述结果来自 `ubuntu-24.04` 上实际 `compose build` + 启动隔离候选 + 逐条断言；镜像构建成功本身不作为通过依据 | 已覆盖 |

## 运行验收发现并修复的缺陷

这些缺陷在静态检查与本地编译阶段都看不出来，只有真正启动容器才暴露：

1. **prod 根本起不来。** `ImageFeatureAnalyzerTool` 依赖 `vision-space.vision-recognition.base-url/model`，但这两个键只写在被 git 忽略的 `application-local/server.yml` 里，镜像内的 jar 没有它们，占位符解析直接让上下文启动失败。修复：在共享 `application.yml` 给非秘密默认值，api-key 仍只走生产注入。
2. **MySQL 初始化账号从未创建。** init 脚本被官方 entrypoint `source` 进同一 shell，脚本里的 `set -u` 让 entrypoint 的 `_mysql_passfile` 无参调用在 `$1` 处报 `unbound variable`，账号创建语句根本没执行，后端以 `Access denied for user 'vision_app'` 启动失败。修复：不开启 nounset，用显式变量校验保留 fail-fast。
3. **探活账号主机不匹配。** 容器内 MySQL 把回环客户端识别为 `'healthcheck'@'127.0.0.1'`，不是 `localhost`，健康检查一直 `Access denied`、MySQL 永远 unhealthy。修复：按实际客户端主机创建账号，并把 `mysqladmin ping` 换成真正认证的 `SELECT 1`。
4. **建表数写死。** 脚本把期望表数写死为 15，main 已演进到 22 张，CI 在 schema bootstrap 处直接失败。修复：校验「正则提取数 == 源文件声明数」，漏抓仍会失败。
5. **就绪故障判定贴着超时边界。** Redis 停止后 readiness 指标确实转 DOWN，但容器探活每轮要等 Lettuce 重连超时（3 秒），6 次重试后接近 90 秒断言边界，出现偶发误判。修复：Redis 健康检查超时降到 1 秒（本地 PING 是毫秒级），断言窗口放宽到 150 秒。
6. **延迟启动实验的假设错误。** 原实验同时停 MySQL 和 Redis，但 Redis 缺失时 Redisson 在启动早期就明确失败，应用根本到不了 Hikari，「同进程等待后恢复」不可能成立。修复：实验只停 MySQL，验证 Hikari 有限等待窗口内的同进程恢复；Redis 故障仍由就绪迁移和重试耗尽实验覆盖。

## 未验证范围（诚实清单）

- **本机 Docker 仍未实测。** 本地 daemon 不可用，所有容器运行证据来自 GitHub Actions 的 `ubuntu-24.04`。这不影响「隔离候选实跑」的结论，但本机原生模式的任何结果都不能当作容器实测。
- **`docker-compose.host-access.yml` 未实际启动。** 只核对了 `compose config` 的绑定地址与端口；「确需宿主访问」的覆盖栈没有运行证据。
- **真实公网可达性、TLS、反向代理超时、Cookie 属性。** 属部署证据，不在本票；不能从 Compose 文本推断公网不可达。
- **生产凭据与真实外部服务。** 验收使用随机测试秘密，模型/外部服务地址不产生真实调用；未验证真实 DashScope、Pexels、对象存储。
- **人工部署迁移。** 旧数据卷不会重跑 init 脚本，`vision_app` 需运维按 `SECRETS.md` 等价创建；本票未执行任何线上变更。

## 复跑入口

```powershell
# 任意有 Docker 的机器（Windows / Linux + pwsh）
pwsh.exe -NoLogo -NoProfile -File ./scripts/container-verification.ps1

# 脚本控制流回归（不需要 Docker）
pwsh.exe -NoLogo -NoProfile -File ./scripts/tests/test-container-verification.ps1

# 完整验收基线（隔离 MySQL/Redis + 后端/前端）
pwsh.exe -NoLogo -NoProfile -File ./scripts/acceptance.ps1
```

CI 上由 `.github/workflows/container-verification.yml` 在每次 PR / main push / 手动触发时执行，产物保留 14 天。

## 合并注意事项

- 合并后若已有旧的 compose 部署（`version: '3.8'` 版本文件、发布 3306/6379 的旧栈），需要运维显式迁移：新栈默认零宿主发布，数据库口令首次由 init 脚本写入数据卷，旧数据卷复用时 init 不会重跑，需按 `SECRETS.md` 的人工关卡流程对齐账号。
- `SPRING_DATASOURCE_USERNAME` 不再是 root，需在部署环境中确认 `vision_app` 账号已由 init 脚本创建或人工等价创建。
- 管理端口 9081 绑定容器内 127.0.0.1：如需从容器外查看健康明细，须经 `docker exec`，不要为此改绑。
- 生产 profile 现在显式要求图片识别服务的 `base-url/model` 有值（共享配置已给默认值）；若部署方需要指向其他识别服务，用 `VISION_SPACE_VISION_RECOGNITION_BASE_URL/MODEL` 覆盖。
