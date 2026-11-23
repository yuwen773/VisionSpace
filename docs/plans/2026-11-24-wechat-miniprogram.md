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

- `UserServiceImpl.getLoginUser` 增加回退：会话无用户时，读取请求头或查询参数 `satoken`，
  经 `StpKit.SPACE.getLoginIdByToken` 解析 userId 并加载用户。
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

