# #45 最小权限容器与健康部署

工单：[45](https://github.com/yuwen773/VisionSpace/issues/45)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26)。
分支：`codex/45-container-health`。本文只记录本次实际执行到的证据，未执行的部分单列在「未验证范围」。

## 改动结构

| 位置 | 作用 |
| --- | --- |
| `Dockerfile` | 非 root 专用 UID 10001 运行；`/app` 与 jar 属主 root、755（运行身份可读可遍历、不可写）；可写目录仅 `/app/tmp`（770，属主 app）；HEALTHCHECK 走管理端口的 readiness；EXPOSE 只声明业务端口；`-XX:MaxRAMPercentage=75`（容器内存边界，工单未要求，属部署稳健性默认，可按需移除） |
| `docker-compose.yml` | MySQL/Redis 不再发布任何宿主端口，只留在 `internal: true` 网络（无外联出口）；backend 端口只绑 `127.0.0.1`（LAN 不能再直连应用，放宽须运维显式改）；三方 healthcheck + `depends_on: service_healthy`；应用改用受限账号 `vision_app`（非 root）；必需秘密全部 `:?required` 显式失败；profile 默认 `prod`；移除 `--default-authentication-plugin=mysql_native_password`（MySQL 8 默认 caching_sha2_password 即新账号所用插件；JDBC URL 相应加 `allowPublicKeyRetrieval=true`） |
| `docker/mysql/init/01-create-app-user.sh` | 首次初始化创建 `vision_app`（业务 DML 权限）与 `healthcheck`（仅连接权，`mysqladmin ping` 无需库表授权）账号；口令来自容器环境变量，不落盘；嵌入 SQL 前转义反斜杠与单引号，任意合法口令不破坏语句 |
| `docker-compose.host-access.yml` | 显式宿主访问覆盖文件：仅 `127.0.0.1` + 非默认端口；默认栈不含任何宿主 DB/Redis 发布 |
| `application-prod.yml` | 管理端点独立端口 9081 且只绑 127.0.0.1；只暴露 health；`show-details/show-components: never`；readiness 组纳入 `db,redis`；Hikari `initialization-fail-timeout: 30000` 提供有限启动重试窗口：依赖延迟最多等 30 秒，耗尽即明确异常退出（不无限阻塞、不带病启动） |
| `pom.xml` | 新增 `spring-boot-starter-actuator`（唯一依赖变更） |
| `scripts/container-verification.ps1` | 本票专用隔离验证：唯一 Compose project + 随机回环端口 + 随机秘密（字符集避开 SQL 特殊字符）；启动顺序为先 mysql/redis → 以 root 导入 15 张业务表（复用 #27 的 DDL 提取约定；vision_app 仅业务 DML，无 CREATE）→ 再启动 backend，避免 FileStorageInit 查询空库失败；HTTP 验收前有界等待业务端点就绪；恢复等待持续到 healthy 或超时（不把 unhealthy 过渡态当终态）；端口发布判定以本轮容器的 compose ps 为准（字段结构对照 Docker 官方文档：服务级 `Service`、发布项级 `URL`，已离线样本验证回环断言与非默认端口漏检修复）；A4 覆盖 MySQL/Redis 停止恢复，以及下述同进程延迟启动和非零退出实验；compose ps JSON 逐行解析 |
| `HttpHealthEndpointTest` | 健康端点契约回归：业务健康不含依赖细节；actuator health 状态 UP 且不泄露组件与连接信息 |
| `scripts/acceptance.ps1` | `runner-tests` 关卡追加执行 `tests/test-container-verification.ps1`：容器验证脚本的控制流回归（8 个场景，含预期拒绝）由 CI 自动跑，不再只靠手工执行；该回归不需要 Docker，容器运行验收仍单独需要 |
| `.gitignore` | 追加 `.env.secrets*`（密码代理文件，含真实值，禁止提交） |

## 已完成的静态与本地验证

| 检查 | 结果 |
| --- | --- |
| `docker compose config` 语法与插值校验 | PASS（required 变量、网络 internal、healthcheck 均按预期渲染） |
| 验收脚本与回归脚本 PowerShell 语法、轻量替身 Compose 配置解析 | PASS |
| 启动验证脚本定点回归（本次修复） | PASS：8/8，含预期拒绝场景；只验证脚本控制流，不能替代业务容器验收 |
| 轻量 Docker 替身实验（本次修复） | BLOCKED_ENV：前置 `docker info` 失败，`dockerDesktopLinuxEngine` 命名管道不存在；9 条生命周期断言未执行 |
| `mvn compile` / `mvn test-compile`（本地，含 actuator 依赖与新测试） | PASS |
| Dockerfile wget 探活行为（镜像内实测 `wget` 可用且连接拒绝时返回非 0） | PASS |
| 基础镜像 `eclipse-temurin:21-jre-alpine` 已拉取 | PASS（`maven:3.9-eclipse-temurin-21-alpine` 下载中断，未完成） |

## 2026-10-28 复核

分支已 rebase 到 `origin/main`（`8dd98e0`），五个提交无冲突落地，复核时 HEAD `76d9ca6`。

| 检查 | 结果 |
| --- | --- |
| 完整验收套件（原生隔离 MySQL 8.0.26 + Redis 3.2.100，`native-explicit`） | **12/12 PASS**：`runner-tests`、`secret-scan`、`isolation`、`backend`、`frontend-install`、`frontend-lint`、`frontend-format`、`frontend-typecheck`、`frontend-tests`、`frontend-build`、`frontend-api-contract`、`cleanup` |
| 后端测试 | 523 条，0 失败、0 错误、0 跳过；其中 `HttpHealthEndpointTest` 2 条实际执行通过（actuator 依赖没有带坏既有用例） |
| `runner-tests` 内容 | 已包含容器验证脚本的 8 个控制流回归场景，全部符合预期 |
| 容器运行验收（A1–A4） | 仍 **BLOCKED_ENV**：`docker info` 连 `dockerDesktopLinuxEngine` 命名管道失败，daemon 未运行；本节不改变上表结论 |

首轮 `frontend-format` FAIL 是工作区换行符问题，不是代码问题：本 worktree 建在 `.gitattributes`（`* text=auto eol=lf`）落地之前，465 个已跟踪文件仍是 CRLF 检出，Prettier 报 38 个文件。按属性重新检出后 `npm run format:check` 全部通过，`git status` 仍干净——索引内容一行未改。新建 worktree 不受影响。

## 验收对照（容器运行验收：BLOCKED_ENV）

| 工单验收项 | 验证方式 | 结果 |
| --- | --- | --- |
| 45.1 数据库/Redis 默认内部网络 | `container-verification.ps1` A2 组（本轮容器端口发布断言 + `network inspect Internal=true`） | **BLOCKED_ENV** |
| 45.2 非 root / 受限可写 / profile / SQL 日志 | A1 + A3 组（`id -u`、写入探针、profile 断言、日志扫描） | **BLOCKED_ENV** |
| 45.3 健康可判断 + 有限重试 + 明确失败 | A4 组（mysql/redis 停止→unhealthy→恢复→healthy；观测连接阶段并验证同进程等待/恢复；150s 内实际非 0 退出且依赖保持停止） | **BLOCKED_ENV** |
| 45.4 以隔离候选运行证据验证 | 本脚本实际运行输出 | **BLOCKED_ENV** |

## 未验证范围（诚实清单）

- 本次四项 P2 修复只完成脚本定点回归；Docker 替身实验在 daemon 前置检查处阻断，未执行完整业务候选验收。以下此前缺失的 A1–A4 证据仍未补齐，不能由 8/8 的控制流回归升级为 PASS。
- **业务容器运行证据仍缺失**：此前多次尝试构建隔离候选，均在拉取 `maven:3.9-eclipse-temurin-21-alpine` 基础镜像时因 Docker Desktop 反复退出（系统内存压力）而中断（两次下载至 24-135MB / 158MB 后 EOF）。镜像从未构建成功，栈从未启动，以下均未实测：
  - 容器实际 UID 与目录写权限（A1）
  - 实际端口发布与网络可达范围（A2）
  - 实际生效 profile 与敏感日志行为（A3）
  - 依赖故障下的健康迁移与恢复（A4）
  - MySQL init 脚本 `01-create-app-user.sh` 的实际执行（`docker_process_sql` 路径只在真实镜像里走到）
- 配置文本与 compose config 输出不能替代以上运行证据，本票不以静态检查虚报通过。
- `HttpHealthEndpointTest` 编译通过但未随隔离验收套件实际运行（完整套件需 Docker 或原生 MySQL/Redis 实例）。

## 四项 P2 修复与针对性验证

- 耗尽实验改为 `docker start` 直接启动本轮既有 backend 容器，不使用会启动依赖的 `compose up`；在等待和耗尽阶段分别断言 MySQL/Redis 仍处于停止状态。
- 启动命令本身必须成功；预期的应用失败由有界 `inspect` 轮询观察，避免非零 Compose 退出在断言前被封装函数抛出。
- 两个隔离启动实验先用 `docker update --restart=no` 关闭本轮 backend 自动重启。延迟实验只读取本次启动时间之后的日志，观测到 Hikari 进入连接阶段后延迟 10 秒才恢复依赖，并核对等待及恢复前后的 `StartedAt` / `RestartCount` 一致。
- 耗尽实验只接受本次应用进程在 150 秒内实际退出且退出码非零；等待超时、零码退出、inspect 失败或空结果均不能通过。此处关闭自动重启只用于隔离实验，Compose 的部署配置仍为 `unless-stopped`。

针对性回归直接执行脚本中的两个真实场景块，覆盖非零退出、持续运行但不健康、零码退出、inspect 命令失败、inspect 空结果、同进程恢复、恢复时进程被替换，以及未观测到连接阶段。8 个场景均符合预期，拒绝场景中的 `FAIL` 是被回归测试要求的结果。修复前已运行同一回归入口并复现提前抛异常、自动启动依赖及重启掩盖问题。

```powershell
pwsh.exe -NoLogo -NoProfile -File ./scripts/tests/test-container-verification.ps1
# 轻量 Docker 替身：验证真实 Docker 状态与生命周期，不构建业务镜像
pwsh.exe -NoLogo -NoProfile -File ./scripts/tests/test-container-verification.ps1 -Docker
```

## 复跑入口（环境恢复后）

```powershell
pwsh.exe -NoLogo -NoProfile -File ./scripts/container-verification.ps1
```

先 `docker pull maven:3.9-eclipse-temurin-21-alpine`（支持断点续传的 pull 比构建内下载更稳）可显著缩短首次构建时间。

## 合并注意事项

- 合并后若已有旧的 compose 部署（`version: '3.8'` 版本文件、发布 3306/6379 的旧栈），需要运维显式迁移：新栈默认零宿主发布，数据库口令首次由 init 脚本写入数据卷，旧数据卷复用时 init 不会重跑，需按 `SECRETS.md` 的人工关卡流程对齐账号。
- `SPRING_DATASOURCE_USERNAME` 不再是 root，需在部署环境中确认 `vision_app` 账号已由 init 脚本创建或人工等价创建。
- 管理端口 9081 绑定容器内 127.0.0.1：如需从容器外查看健康明细，须经 `docker exec`，不要为此改绑。
