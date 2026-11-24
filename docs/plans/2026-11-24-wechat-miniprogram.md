# 微信小程序端 — 设计与实施计划

- 日期：2026-11-24
- 状态：设计已确认（经 /grill-me 逐轮拷问），M1 开工中
- 分支：`t3/miniprogram-m1-wechat-auth`

## 1. 目标

为 VisionSpace 增加一个微信小程序客户端，让用户在手机上：

1. 浏览公开图库（免登录）
2. 登录后浏览个人空间与团队空间，按关键词与颜色找回作品
3. 从相册/相机把图片上传进个人或团队空间

不做：Agent 对话、图片编辑/删除、成员管理、会员/反馈/空间分析、admin、协作编辑。第 2 期优先级：图片编辑与整理 > Agent 对话 > 成员与权限管理。

## 2. 已确认的决策

| 维度 | 决定 | 理由 |
|---|---|---|
| 主轴 | 登录后空间浏览 + 关键词/颜色检索 | 颜色与语义检索是产品核心，移动端增益最大 |
| 次轴 | 拍照/相册入库 | 手机是天然采集入口 |
| 主体 | 个人主体，仅内部体验 | 不做 AI 类目、不上架公开搜索 |
| 域名 | `visionspace.space` 已 ICP 备案 | 体验版同样校验备案域名 |
| 技术栈 | 原生小程序 + TypeScript | 平台能力直接、包体积与审核可控 |
| 导航 | 发现 / 空间 / 我的 三 tab | 游客可先看内容 |
| 主题 | 跟随系统：深色极光 / 浅色紫漾 | 两套主题同时成立，零配置 |
| 上传 | 多选≤9、串行、客户端压到 2MB、默认个人空间可选团队 | 后端单文件上传 + 200 张/日 + 1GiB |
| 点赞/收藏 | 只展示计数，不可点 | 后端无状态化点赞，空间图不可上报 |
| 分享 | 仅公开图库图片 | 避免接收方无权限 |
| 埋点 | 公开图库上报，新增小程序来源值 | 空间图片上报后端 403 |
| 绑定 | 微信 ↔ 账号 一对一 | 账号密码首次绑定，之后静默登录 |
| 工程 | 同仓库 `miniprogram/` | 契约共享、走同一 worktree + PR 流程 |
| 验收 | 无 DOM 的 TS 逻辑层 vitest + 真机清单 | 小程序无浏览器自动化 |

## 3. 现状事实（已核实）

- 登录态是 **Spring Session + `SESSION` Cookie**（Redis，30 天），不是 Sa-Token 令牌。
- Sa-Token 只用于空间鉴权（`StpKit.SPACE`，loginType=`space`），全仓无 `@SaCheckLogin`。
- CSRF 双提交：`VS_CSRF` Cookie + `X-CSRF-Token` 头 + 会话值三方一致（`CsrfProtectionFilter`）。
- CORS 生产只信同源；来源判定对「无 Origin 头」放行（`RequestOriginPolicy:38-42`）。
- `PictureVO.url/thumbnailUrl/previewUrl` 是**相对路径** `/picture/content/{id}/{variant}`。
- 内容接口只认会话，没有 header/query 令牌。
- 空间列表不按审核状态过滤；违规图 URL 置空、`quarantined=true`。
- 非管理员上传一律 `reviewStatus=0`，空间内立即可见，公开图库需 `reviewStatus=1`。
- `user` 表没有 openid/unionid/phone；注册不创建空间。
- 上传仅收 `file` + `id/fileUrl/picName/spaceId`；`picName` 空则取文件名。
- 配额：图库 200 张/日、总量 1GiB；超额返回 `code:50001` + HTTP 200。
- 颜色检索必须带 `spaceId`，返回最相近 12 张；公开图库不支持。
- 行为上报仅对公开图库可读图片生效，空间图片 403。

## 4. 后端改动（最小增量，Web 路径不变）

### 4.1 数据

- `user` 表新增 `wxOpenId varchar(128) NULL` + `UNIQUE INDEX uk_user_wx_open_id`。
  - 列名沿用仓库 camelCase 约定（`map-underscore-to-camel-case: false`）。
  - 解绑即置 `NULL`；软删用户因唯一索引含 NULL 不冲突。
