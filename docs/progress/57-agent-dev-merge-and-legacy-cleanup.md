# #57 feature/agent-dev 并入 main 与历史遗留清理

基线：`origin/main` @ `071d9ae`（PR #112）。工作分支：`rebase/agent-dev-on-main`。

本轮目标：先把长期偏离的 `feature/agent-dev` 收回到 main 上，清掉审计确认的零引用遗留代码，然后发一次版。部署、生产库核对与密钥轮换不在本轮自动执行范围内。

## 1. 分支处置

`feature/agent-dev` HEAD `de26c03` 与 main 的分叉是 33 个独有提交对 334 个。原分支有三处必须先处理的事实：

1. **HEAD 不可编译。** `6dcbe4c` 把 `PasswordService` 改名为 `AuthService` 后，`UserService` 接口仍声明
   `userLogin` / `getLoginUser` / `getLoginUserVO` / `userLogout` 四个方法，实现类里没有对应实现
   （提交信息声称"内部委托 AuthService"，实际从未补上）。main 走的是另一套：`PasswordHasher` +
   单体 `UserServiceImpl`，四个方法都在实现类里。
2. **明文密钥入库。** `85f208e` 把 `application-local.yml` 连同 4 个真实 API Key 提交进了分支。
   该文件只在本地分支与 `backup/agent-dev-2026-04-05` 标签里，`origin/main` 与 `origin/agent-dev`
   都没有。本次移植脱离该文件，密钥仍需人工轮换。
3. **两处自我回滚的提交对。** `696e573` BCrypt 升级被 `7f72b7f` 撤销；`c66a695` 颜色搜索
   数据库预过滤被 `7bf3532` 撤销。两对在净树上等于不存在。

处置方式：以 main 为底，选择性移植 21 个提交，其余 12 个丢弃并记录理由。不用 `git rebase`
机械重放，是因为其中 8 个提交与 main 上已存在的等价实现正面冲突，重放只会把 main 的
设计推回去。

### 丢弃的提交

| 提交 | 内容 | 丢弃理由 |
| --- | --- | --- |
| `696e573` + `7f72b7f` | BCrypt 升级及其回滚 | 净变更等于零；main 已有 `PasswordHasher` 与逐次升级策略 |
| `c66a695` + `7bf3532` | 颜色搜索 DB 预过滤及其回滚 | 净变更等于零 |
| `6dcbe4c` | PasswordService → AuthService 改名 | 就是让分支不可编译的那次；main 没有这一层，强行带入只会制造第二套鉴权实现 |
| `f1f2d09` | 新增 `AuthController` | 与 main 的 `UserController` 并存会形成两套登录入口 |
| `0800def` | 前端鉴权接口迁移到 `/auth/` | 依赖上一条；main 的前端仍走 `userController` |
| `fd6dd3f` | 排序字段白名单 | main 有 `SortKeyResolver` 与 `SortKeyWhitelistIntegrationTest` |
| `d7b7c1f` | CORS 可配置白名单 | main 的 `CorsConfig` 已可配置，且额外有 CSRF/来源判定 |
| `b28d459` | 移除未使用的 echarts-wordcloud | main 的 `package.json` 里已经没有该依赖 |
| `0bebcde` | 热门推荐改 Redis ZSet | main 的推荐实现更新（`RecommendCacheManager` + 候选池 + 重排）并有 HTTP 用例覆盖 |
| `6c0d4d4` | 注册 MermaidDiagramTool / UndrawIllustrationTool | 属于「把未接线工具接上」的行为变更，不是本次遗留清理的范围；main 上这两个工具仍未注册，留作单独决策 |

## 2. RAG 线在 main 上的适配

移植过来的 21 个提交是「站内图片语义检索」这条线。它在 main 上无法原样运行，原因是
`feature/agent-dev` 按 spring-ai **1.1.2** 编写，main 是 **1.1.8**。

- `SearchRequest.SearchRequestBuilder` → `SearchRequest.Builder`；`builder.filter(...)` →
  `filterExpression(...)`。
- `Filter.eq/and/or` 静态助手在 1.1.8 不存在，改为 `Filter.Expression` + `Filter.Key/Value` 构造。
- `EmbeddingRequest` 只接受 `List<String>`，原先把 `List<Map<String,Object>>`（文本+图片）传进去
  的多模态入参已不可用。
