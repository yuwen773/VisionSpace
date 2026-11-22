# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- **个人创作者**：上传、整理、审核自己的 AI 图片，按颜色 / 语义找回作品，并与 SpaceMind 对话完成扩图与创作。
- **团队成员**：在共享空间里按 viewer / editor / admin 角色协作管理图片与成员。
- **平台管理员与违规处置员**：分别承担平台运营与违规资源隔离/恢复，职责互不继承。

## Product Purpose

VisionSpace 是一个 AI 图片创作、检索与协作平台：把图片放进个人空间或团队空间，用颜色、语义与长期偏好把它们重新找出来，并通过 SpaceMind 继续创作。成功意味着用户能快速定位自己的作品、理解自己的使用情况，并掌控 AI 对自己的记忆。

## Positioning

不是又一个图床：VisionSpace 让「颜色 + 语义 + 用户显式偏好」共同决定检索与推荐，并把这些能力放进有权限边界的空间模型里。

## Operating Context

- 浏览器中使用，中文界面为主；桌面与移动端都要可用。
- 登录后从全局页头进入「我的空间」（跳转到个人空间）或「用户中心」。
- 用户中心是账号与作品档案的家：个人资料、我的图片与统计、会员权益与兑换、AI 长期偏好、意见反馈。
- 主题由用户切换：极光（深色）与紫漾（浅色），偏好保存在本地。

## Capabilities and Constraints

- 用户中心五个分区及其接口必须保留：`getLoginUser`、`updateUser`、`uploadAvatar`、`getUserPictureStats`、`getUserRecentPictures`、`exchangeVip`、偏好读写与清除、反馈提交与附件上传。
- 会员资格只由 `vipExpireTime` 是否在将来决定，与 `userRole` 无关；兑换码结果分 success / already_used / invalid。
- 自动学习默认关闭；显式偏好优先于 AI 推断；清除偏好不可恢复。
- 头像 ≤2MB，反馈附件 ≤5 张、单张 ≤5MB。
- 双主题必须同时成立；颜色对比度需达到正文 4.5:1。
- 现有路由 `/user/center`、`requiresAuth`、全局页头与页脚不可改动。

## Brand Commitments

- 产品名 VisionSpace / SpaceMind；中文界面。
- 两套既有主题名：极光（深空暗色）与紫漾（浅紫粉）。本次不新增第三套主题。
- 奖项级视觉方向已在 Landing 与 Explore 上确立：编辑式排版、衬线大标题、等宽元数据、细线分隔、图片承担色彩、克制动效。

## Evidence on Hand

- 真实产品代码与接口定义在 `frontend/src`、后端 `src/main/java`。
- 没有品牌摄影素材库；页面中的作品图只能来自用户真实上传数据或设计好的空状态，不得伪造作品。

## Product Principles

1. 作品优先：界面先回答「我有哪些作品、它们怎么样」，再谈设置。
2. 用户的记忆由用户掌控：偏好可见、可改、可清除，默认不学习。
3. 权限边界清晰：私人、团队、平台角色的能力不互相继承。
4. 熟悉的任务用熟悉的控件，品牌表达放在排版与节奏里。
5. 深色与浅色是同一套版式的两种光线，不是两套设计。

## Accessibility & Inclusion

- 键盘可达：导航、编辑、上传、兑换、提交全部可聚焦操作，焦点可见。
- 正文对比度 ≥4.5:1；尊重 `prefers-reduced-motion`。