- 迁移：`sql/migrations/2026-11-24-wechat-openid.sql` + `-rollback.sql`；
  同步更新 `sql/create_vision_space.sql` 全量 DDL。

### 4.2 新端点（`/user/wx/*`）

| 端点 | 请求 | 响应 |
|---|---|---|
| `POST /user/wx/login` | `{code}` | 成功：`{token, tokenName, user}`；未绑定：`code=40102` |
| `POST /user/wx/bind` | `{code, userAccount, userPassword}` | 同上；账号密码错误=40000，openid 已绑他人=40103 |

- `code` → `https://api.weixin.qq.com/sns/jscode2session` 换 `openid`。
- 绑定成功后签发 Sa-Token，并写入 `wxOpenId`（`UPDATE ... WHERE id=? AND wxOpenId IS NULL` 保证一对一）。
- 令牌使用 `StpKit.SPACE.login(userId)` + `getTokenValue()`；与 Web 登录共用同一账号体系。

### 4.3 令牌解析

- `UserServiceImpl.getLoginUser` 增加回退：**显式令牌优先**（请求头或查询参数带有效
  `satoken` 时按该令牌解析 userId），否则用浏览器会话；两者共用 `StpKit.SPACE` 存储。
- 内容接口 `/picture/content/**` 因此可直接用 `?satoken=` 被 `<image>` 加载。
- **风险**：令牌进入 nginx access_log。M1 同步给出 nginx 脱敏方案（对 `satoken` 参数打码），
  并保留后续改为短时 ticket 的接口位置。

### 4.4 CSRF 分流

- `CsrfProtectionFilter` 按凭据类型分流：
  - `SESSION`：保持现有「Origin + 双提交」全量校验（Web 字节级不变）。
  - `TOKEN`：跳过 Origin 与双提交（令牌不会被浏览器自动附带，不构成 CSRF 载体）。
  - `ANONYMOUS`：保持现有行为。

### 4.5 枚举

- `ActionSourceEnum` 新增 `MINI_PROGRAM(4, "小程序")`，旧值不变。

## 5. 小程序端设计

### 5.1 目录

```
miniprogram/
  project.config.json
  app.ts / app.json / app.wxss
  pages/
    discover/   公开图库：榜单切换 + 关键词 + 分类
    space/      空间列表（个人/团队）
    space-detail/ 图片网格 + 颜色检索 + 上传入口
    upload/     选图、改名称、压缩与串行上传
    picture/    详情：大图、元数据、计数、分享（仅公开）
    me/         资料 + 退出登录
    login/      账号密码绑定
  services/     请求层（令牌、错误码、重试）、契约类型
  logic/        无 DOM 的纯 TS：分页状态机、颜色检索、上传编排、埋点
  styles/       两套 token 的 WXSS
```

### 5.2 关键交互

- 图片加载：`<image src="{base}/api/picture/content/{id}/thumbnail?satoken={token}">`；
  公开图库不带令牌。
- 违规/无权限：`quarantined=true` 或 URL 为空 → 占位卡，不显示破图。
- 无空间：首次进空间 tab 查不到 `spaceType=0` 时，调 `POST /space/add` 建默认空间（需幂等处理）。
- 上传：`wx.chooseMedia` ≤9 → 逐张 `wx.compressImage`/质量压缩到 <2MB →
  `wx.uploadFile`（`file` + `spaceId` + `picName`）→ 全部完成后回空间刷新。
- 颜色检索：空间内色卡面板 + 从图取色，调用 `/picture/search/color`，展示 top12。

## 6. 里程碑

| 里程碑 | 内容 | 验收 |
|---|---|---|
| **M1 后端底座** | 迁移 + openid + `/user/wx/*` + 令牌解析 + CSRF 分流 + 来源枚举 + nginx 脱敏 | 新增单元/集成测试；Web 既有 acceptance 不回归 |
| M2 公开图库 | 发现 tab：榜单/关键词/分类、详情、分享、匿名埋点 | 真机清单 + 逻辑层单测 |
| M3 空间浏览 | 登录/绑定、空间列表、网格、详情、颜色检索 | 真机清单 + 逻辑层单测 |
| M4 上传 | 选图/压缩/串行上传/配额错误提示 | 真机清单 + 逻辑层单测 |

每刀一个 PR，不做长期驻留的大分支。

## 7. 风险与对策

