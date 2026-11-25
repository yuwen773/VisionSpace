# #27 可复现验收基线

工单：[27](https://github.com/yuwen773/VisionSpace/issues/27)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26)。本入口用于工程回归；生产部署、真实模型、云存储及人工运维验收独立记录。

## 集成基准与原报告差异

实施起点为 `main@35cdf05ecfaa14e2010c1522de1a53bb086d0597`。父规格规划基准 `fcff5c24f53e410a0be39db69b58317ef9a96b0a` 是其祖先；原报告的远程基准为 `41ccda2eca602580e01e0d06b1d11736fb1f699b`。本票以当前 HEAD 为集成基准，直接保留已经集成的修复，不整体合并任何其他分支，也不重新实现后续业务票。

| 原报告发现 | 当前基线与验证入口 |
| --- | --- |
| A12 公开图片删除误扣空间额度 | `0c60261` 已修复空 spaceId 分支；`HttpBaselineTest` 用登录 Cookie 调真实删除入口，检查图片逻辑删除、无关空间额度不变及记录的文件删除。 |
| A19 历史分页/会话游标不同源 | `9c46db9`、`9ceb073` 已纳入；保留 `ChatHistoryCursorIntegrationTest` 的六条真实 PostgreSQL 回归，不以 Mockito 替代分页 SQL。 |
| A31 前端请求实例与返回值 | `f5d646d` 及后续生成器适配已纳入；`frontend/src/__tests__/request.spec.ts` 通过真实 Axios 实例和传输适配器验证 Cookie 配置、请求数据、成功/业务错误响应及网络失败。HTTPS 同源、SSE、WebSocket 的生产链路仍属后续票。 |
| 本地修复未进入 main、尚无自动化入口 | 当前 HEAD 已包含 PR #49 的测试/API 生成修复；本票提供统一脚本与最小 PR workflow，并已在 PR #50 上实际远程执行通过（见下）。分支保护仍须另行授权配置。 |

不将其他授权缺陷判为已修复：空鉴权上下文、JSON 媒体类型参数解析、私人资源/Agent/MCP 授权矩阵及真实出站控制仍由对应业务票处理。本票仅建立基线，覆盖正常 JSON 的公开删除及明确拒绝路径。

## 可重复命令

需要 JDK 21（`JAVA_HOME` / `PATH` 必须选中该版本）、Maven 3.9、Node 22.21.0、npm 10、PowerShell 7 与可用 Docker engine。在项目根目录执行：

```powershell
pwsh.exe -NoLogo -NoProfile -File ./scripts/acceptance.ps1
```

脚本每次创建仅绑定回环地址的 PostgreSQL 18.7（`postgres:18.7`）、Redis 7.2.4 容器及随机端口，不使用 compose、`.env` 或现有服务。PostgreSQL 18 的数据目录已版本化（`PGDATA=/var/lib/postgresql/18/docker`），命名卷一律挂在 `/var/lib/postgresql`（`vsvol-<run-id>-postgres` / `vsvol-<run-id>-redis`），容器与卷都带 `vs.owner` / `vs.run-id` / `vs.kind` 标签；退出时按本轮 run-id 删除容器和卷，并断言无残留。业务表只有一个建表入口：Flyway（`org.flywaydb:flyway-maven-plugin:13.13.0`）以迁移角色 `vision_migrator` 执行 `src/main/resources/db/migration/V1__baseline.sql`；容器初始化脚本只建角色、建库、装 `pgcrypto` 扩展、写库级 UTC 时区与库注释，不建任何业务表。运行角色 `vision_app` 只拿到 DML 权限（`SELECT/INSERT/UPDATE/DELETE`）与序列 `USAGE`。测试使用独立 `acceptance.yml`，不导入开发或生产配置。库名和凭据逐次生成，PostgreSQL 限定专用账号及专用数据库，Redis 使用独立容器、密码及 DB 15。连接前校验两个实例的随机标记：PostgreSQL 用库级注释（`COMMENT ON DATABASE <db> IS '<nonce>'`）承载，Redis 用 `acceptance:instance` 键；缺少配置、错误 profile、默认端口或标记不匹配均拒绝启动。运行器环境变量为 `VS_ACCEPTANCE_NONCE`、`VS_ACCEPTANCE_PG_PORT`、`VS_ACCEPTANCE_REDIS_PORT`；口令只经 `PGPASSWORD` / `FLYWAY_PASSWORD` 环境变量传入，不写进命令行。直接执行依赖 Spring 的 `mvn test` 而不经脚本，会报 `BLOCKED_ENV`，不会回退本地配置。

### Docker 测试资源

`scripts/docker-test-harness.psm1` 是所有 Docker 测试资源的唯一入口：命名卷、`vs.*` 标签、按 run-id 的 teardown 与 `Assert-VsClean`。新增测试不得裸 `docker run` 起服务；脚本外的任何手工 `docker run` 必须显式挂命名卷，并打上 `vs.owner=vision-space` 与 `vs.run-id=<id>` 标签。

硬杀（关终端、超时终止、`wsl --shutdown`、关机）会让 `finally` 失效，残留由审计器兜底：

```powershell
pwsh.exe -NoLogo -NoProfile -File ./scripts/docker-test-resources.ps1             # 只读盘点
pwsh.exe -NoLogo -NoProfile -File ./scripts/docker-test-resources.ps1 -ApplySafe   # 只回收 SAFE_*
```

审计器永不调用任何 prune，永不删除运行中容器、compose 开发服务或其他项目的资源；无标签历史残留归入 `REVIEW_*`，只报告不删除。`acceptance.ps1` 的 `runner-tests` 会执行 harness 与审计器的反例测试（`scripts/tests/test-docker-lifecycle.ps1`、`scripts/tests/test-docker-reaper.ps1`）。隔离服务一律走 Docker（`infrastructure=docker`）；原生二进制模式已随 PostgreSQL 18 迁移移除，不再有需要按基础设施类型跳过的分支。PowerShell 的 `ProcessExit` 处理器在非 runspace 线程回调，无法可靠执行清理且会污染退出码，因此 harness 不使用它——正常退出依赖 `finally`，硬杀残留依赖审计器。

### 隔离服务（PostgreSQL 18）

隔离服务只提供 Docker 一条路径：容器镜像 `postgres:18.7` 与 `redis:7.2.4`，只绑定回环地址并挂本轮命名卷，库名与凭据逐次生成。原生的 `-MySqlBin`/`-RedisBin` 模式（本地起 `mysqld`/`redis-server.exe` 进程、靠 `SELECT @@datadir` 核对身份）已随迁移移除，不再保留：

```powershell
# 与脚本内部同一条路径的手工检查：读库级隔离标记与已建出的表
docker exec -e "PGPASSWORD=<nonce>" vs-acceptance-postgres-<nonce> \
  psql --no-psqlrc -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'" -U vision_app -d vs_acceptance_<nonce>
```

Docker 不可用时脚本直接判 `BLOCKED_ENV`，不会回退到本地已有服务、不会连接 3306/5432/6379 的既有实例，也不会读取本地数据库配置。容器与卷由脚本自建自删：每轮只清理自己按本轮 run-id 标签创建的资源，临时日志和隔离测试数据保留在结果中的 `logs` 路径，供失败排查。

## 真实链路与替身边界

`HttpBaselineTest` 通过真实随机 TCP 端口访问 Spring Boot，经过 JSON 请求绑定、Sa-Token 拦截器、控制器和业务服务；注册/登录走 HTTP，Spring Session 与 Sa-Token 使用真实隔离 Redis，数据和事务使用真实隔离 PostgreSQL（Flyway 建的基线 schema）。

公开作者删除成功、未登录删除拒绝、其他用户删除拒绝及登录/匿名业务查询均有回归。拒绝路径同时检查图片未删除、空间额度未变和外部调用数未增加。外部存储位于测试传输边界：替换 `PictureStorageService` 为发送到本机 HTTP 记录器的适配器；不会实际删除云对象。模型及可配置外部 HTTP 地址指向本机记录器，未知调用返回 503 并记录；没有模拟真实模型推理或云服务成功。硬编码 SDK/URL 等未执行路径不据此认定已受全面出站限制。

原有 Spring 装配测试和历史游标测试都使用相同的强制隔离初始化器。原有有效测试继续保留；新增缺失配置/危险端口保护回归。后端 Surefire 版本固定，并在没有发现测试时失败。

## 结果与最小 CI

脚本先验证六个结果分类场景，再顺序执行 `mvn clean verify`、`npm ci`、`npm run lint:check`、`npm run format:check`、`npm run type-check`、`npm test`、`npm run build-only`，后端通过时再跑 `scripts/openapi-contract.ps1` 核对生成客户端与契约。各关卡分别记录，后端失败仍会运行独立前端检查。最后生成 `target/acceptance/summary.json`，本轮各关卡日志和 Surefire XML 位于 `target/acceptance/<本轮标识>/`，原始临时目录在 summary 中列出。Surefire 从一开始就写入本轮独立目录，环境阻断不会混入旧测试结果。生成产物不入库。

### 接口契约关卡

`OpenApiContractTest` 在隔离环境里把后端 `/api/v3/api-docs` 落到 `target/openapi-contract.json`；`scripts/openapi-contract.ps1` 随后用与 `npm run openapi2ts` 相同的生成配置（同一份 `openapi.config.js`）生成到临时目录，与入库的 `frontend/src/api/` 逐字节比较。因此后端接口变更后忘记重新生成客户端会直接 FAIL，不再依赖评审人肉眼判断，也不需要手工启动后端。生成前会对 spec 的键递归排序，避免 springdoc 的属性顺序让同一契约产出不同文件。

| 状态 | 含义 |
| --- | --- |
| `PASS` / 退出 0 | 本次实际执行成功；仅适用于 summary 标注的基础设施。 |
| `FAIL` / 退出 1 | 编译、断言或构建失败。保留失败测试，不能自动归类为既有失败。 |
| `BLOCKED_ENV` / 退出 2 | 隔离服务、实例标记或必要环境不可用；不能表述为通过。若同时有真实失败，优先退出 1。 |
| 既有失败 | 对同一隔离配置复现于起点 HEAD 后，单独记录具体测试、日志和基线比较依据；保持门禁失败，不跳过或删除。 |

`.github/workflows/acceptance.yml` 在 PR、main push 和手动触发时运行同一命令，使用只读仓库权限和超时，并在成功/失败时上传验收日志及 Surefire 报告。无需真实外部服务凭据。工作流已在 PR #50 上实际远程执行通过；此票不配置分支保护。

### 浏览器作业失败排查

`browser` job 的失败证据在 `srf-browser-<attempt>` artifact，由 `scripts/publish-evidence.py` 投影输出，按 `scripts/browser-acceptance.ps1` 生成本地原始日志：

| 文件 | 内容 |
| --- | --- |
| `browser/<slot>/summary.json` | 该 slot 的脱敏结论（`results` 里各阶段 PASS/FAIL、`productionAcceptance`） |
| `browser/<slot>/<runId>/artifacts/results.json` | 九个步骤的 slot 序列；只含 `status`，不含断言文本 |
| `browser/<slot>/<runId>/artifacts/diagnostics/browser-full.log` | 失败现场的完整 e2e 日志，含断言消息与堆栈 |

定位失败时先读 `browser-full.log` 的断言文本，再用 `results.json` 的 slot 序列确认影响范围。`publish-evidence.py` 有意剥离 `summary.json` / `results.json` 里的失败消息，只看状态码无法区分「真缺 Cookie」和「断言脚本漏抓」；`diagnostics/*.log` 是经白名单透传的例外，不含凭据。

`https-browser` 第一步必挂时先查 e2e 脚本代码（例如 `headersArray()` 返回 Promise 却漏 `await`），不要当成环境 flaky 重跑。确属偶发失败时完整重跑 `gh run rerun <run-id>`，不加 `--failed`：部分重跑会让 evidence 台账缺 T01–T24 same-candidate 证据链。同一根因还会连带 `evidence` 与 `SRF merge gate` 一起 FAIL，不是三个独立故障。

`npm run type-check` 依次跑 `vue-tsc --build`（`src/`）与 `tsc -p tsconfig.e2e.json`（`frontend/e2e/**/*.mjs`）。e2e 是纯 `.mjs`，ESLint 无类型信息，漏 `await` 之类 Promise 误用只由后一个 `checkJs` 关卡拦截。

### 用例执行顺序与共享库

后端验收用例跑在**同一个隔离库**上（一轮只建一次库，所有用例共用），所以：

- 用例只清自己写的那几行不够——凡是本轮写进去的表行，都要在 `@AfterEach` 里删掉，尤其是故意造的孤儿数据（目标行不存在、聚合器不会消费它），它会一直挂在库里影响后面用例；
- 断言不要用**全库口径**（例如「待处理积压必须为 0」），要改成按自己那批数据断言；全局量只能断言方向（变小、变少），不能断言绝对值；
- CI 与本机的用例顺序不同：Linux CI 的顺序是 surefire 按目录枚举出来的顺序（ext4 的目录哈希序），本机 Windows 是 NTFS 的字母序。同一个用例「本地绿、CI 红」通常就是这个原因，不是环境 flaky。

## 验证记录

本节分两段：**历史记录**是 2026-10-22 的 MySQL 8 与原生二进制时代的结论，只作历史基线保留，不能外推到 PostgreSQL 18 链路；**当前状态**描述迁移后（PostgreSQL 18）的验收契约，实测结论以迁移后重新运行产生的 `target/acceptance/summary.json` 为准。

### 当前状态（PostgreSQL 18）

| 验证项 | 当前契约 |
| --- | --- |
| 隔离服务 | Docker 里的 `postgres:18.7` + `redis:7.2.4`，只绑定回环地址、随机端口、随机库名与逐次凭据；数据目录卷挂在 `/var/lib/postgresql`。 |
| 建表入口 | 只有 Flyway（迁移角色 `vision_migrator` 执行 `V1__baseline.sql`）；初始化脚本不建业务表。运行角色 `vision_app` 只有 DML 权限。 |
| 隔离标记 | PostgreSQL 库级注释（`COMMENT ON DATABASE`）+ Redis `acceptance:instance` 键；标记不匹配即拒绝启动。 |
| 原生二进制模式 | 已移除（`-MySqlBin`/`-RedisBin` 不再存在），Docker 不可用即 `BLOCKED_ENV`。 |
| 密钥传递 | 只经 `PGPASSWORD` / `FLYWAY_PASSWORD` 环境变量，不进 argv。 |

### 历史记录（2026-10-22，MySQL 8 与原生二进制时代）

2026-10-22，Windows + JDK 21.0.7 / Maven 3.9.9 / Node 22.21.0 / npm 10.9.4。显式原生服务模式使用全新临时数据目录，实测 MySQL 8.0.26、Redis 3.2.100；没有使用现有系统服务。Docker engine 在本机不可用，不能把原生模式的结果等同于 MySQL 8.0.36 / Redis 7.2.4 容器实测。

| 验证项 | 实际结果 |
| --- | --- |
| 功能关卡首次通过（`33e4c8872ad04518addc23ddf73c653c`） | 隔离服务、85 条后端测试及打包、前端安装/类型检查/12 条测试/构建均 PASS；当时尚未纳入 Windows MySQL 子进程退出检查。 |
| 加入实际连接身份检查后重复运行（`f44af4272ae34a65aa45e62b70f423e6`） | 后端 86 条测试、0 失败/错误/跳过，打包 PASS；前端安装/类型检查/12 条测试/构建全部 PASS，退出 0。运行时额外注入 MySQL、Hikari、Redis 的默认端口 URL，实际业务连接仍通过本轮 MySQL/Redis 标记检查。 |
| 最终完整运行（`d9ed4cb4035446ecab1f8c9e6778fc3e`） | 六个分类场景、隔离服务、86 条后端测试及打包、前端安装/类型检查/12 条测试/构建、资源清理全部 PASS，退出 0。结束后独立核查临时 MySQL/Redis 剩余进程为 0。该轮同样包含环境变量干扰验证。最新结果见 `target/acceptance/summary.json`。 |
| 缺少隔离参数 | 单独执行 `VisionSpaceApplicationTests`，按预期在初始化器抛出 `BLOCKED_ENV: isolation identity missing`，未启动应用业务 Bean。日志名 `visionspace-27-fail-closed.log` 位于系统临时目录。 |
| 结果分类 | 六个独立场景 PASS：成功、纯隔离错误、混合断言失败、混合普通错误、损坏 XML、无测试报告的构建失败。后三类及混合失败均保留 FAIL。 |
| 原有有效测试 | 78 条后端、10 条前端原有用例全部保留并实际通过；没有发现需豁免的既有失败。 |
| Standards / Spec 审查 | 分别完成独立审查；报告混合失败误分类和旧报告混入问题，修复后复核均为 0 剩余发现。 |
| 现有未提交资料 | 实施期间对 62 个未跟踪文件复核 SHA-256，0 不匹配。PR #51（`4aa9fa4`）已将其中的 `.zread/` 与 `软件著作权申请资料/` 加入 `.gitignore`、将 `CONTEXT.md` 纳入版本控制，此后不再出现在 `git status` 中；文件本身未删除。 |
| Docker / GitHub Actions | 本机 Docker 不可用，原生模式结果不等同容器实测。远程 Actions 已执行：PR #50 的 [run 39734430077](https://github.com/yuwen773/VisionSpace/actions/runs/39734430077) 在 `ubuntu-24.04` 以 Docker 模式 PASS，用时 3 分 07 秒，`summary.json` 记录的 checkout 提交为合并态 `3c93267`（`41ff3a1` merge into `35cdf05`）；MySQL `8.0.36`、Redis `7.2.4`，后端 86 条 / 前端 12 条 / 分类 6 场景 / 类型检查 / 构建 / 清理均 PASS，产物 `acceptance-evidence` 已上传。 |
| 真实模型、云对象、生产与人工验收 | NOT_RUN。本票未关闭工单，未配置分支保护，未修改远程治理配置；PR #50 按当前授权保持未合并。 |

调试时曾暴露本票新增 DDL 提取错误及测试适配器未覆盖同名 `@Resource` 注入，两处均保留断言并修复后通过；这些不是原有测试失败。结束审计还发现 Windows MySQL 监控程序留下子进程，已在验证随机数据目录后正常关闭此前测试实例，并加入服务端正常关闭、进程退出等待及清理失败记录；最终运行确认无遗留临时服务。前端构建仍输出依赖包体积及现有 CSS 警告，构建退出 0；未在本票修改相关业务样式或打包策略。