- `UserMessage(String, List<Media>)` 构造器移除，改 `UserMessage.builder()`。
- `ChatClient.chatResponse()` 不再返回 `Optional`。

**向量化改为标准 `EmbeddingModel`。** 原实现把图文交给 DashScope 多模态模型，再手工
`setEmbedding` 注入 `Document`。1.1.8 下 Chroma 自动配置需要的是 `EmbeddingModel`，而
`DashScopeMultimodalEmbeddingModel` 实现的是 `DocumentEmbeddingModel`，两者接不上。
现在的做法：索引文本 = 名称 + 简介 + 分类 + 标签 + 视觉模型生成的富描述，交给 `VectorStore`
自己嵌入；查询侧用同一模型嵌入查询串。索引与检索必然同空间，也不需要业务层碰向量。

**启动安全。** 原先 RAG 无条件装配，没有 Chroma、没有 embedding 配额的环境会直接启动失败
（隔离验收既不启 Chroma 也没有真实密钥）。现在整条链路由 Spring AI 自己的开关控制：

```yaml
spring.ai.model.embedding.text   # none | dashscope
spring.ai.vectorstore.type       # none | chroma
```

基线 `application.yml` 两项都是 `none`；`application-prod.yml` 是 `dashscope` + `chroma`；
`src/test/resources/acceptance.yml` 显式 `none`。三个组件
（`PictureEmbeddingServiceImpl`、`InternalImageSearchTool`、`VectorStoreController`）以
`spring.ai.vectorstore.type=chroma` 为条件，`PictureServiceImpl` 与 `ImageAgent` 改为可选注入，
缺 Bean 时按「没有站内检索」继续工作。

`docker-compose.yml` 的 chromadb 服务原先挂在不存在的 `vision-space` 网络上，改为 `internal`，
并给 backend 补 `depends_on`。

**循环依赖。** 首轮 CI 的容器门禁抓到一个只在「开启 RAG」时才会出现的问题：

```
Error creating bean with name 'assetSearchAgent': Injection of resource dependencies failed
APPLICATION FAILED TO START
```

`PictureServiceImpl` 直接注入 `PictureEmbeddingService`，而后者又注入 `PictureService`，两个
Bean 互相依赖。Spring Boot 默认禁止循环引用，于是 `pictureServiceImpl` 建不出来，错误沿
`assetSearchAgent → AssetSearchService → PictureService` 冒到最外层。基线 / 验收跑的都是
`vectorstore.type=none`，`PictureEmbeddingServiceImpl` 根本不存在，所以 723 个测试全绿也发现不了。

改为注入 `ObjectProvider<PictureEmbeddingService>`，调用点按需取用并判空。顺带补齐了上传路径上
原本缺失的那处判空——另外三处调用点都有，只有它在 RAG 关闭时会拿着 null 直接调用。

**仍未解决的取舍**：`ChromaVectorStore` 在构造期就会连向量库并建集合，连不上直接让整个 Spring
上下文启动失败。也就是说，开启 RAG 的部署里 Chroma 不可用时后端起不来，靠 `restart: unless-stopped`
等 Chroma 就绪后重试。本轮不动这个行为（容器门禁会用真实 compose 栈验证它），但换部署环境时需要知道。

## 3. 本轮不引入 schema 变更

原分支在 `de26c03` 加了 `picture.visibility` 列，为的是给向量检索做可见性过滤。本轮把它去掉：

- 索引只收「已审核通过的公开图库图片」（`spaceId` 为空），可见性本来由审核状态与空间归属决定；
- 再加一个列等于用 schema 变更表达同一件事，而且会让这次发版强依赖一次生产库变更。

`InternalImageSearchTool` 的过滤条件改为：管理员不限制；普通用户只看公开图库
（`spaceId = 0`）；在自己空间内额外可见本空间自己的图片。索引侧不再写 `visibility` 元数据。

`src/main/resources/db/migration/V20260405__add_picture_visibility.sql` 一并删除——仓库里没有
Flyway，这个路径从来不会被执行，留着只会让人误以为迁移会自动运行。

## 4. 零引用遗留清理

每一项都在 rebase 后的树上重新核对过「零活引用」。

后端：

- `manager/sharding/PictureShardingAlgorithm`、`DynamicShardingManager`：整文件注释，分库分表
  方案从未启用；`pom.xml` 里对应的 `shardingsphere-jdbc-core` 注释块与 `SpaceServiceImpl`
  的两行注释一并删除