| 风险 | 对策 |
|---|---|
| 令牌进 access_log | nginx 日志对 `satoken` 参数脱敏；M2 前评估短时 ticket |
| 空间列表含违规图 | 占位卡 + `quarantined` 分支 |
| 上传待审核但空间可见 | 与 Web 一致；不加额外状态提示（VO 无 reviewStatus） |
| 个人主体不能用手机号登录 | 账号密码绑定，已确认 |
| 小程序无自动化 UI 测试 | 逻辑层抽离 + vitest；UI 真机清单 |
| `StpKit.SPACE` 令牌与 Web 会话并存 | 登录/登出各自清理；共享同一账号与 30 天有效期 |

## 8. 明确不做

- Agent 对话与语义检索、图片编辑/删除/批量、空间成员管理、会员兑换、用户中心业务分区、
  空间分析、反馈、admin/moderation、实时协作编辑。
- 不新增第三套主题；不改现有 `/user/center` 与全局页头页脚。

## 9. M1 实施记录（2026-11-24）

已落地：

- `user.wxOpenId` 列 + 唯一索引（前向迁移、回滚说明、全量 DDL 同步）。
- `POST /user/wx/login`、`POST /user/wx/bind`；一对一绑定、幂等重绑、并发由唯一索引兜底。
- `TokenAuthenticationSupport`：请求头 / 查询参数读取 `satoken`，`getLoginUser` 令牌优先。
- `CsrfProtectionFilter` 按凭据类型分流：`TOKEN` 跳过双提交与 Origin，`SESSION` 行为不变。
- `ActionSourceEnum.MINI_PROGRAM(4)`。
- nginx 三个入口配置对 `satoken` 查询参数做日志脱敏；`.env.example` / compose 增加可选配置。

验证证据：

- `HttpMiniProgramAuthTest` 9/9。
- 回归：`HttpAccountSecurityTest` 12/12、`HttpPictureActionReportingTest` 10/10、
  `HttpPublicGalleryTest` 4/4、`HttpSpaceAuthorizationTest` 9/9、
  `HttpForgedAuthorizationTest` 5/5、`HttpFeedbackProtectionTest` 10/10、
  `HttpPictureGrantTest` 9/9。
- `HttpPictureContentAuthTest` 11/12：唯一失败项是浏览器矩阵脚本，原因是本 worktree 未安装
  `frontend/node_modules`（`ERR_MODULE_NOT_FOUND: playwright-core`），与本次改动无关。

**环境注意**：Windows 原生 Redis 最高 5.0.14，Sa-Token 1.46 写会话时使用 Redis 5 不支持的
`SET` 语法（`ERR syntax error`），会导致**任何**登录类验收失败。以上测试改用 Docker
`redis:7.2.4` 跑隔离环境；原生 Redis 5 不可用于本套件的 HTTP 验收。


## 10. M2 实施记录（2026-11-24）

已落地（`miniprogram/`，原生 TS）：

- 工程：`project.config.json`（`useCompilerPlugins: ["typescript"]`，appid 为占位
  `touristappid`，导入后需替换）、两套 tsconfig（小程序 CommonJS / 测试 ESM）、
  Vitest、`theme.json`（深色极光 / 浅色紫漾，导航栏与 tabBar 均用 @变量）。
- 发现 tab：榜单 hot/latest/quality/random + `POST /picture/list/page/vo`
  关键词/分类检索；`contextValid=false` 时整体重置；id 去重；上拉分页、
  下拉刷新、错误重试、空态。
- 详情页：`GET /picture/get/vo`，公开作品（`spaceId=null`）才出现分享入口；
  展示浏览/点赞/收藏/分享计数但不可点；隔离/无内容显示占位。
- 匿名埋点：曝光（相交 50% 触发，`observeAll` + 闸门去重）、点击、浏览，
  `source=4`（M1 新增的 `MINI_PROGRAM`），`eventId` 按后端正则生成。
- 主题：WXSS 变量对齐 Web 端色值，`prefers-color-scheme` 跟随系统。
- CI：`.github/workflows/miniprogram.yml` 只跑 `type-check` + `vitest`，
  paths 过滤 `miniprogram/**`。

关键取舍：

- M2 的 tabBar 只有 发现 / 我的（原计划三 tab）；空间与"我的"资料在 M3 补齐。
  微信要求 tabBar 至少两项，因此先放一个只读的「关于」页。
