# #45 最小权限容器与健康部署

工单：[45](https://github.com/yuwen773/VisionSpace/issues/45)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26)。
分支：`codex/45-container-health`。本文只记录本次实际执行到的证据，未执行的部分单列在「未验证范围」。

## 改动结构

| 位置 | 作用 |
| --- | --- |
| `Dockerfile` | 非 root 专用 UID 10001 运行；可写目录仅 `/app/tmp`（750，属主 app）；`/app` 与 jar 属主 root 且不可写；HEALTHCHECK 走管理端口的 readiness；EXPOSE 只声明业务端口；`-XX:MaxRAMPercentage=75`（容器内存边界，工单未要求，属部署稳健性默认，可按需移除） |
| `docker-compose.yml` | MySQL/Redis 不再发布任何宿主端口，只留在 `internal: true` 网络（无外联出口）；backend 端口只绑 `127.0.0.1`（LAN 不能再直连应用，放宽须运维显式改）；三方 healthcheck + `depends_on: service_healthy`；应用改用受限账号 `vision_app`（非 root）；必需秘密全部 `:?required` 显式失败；profile 默认 `prod`；移除 `--default-authentication-plugin=mysql_native_password`（MySQL 8 默认 caching_sha2_password 即新账号所用插件；JDBC URL 相应加 `allowPublicKeyRetrieval=true`） |
| `docker/mysql/init/01-create-app-user.sh` | 首次初始化创建 `vision_app`（业务 DML 权限）与 `healthcheck`（仅连接权，`mysqladmin ping` 无需库表授权）账号；口令来自容器环境变量，不落盘 |
| `docker-compose.host-access.yml` | 显式宿主访问覆盖文件：仅 `127.0.0.1` + 非默认端口；默认栈不含任何宿主 DB/Redis 发布 |
| `application-prod.yml` | 管理端点独立端口 9081 且只绑 127.0.0.1；只暴露 health；`show-details/show-components: never`；readiness 组纳入 `db,redis`；Hikari `initialization-fail-timeout: 30000` 提供有限启动重试窗口：依赖延迟最多等 30 秒，耗尽即明确异常退出（不无限阻塞、不带病启动） |
| `pom.xml` | 新增 `spring-boot-starter-actuator`（唯一依赖变更） |
| `scripts/container-verification.ps1` | 本票专用隔离验证：唯一 Compose project + 随机回环端口 + 随机秘密，逐条产出验收证据，只清理本轮资源 |
| `HttpHealthEndpointTest` | 健康端点契约回归：业务健康不含依赖细节；actuator health 状态 UP 且不泄露组件与连接信息 |
| `.gitignore` | 追加 `.env.secrets*`（密码代理文件，含真实值，禁止提交） |

## 已完成的静态与本地验证

| 检查 | 结果 |
| --- | --- |
| `docker compose config` 语法与插值校验 | PASS（required 变量、网络 internal、healthcheck 均按预期渲染） |
| `container-verification.ps1` PowerShell 语法解析 | PASS |
| `mvn compile` / `mvn test-compile`（本地，含 actuator 依赖与新测试） | PASS |
| Dockerfile wget 探活行为（镜像内实测 `wget` 可用且连接拒绝时返回非 0） | PASS |
| 基础镜像 `eclipse-temurin:21-jre-alpine` 已拉取 | PASS（`maven:3.9-eclipse-temurin-21-alpine` 下载中断，未完成） |

## 验收对照（容器运行验收：BLOCKED_ENV）

| 工单验收项 | 验证方式 | 结果 |
| --- | --- | --- |
| 45.1 数据库/Redis 默认内部网络 | `container-verification.ps1` A2 组（端口发布断言 + `network inspect Internal=true` + 宿主 netstat） | **BLOCKED_ENV** |
| 45.2 非 root / 受限可写 / profile / SQL 日志 | A1 + A3 组（`id -u`、写入探针、profile 断言、日志扫描） | **BLOCKED_ENV** |
| 45.3 健康可判断 + 有限重试 + 明确失败 | A4 组（stop/start mysql 健康迁移）+ Hikari 30s 重试窗口 | **BLOCKED_ENV** |
| 45.4 以隔离候选运行证据验证 | 本脚本实际运行输出 | **BLOCKED_ENV** |

## 未验证范围（诚实清单）

- **容器运行证据全部缺失**：本轮多次尝试构建隔离候选，均在拉取 `maven:3.9-eclipse-temurin-21-alpine` 基础镜像时因 Docker Desktop 反复退出（系统内存压力）而中断（两次下载至 24-135MB / 158MB 后 EOF）。镜像从未构建成功，栈从未启动，以下均未实测：
  - 容器实际 UID 与目录写权限（A1）
  - 实际端口发布与网络可达范围（A2）
  - 实际生效 profile 与敏感日志行为（A3）
  - 依赖故障下的健康迁移与恢复（A4）
  - MySQL init 脚本 `01-create-app-user.sh` 的实际执行（`docker_process_sql` 路径只在真实镜像里走到）
- 配置文本与 compose config 输出不能替代以上运行证据，本票不以静态检查虚报通过。
- `HttpHealthEndpointTest` 编译通过但未随隔离验收套件实际运行（完整套件需 Docker 或原生 MySQL/Redis 实例）。

## 复跑入口（环境恢复后）

```powershell
pwsh.exe -NoLogo -NoProfile -File ./scripts/container-verification.ps1
```

先 `docker pull maven:3.9-eclipse-temurin-21-alpine`（支持断点续传的 pull 比构建内下载更稳）可显著缩短首次构建时间。

## 合并注意事项

- 合并后若已有旧的 compose 部署（`version: '3.8'` 版本文件、发布 3306/6379 的旧栈），需要运维显式迁移：新栈默认零宿主发布，数据库口令首次由 init 脚本写入数据卷，旧数据卷复用时 init 不会重跑，需按 `SECRETS.md` 的人工关卡流程对齐账号。
- `SPRING_DATASOURCE_USERNAME` 不再是 root，需在部署环境中确认 `vision_app` 账号已由 init 脚本创建或人工等价创建。
- 管理端口 9081 绑定容器内 127.0.0.1：如需从容器外查看健康明细，须经 `docker exec`，不要为此改绑。