- `service/AIService`：无实现、无引用
- `model/dto/agent/AgentRunResponse`：流式协议最终改用 SSE 文本帧，该类零引用
- `model/dto/storage/StorageConfigActiveRequest`：无引用
- `FeedbackService.getFeedbackById`：控制器走 `getRequiredFeedback` + `getFeedbackVO`
- `SpaceUserService.getSpaceUserVO(SpaceUser, HttpServletRequest)`：列表版承担全部调用
- `StorageConfigService.getActiveStorageConfig`：无调用方
- `ImageSearchApiFacade.main`：调试入口
- `AiModelConfig.imageModel`：与 `ImageGeneratorTool` 的同名 `@Value` 重复且无读取方
- `resources/static/index.html`：脚手架遗留页面
- `application.yml` 的 `vision-space.aliyun-ai.base-url`：无代码读取

前端：

- `api-custom/mcp.ts` 的 `getMcpServer`
- `api-custom/resourceProtection.ts` 的 `GrantStatus`
- `utils/index.ts` 的 `debounce`（唯一使用者 `TestPage` 早已删除）

**刻意不动**：`frontend/src/api/` 整棵树是生成器产物，由 `scripts/openapi-contract.ps1`
逐字节对账，任何手工删除都会被下次生成还原并使门禁失败。

## 5. 部署前必须知道的三件事

1. **明文密钥需要轮换。** 分支历史里 `85f208e` 引入的 4 个 Key（DashScope、Pexels、
   dmxapi 视觉识别）应视为已泄露。它们没有进入 `origin/main`，但出现在本地分支与
   `backup/agent-dev-2026-04-05` 标签中。
2. **生产默认开启 RAG**，因此 compose 会同时拉起 `chromadb`。不打算跑向量库的部署需要同时设置
   `SPRING_AI_MODEL_EMBEDDING_TEXT=none` 与 `SPRING_AI_VECTORSTORE_TYPE=none`。
3. **首次启用 RAG 需要建索引**：调用一次 `POST /api/vector-store/full-sync`（管理员）。
   在此之前 `searchInternalImages` 返回空结果，对外表现为「站内搜不到图」，不会报错。

另有一处已知的降级：`ImageAgent` 的系统提示词无条件包含「优先调用 `searchInternalImages`」。
RAG 关闭时该工具不存在，模型拿不到它，只是白写了一句指引，不会报错。把提示词也做成
条件拼接留到后续工单，避免这次改动去动那段长文本。

## 6. 验收

`scripts/acceptance.ps1`（不带参数，隔离 MySQL + Redis 容器），head `ac4718e`，
runId `73982b524dc4431dadc6b395124b1d89`，14 个门禁全 PASS：

| 门禁 | 结果 | 数量 |
| --- | --- | --- |
| runner-tests | PASS | 3 |
| secret-scan | PASS | 890 个候选文件，0 命中 |
| isolation | PASS | |
| backend | PASS | 723 个测试，0 失败 |
| frontend-install | PASS | |
| frontend-lint | PASS | |
| frontend-format | PASS | |
| frontend-typecheck | PASS | |
| frontend-tests | PASS | 210 个测试 / 27 个文件 |
| frontend-build | PASS | |
| asset-color-calibration | PASS | |
| asset-search-browser | PASS | 3 个浏览器 IT |
| frontend-api-contract | PASS | 生成客户端与契约逐字节一致 |
| cleanup | PASS | |

过程中出现过两次环境性失败，都不是代码问题，记录在此以免下次误判：

1. **`frontend-install` 超时被掐断**导致后续 `backend` 报
   `Cannot find package 'playwright-core'`。npm ci 在本机只有 ~100 KB/s 单连接带宽，
   686 MB 的 `node_modules` 要跑近一小时；被中断时留下半截 `node_modules`，而
   `backend` 阶段在 `frontend-install` 之前执行，于是浏览器用例拿不到 `playwright-core`。
   缓存灌满后重跑即通过。
2. **`frontend-format`** 失败是 cherry-pick 冲突解决时漏跑 prettier（`AssistantMessage.vue`
   的 `v-for` div 被拆成多行），`ac4718e` 修掉。

`productionAcceptance` 为 `NOT_RUN`——生产库核对与真实部署不在本轮自动执行范围内。