- 推荐接口返回 `RecommendPictureVO`（无作者/计数），详情统一再取一次
  `PictureVO`，避免为列表补字段改后端。
- 列表页 size=12（检索接口上限 30，推荐接口上限 100）。

验证证据：

- `npm run type-check`（src + tests）通过。
- `npm test`：4 个测试文件 43 项全通过（分页状态机、展示格式化、
  埋点去重与 eventId、错误码文案）。
- 真机清单（见 `miniprogram/README.md`）尚未在真机执行，需开发者工具 + 体验版手动过。

### 10.1 代码审查修正（PR #160）

两轴审查（Standards / Spec）后修正：

- **Long 类型**：`JsonConfig` 把所有 Long 序列化成十进制字符串，客户端此前把 id/计数
  写成 `number`。改为 `WireLong = number | string`，计数/体积经 `toNumberOrNull` 处理；
  id 后来发现不能转数字（19 位雪花 id 超出安全整数，见 §13），改为全程字符串。
- **分页到底判定**：不再用「本页未拿满」判断结束（后端会在页内过滤隔离/删除记录），
  改为「本页为空或累计达到 total」才停；无效 total 不再被当成 0。
- **分享入口**：详情页加载一开始就 `hideShareMenu`，失败/非公开作品不会留下可转发入口。
- **sitemap**：改为全站 disallow，与「个人主体、不上架公开搜索」一致。
- **重复代码**：抽取 `services/telemetry.reportOnce`（闸门 + eventId + 静默失败）与
  `logic/errors.errorMessage`；榜单 key 直接来自 `RECOMMEND_TYPES`。
- 测试 43 → 47 项。

## 11. M3 实施记录（2026-11-24）

已落地：

- **登录**：`pages/login` 先 `wx.login` → `POST /user/wx/login`；40102 才展示
  账号密码绑定表单，绑定每次重新取 code；成功后存令牌 + 令牌名 + 脱敏用户。
  「我的」展示昵称/ID、退出登录（后端 50001「未登录」也照常清本地）。
- **空间列表**：没有 `/space/list/my`，用 `POST /space/list/page/vo`（pageSize=20，
  非管理员上限）+ `POST /spaceUser/list/my` 合并角色；个人空间无成员记录，
  所有者按 `SpaceVO.userId` 判定为「创建者」。注册不建空间，进入空间页时用
  `spaceType=0&pageSize=1` 单独查个人空间是否存在，缺失才 `POST /space/add`，
  建完再查一次确认（50001 只当作「可能已存在」，复查不到仍报错）。
- **空间详情**：`POST /picture/list/page/vo` 带 `spaceId`，分页 12；隔离行由后端
  置空 URL 并带 `quarantined=true`，卡片显示「内容已隔离」。
- **颜色检索**：`POST /picture/search/color`（top 12，无分页）；12 色色卡 +
  「从图片取色」（离屏 canvas 32×32 取平均色），失败提示改用色卡。
- **图片访问**：空间作品的内容 URL 在查询参数带 `satoken`（`<image>` 无法带头）；
  详情统一带令牌请求，40100 引导重新登录，40101 按本地是否还有令牌区分
  「登录后查看」与「确实无权限」；公开作品的图片 URL 不带令牌。
- **埋点/分享**：空间作品不报行为（后端 403），也不出现分享入口；公开作品保持 M2 行为。
- 主题：新增页面沿用同一套 token，未新增第三套配色。

关键取舍：

- 颜色检索返回无距离字段，展示顺序即后端相似度顺序。
- 空间列表仍一次取 20 个；存在性判断已改为服务端 `spaceType` 过滤，不受此限制。
- 取色依赖离屏 canvas 2d，旧机型失败时降级到色卡，不阻塞检索。

验证证据：

- `npm run type-check`（src + tests）通过。
- `npm test`：8 个测试文件 82 项全通过（新增颜色、空间合并、账号校验、URL 拼接）。
- 真机清单（M2 + M3）尚未执行，需开发者工具 + 体验版手动过。

### 11.1 代码审查修正（PR #161）

两轴审查（Standards / Spec）后修正：

- **错误码语义**：`50001` 被建空间、上传配额等多处复用，`describeError` 不再写死
  「上传额度已用完」，改为优先用服务端文案；新增 `isLoginExpired` /`isPermissionDenied`
  谓词，页面不再散落 `code === 40100`。
