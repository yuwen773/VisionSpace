# #27 可复现验收基线

工单：[27](https://github.com/yuwen773/VisionSpace/issues/27)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26)。本入口用于工程回归；生产部署、真实模型、云存储及人工运维验收独立记录。

## 集成基准与原报告差异

实施起点为 `main@35cdf05ecfaa14e2010c1522de1a53bb086d0597`。父规格规划基准 `fcff5c24f53e410a0be39db69b58317ef9a96b0a` 是其祖先；原报告的远程基准为 `41ccda2eca602580e01e0d06b1d11736fb1f699b`。本票以当前 HEAD 为集成基准，直接保留已经集成的修复，不整体合并任何其他分支，也不重新实现后续业务票。

| 原报告发现 | 当前基线与验证入口 |
| --- | --- |
| A12 公开图片删除误扣空间额度 | `0c60261` 已修复空 spaceId 分支；`HttpBaselineTest` 用登录 Cookie 调真实删除入口，检查图片逻辑删除、无关空间额度不变及记录的文件删除。 |
| A19 历史分页/会话游标不同源 | `9c46db9`、`9ceb073` 已纳入；保留 `ChatHistoryCursorIntegrationTest` 的六条真实 MySQL 回归，不以 Mockito 替代分页 SQL。 |
| A31 前端请求实例与返回值 | `f5d646d` 及后续生成器适配已纳入；`frontend/src/__tests__/request.spec.ts` 通过真实 Axios 实例和传输适配器验证 Cookie 配置、请求数据、成功/业务错误响应及网络失败。HTTPS 同源、SSE、WebSocket 的生产链路仍属后续票。 |
| 本地修复未进入 main、尚无自动化入口 | 当前 HEAD 已包含 PR #49 的测试/API 生成修复；本票提供统一脚本与最小 PR workflow，并已在 PR #50 上实际远程执行通过（见下）。分支保护仍须另行授权配置。 |

不将其他授权缺陷判为已修复：空鉴权上下文、JSON 媒体类型参数解析、私人资源/Agent/MCP 授权矩阵及真实出站控制仍由对应业务票处理。本票仅建立基线，覆盖正常 JSON 的公开删除及明确拒绝路径。

## 可重复命令

需要 JDK 21（`JAVA_HOME` / `PATH` 必须选中该版本）、Maven 3.9、Node 22.21.0、npm 10、PowerShell 7 与可用 Docker engine。在项目根目录执行：

```powershell
pwsh.exe -NoLogo -NoProfile -File ./scripts/acceptance.ps1
```

脚本每次创建仅绑定回环地址的 MySQL 8.0.36、Redis 7.2.4 容器及随机端口，不使用 compose、`.env` 或现有服务；退出时只删除本次创建的容器。测试使用独立 `acceptance.yml`，不导入开发或生产配置。库名和凭据逐次生成，MySQL 限定专用账号及专用数据库，Redis 使用独立进程、密码及 DB 15。连接前校验两个实例的随机标记；缺少配置、错误 profile、默认端口或标记不匹配均拒绝启动。直接执行依赖 Spring 的 `mvn test` 而不经脚本，会报 `BLOCKED_ENV`，不会回退本地配置。

Docker 不可用的 Windows 主机可显式指定两个独立服务程序目录：

```powershell
pwsh.exe -NoLogo -NoProfile -File ./scripts/acceptance.ps1 -MySqlBin '<MySQL bin>' -RedisBin '<Redis bin>'
```

此模式在系统临时目录创建全新 MySQL 数据目录及 Redis 进程；不读取已有 MySQL/Redis 配置，不注册或修改系统服务，不连接 3306/3307/6379。执行建表之前先只读核对 `@@datadir` 与本轮目录一致，再创建专用账号、库和实例标记。版本以指定程序为准，必须与 Docker 验证分开报告。每轮只停止自己启动的进程，临时日志和隔离测试数据保留在结果中的 `logs` 路径，供失败排查。

## 真实链路与替身边界

`HttpBaselineTest` 通过真实随机 TCP 端口访问 Spring Boot，经过现有 `HttpRequestWrapperFilter`、JSON 请求绑定、Sa-Token 拦截器、控制器和业务服务；注册/登录走 HTTP，Spring Session 与 Sa-Token 使用真实隔离 Redis，数据和事务使用真实隔离 MySQL。

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

## 本次验证记录

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