- **40100 vs 40101**：作品详情只在令牌过期、或本就没登录时提示去登录；已登录成员
  的 40101 只显示「仅对空间成员可见」，避免误导重登。`fetchPicture` 里死代码
  （40100 后匿名重试，该接口对过期令牌按匿名处理）已删除。
- **登录态模型统一**：`logic/auth.StoredProfile` 成为唯一缓存模型，`session` 复用
  `toStoredProfile` 校验；删掉重复的 `StoredUser` 与中间层类型转换。
- **空间存在性**：改为 `spaceType` 过滤 + `pageSize=1` 判断（原来只看第一页 20 条，
  空间多时会在已有个人空间的情况下重复建）；建完复查，50001 不再无条件吞掉。
- **空间详情**：不再调用 `/space/get/vo`（普通成员无权限、且为一行类型文案多打一次
  请求），类型由列表页通过 URL 传入；40100 时展示登录引导。
- **重复代码**：抽取 `services/navigation.openLogin`、`resetColorFilter()`、
  `belongsToSpace()`；`spaceTypeLabel()` 统一「个人/团队空间」文案；`normalizeHex`
  文档与实现对齐（三位简写展开为六位）。
- 测试 78 → 82 项。

## 12. M4 实施记录（2026-11-24）

已落地：

- **入口**：空间列表按「所有者 / admin / editor」计算 `canUploadToSpace`，通过 URL
  带到空间详情；只读成员看不到「上传作品」。真正的权限仍由后端
  `SpaceAuthority.canOperateSpace(PICTURE_UPLOAD)` 复核。
- **选图**：`wx.chooseMedia`（album + camera，sizeType compressed），一次最多 9 张，
  选满后入口换成「已选满 9 张」提示。
- **压缩**：后端 `FilePictureUpload` 硬限制 2MB、只收 jpeg/png/jpg/webp。
  客户端先用 `FileSystemManager.getFileInfo` 读大小；格式合法且未超限就原样用，
  否则按 `COMPRESS_STEPS` 逐档重压：质量 80→60→40，最后两档在 1600 / 1200 宽下
  再降到质量 25 / 20；每次都从原图压，避免画质叠加损失；仍超限才报「请换一张」。
  压缩结果复制到 `wx.env.USER_DATA_PATH/vs-upload-*` 固定后缀，离开页面上传结束时清理。
- **命名**：默认取文件名（机器名如 `tmp_xxx` 回退「作品 N」），可逐张修改，
  折叠空白、40 字截断、空名回退。
- **上传**：`wx.uploadFile` 串行队列，part 名 `file`，表单字段 `spaceId` / `picName`，
  令牌走同一 `tokenName` 头；`onProgressUpdate` 驱动逐张进度条。
  响应仍是 `BaseResponse`，复用 `toApiError` 映射；40100 清令牌、停止队列并引导重登。
- **失败处理**：单张失败只标红该张，其余继续；按钮变为「重试失败项」，
  已压缩文件直接复用，不重复压缩。配额/频控等 50001 展示服务端文案。
- **回空间刷新**：只要有任意一张成功，上传页就写一次性 `markSpaceUploaded(spaceId)`，
  部分失败也不会让空间停在旧数据；空间详情在 `onShow` 用
  `consumeSpaceUploaded` 消费后重新拉第一页。
- **离场保护**：页面销毁即停止队列，避免对已销毁页面 setData；队列跑完再回收
  用户目录里的待上传文件，不会删掉正在上传的那一张。

关键取舍：

- 全程串行：微信并发上传容易触发网关限流，也让「部分失败可重试」的状态更简单。
- 上传页不判断角色，只负责上传；权限错误按 40101 文案展示，避免前后端规则漂移。
- 入口收敛为「先进入空间再上传」：计划 §2 的「默认个人空间可选团队」在 M4 没有做成
  上传页内的空间选择器，选一个空间反而多一步；跨空间批量上传留到后续再评估。
- 上传后的作品是 REVIEWING 状态，空间列表按成员可见（公开图库仍需审核通过）。

验证证据：

- `npm run type-check`（src + tests）通过。
- `npm test`：9 个测试文件 104 项全通过（新增上传队列规则、权限判定）。
- 真机清单（M2–M4）尚未执行，需开发者工具 + 体验版手动过；上传相关项见 README。

## 13. 全面审查修正（M1–M4 合并后）

两轴（Standards / Spec）对 `origin/main...HEAD` 整体复审后修正：

- **阻塞级：雪花 id 被 Number 舍入**。`Picture`/`Space`/`User` 用 `IdType.ASSIGN_ID`，
  实际 id 是 19 位（如 `2038608856269361154`），`JsonConfig` 以字符串下发；M2 的
  `toNumberOrNull` 又把它转回 number，`Number()` 会把末几位舍入。列表里的缩略图
  是服务端拼的完整 URL，所以看起来正常，一点卡片就跳到不存在的 id（40400），
  颜色检索/上传也会打到错误空间。改为 id 全程十进制字符串：
  `logic/long.toIdString` / `isValidId`，`GalleryCard.id`、`SpaceSummary.id`、
  `StoredProfile.id`、`ActionEvent.pictureId` 与所有页面路由/接口参数统一用 string；
  number 只留给计数、体积、页码。补 19 位 id 的去重/路由/埋点单测。
- **令牌名契约**：`AuthTokenProperties` 的头名与查询参数名可分别配置，但
  `WxLoginVO` 只下发头名，客户端把同一个名字拼进图片 URL；一旦两处配置不同，
  `<image>` 鉴权失败且 nginx 的 `satoken=` 脱敏规则失效。`WxLoginVO` 增加
  `tokenQueryName`，小程序单独保存并用于内容 URL。
- **CSRF 过滤**：`classify()` 每个状态修改请求最多解析两次 Sa-Token，改为一次判定、
  复用结果；删除随之不再使用的 `isAuthenticated` 辅助方法。
- **死代码**：删除无调用方的 `pickDefaultSpace`、`isValidEventId`、
  `ReportGate.reset/size`，对应测试改为可证伪的行为断言。
- **数据契约**：`WxLoginVO` 的接受度测试补 `tokenQueryName` 断言。
- 验证：`mvn -q -DskipTests compile` 通过；小程序 `npm run type-check` 通过，
  `npm test` 10 个文件 109 项全通过。真机清单仍未执行。

## 14. M5 实施记录（2026-11-24）

### 14.1 目标

§13 之后剩下的最大风险是把令牌放进图片 URL：查询参数会进 nginx 访问日志、Referer
与图片缓存，等于把凭据散出去。本轮把私有图片改成「带鉴权头下载到本地再展示」，
公开图库不受影响。

### 14.2 落地

- `logic/image-queue.ts`：纯逻辑下载队列——限流（默认并发 4）、同 URL 去重、
  成功路径按 URL 缓存、失败只回调不抛出；可单测，不依赖 wx API。
- `services/image-download.ts`：`wx.downloadFile` 带 `satoken` 头下载，返回
  `tempFilePath`；失败抛 `ImageDownloadError`（带状态码，401 触发重新登录引导）。
- `logic/gallery.ts`：卡片新增 `localPath` / `src` / `pending` 三个字段。公开图库
  走 `toCard`（`src` 直接是远程地址）；私有图走 `toPrivateCard`（先占位），
  下载完成后由 `withLocalPath` / `withDownloadFailure` 收尾。
- `pages/space-detail`：列表与颜色检索结果都走私有卡片；翻页重建列表时优先复用
  队列缓存，避免已下载的图重新闪一次占位。
- `pages/picture`：空间作品的大图与原图预览都先鉴权下载；原图预览走本地路径。
- 删除 `logic/url.ts`（`withTokenQuery`）与 `tests/url.test.ts`：令牌不再进 URL，
  客户端也不再保存 `tokenQueryName`。服务端仍在登录响应里下发该字段，作为
  「无请求头场景的查询参数名」契约说明。

### 14.3 验证

- 小程序 `npm run type-check` 通过；`npm test` 10 个文件 116 项全通过
  （新增 `tests/image-queue.test.ts` 6 项与 `toPrivateCard` 相关 5 项）。
- 真机清单新增「M5 私有图片鉴权下载」一节，仍未执行。

### 14.4 后续（未做）

- 大量私有缩略图若实测首屏过慢，再评估短期签名 URL；本轮不动服务端私有图片
  安全模型。
- Agent 的 SSE 接入层（开流前用 401/403/404/429 状态码、流内错误事件、
  增量 UTF-8 解码）尚未开始，属第 2 期。
