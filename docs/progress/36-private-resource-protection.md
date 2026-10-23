# #36 私有资源管理与反馈保护

工单：[36](https://github.com/yuwen773/VisionSpace/issues/36)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26)。
分支：`codex/36-private-resource-protection`。本文只记录本次实际执行到的证据，未执行的部分单列在「人工关卡」。

## 改动结构

| 位置 | 作用 |
| --- | --- |
| `feedback_attachment` 表 | 反馈附件的可信记录：上传者、绑定反馈、平台、对象键、地址、状态（UPLOADED/BOUND/LEGACY_VERIFIED/BLOCKED）、旧地址摘要 |
| `feedback_attachment_archive_20261023` 表 | 回滚归档：回滚前把附件的定位、归属与绑定记录搬到这里，再删业务表；重新部署本工单时搬回 |
| `resource_access_grant` 表 | 单资源限时只读授权：被授权人、授权主体、到期时间、状态（ACTIVE/REVOKED/EXPIRED）、撤销人与理由；`activeKey` 唯一约束表达「同一资源同一对象最多一条有效授权」 |
| `resource_isolation` 表 | 违规资源隔离与恢复：资源类型与标识、状态（QUARANTINED/RELEASED）、操作者、理由、恢复人与恢复理由；`activeKey` 唯一约束表达「同一资源最多一条有效隔离」 |
| `FeedbackAttachmentServiceImpl` | 登记上传、把附件绑定到反馈、按当前反馈权限读取内容、生成受控入口 `/feedback/attachment/{id}` |
| `FeedbackAccessPolicy` | 反馈可见性的唯一判定来源：创建者本人与具有反馈管理职责的平台管理员；控制器与附件服务共用，不再各写一份 |
| `FeedbackController` | 反馈详情按「创建者 + 有反馈管理职责的平台管理员」判定；附件内容每次请求按当前权限与隔离状态判定 |
| `ResourceAccessGrantServiceImpl` | 授予、撤销、按资源列出、按被授权人列出；`canGrant` 只认个人资源所有者与团队管理员身份（平台管理员独立持有的团队管理员身份照常生效） |
| `ResourceGrantExpireJob` | 每 5 分钟把到期授权迁移为 EXPIRED 并释放 `activeKey`：只改展示状态的话无法重新授权 |
| `ResourceIsolationServiceImpl` | 隔离、恢复、是否隔离、隔离中列表、处置历史 |
| `PictureContentAuthManager` | 图片内容读取判定：先看隔离，再看归属，最后看有效授权；隔离中的内容一律拒绝读取，处置职责只看元数据 |
| `ResourceModerationController` | 独立处置角色（`moderator`）的隔离/恢复入口；只返回处置元数据，不返回内容或直链 |
| `UserRoleEnum` / `AuthInterceptor` | 新增 `moderator` 角色；角色改为严格相等，管理职责不互相继承 |
| `IdentifierUtils` | 标识按十进制字符串解析：雪花标识超出 JavaScript 安全整数范围，客户端转成 number 会丢精度 |
| `frontend/src/api-custom/resourceProtection.ts` | 授权有效期约束（默认 7 天、最长 30 天）、授权/附件/隔离状态与资源类型文案、管理资格与附件标识的取值规则 |
| `frontend/src/components/picture/PictureGrantPanel.vue` | 图片详情的授权面板：授予、有效授权列表、撤销、到期提示；授予与撤销入口按服务端给出的管理资格显示 |
| `frontend/src/pages/moderation/ModerationPage.vue` | 内容处置台：隔离、隔离列表、恢复、处置历史（只含元数据） |
| `frontend/src/components/user-center/FeedbackSection.vue` | 反馈附件改为上传登记 + 提交标识，渲染走受控入口 |
| `frontend/src/pages/admin/FeedbackManagePage.vue` | 反馈管理改用 `userName` 与 `attachments`；无受控入口的附件只显示状态 |

## 验收对照

| 验收项 | 证据 | 状态 |
| --- | --- | --- |
| 14.1 匿名/其他用户/创建者/管理员的允许-拒绝矩阵，管理职责不扩展 | `HttpFeedbackProtectionTest#detailIsReadableOnlyByCreatorAndAdminWithFeedbackDuty`、`#adminRoleDoesNotExtendToOtherPrivateResources`、`#anonymousDetailRequestIsRejectedWithoutServerError`；`HttpResourceQuarantineTest#adminContentReadOfPrivateSpaceIsDeniedButReviewDutyRemains` | 已覆盖 |
| 14.2 响应只返回必要用户信息与受控附件标识，无原始 URL 旁路 | `HttpFeedbackProtectionTest#responseIsMinimizedAndAttachmentsHaveNoOriginAddress`（`FeedbackVO` 只含 `userName`/`userId`，`FeedbackAttachmentVO` 只含 `id`/`url`/`status`）、`#rawUrlsCannotBeUsedAsAttachments`；`HttpResourceQuarantineTest#moderationViewsExposeMetadataOnly` | 已覆盖 |
| 14.3 附件实际请求按反馈权限判定，客户端能正常展示 | `HttpFeedbackProtectionTest#attachmentContentFollowsCurrentFeedbackPermission`、`#unboundAttachmentIsOnlyReadableByItsUploader`；前端 `FeedbackSection.vue` 走 `contentSrc()` 渲染 `contentSrc` 受控入口，后台详情走 `getFeedbackById` 取带入口的附件；附件标识按十进制字符串提交（`api-custom/resourceProtection.ts#attachmentIdsOf`），后端 `HttpFeedbackProtectionTest#attachmentIdsBindWhetherTheyArriveAsStringOrNumber` 覆盖字符串与非法标识 | 已覆盖（HTTP + 前端代码层 + 前端单测） |
| 14.4 旧附件归属从可信记录核对，不能确认时阻断，不自动公开或丢弃 | 迁移脚本把旧附件登记为 BLOCKED（保留原文在 `user_feedback.pictureUrls`），已受控登记的引用不重复登记；`HttpFeedbackProtectionTest#legacyAttachmentWithUnconfirmedOwnershipIsBlockedAndNotPublic`；`sql/migrations/2026-10-23-private-resource-protection-blocklist.sql` 输出脱敏阻断清单 | 已覆盖 |
| 15.1 个人资源仅所有者、团队资源仅有权团队管理员可授权，查看者/资源/到期受校验 | `HttpPictureGrantTest#onlyOwnerOrTeamAdminMayGrant`、`#expiryIsMandatoryAndBounded`（到期必填、不得超过 30 天、不得早于当前时间）、`#teamMemberRemovalAlsoCancelsGrantBasedRead`、`#platformAdminKeepsTeamAdminAuthorityButNotStrangersAuthority`（平台管理员独立持有的团队管理员身份照常生效，平台身份本身不构成授权依据） | 已覆盖 |
| 15.2 UI 打通授予、列表、撤销与到期提示，不默认整空间或无限期 | `frontend/src/components/picture/PictureGrantPanel.vue`：默认 7 天、上限 30 天、必须显式到期；`api-custom/resourceProtection.ts#grantExpiresTime` 夹紧范围；`#grantExpiresSoon` 给出临近到期提示；授予与撤销入口按服务端返回的管理资格（`#canManageGrants`）显示，不再按平台角色一律排除 | 已覆盖（前端代码层 + 单测） |
| 15.3 每次读取校验当前授权，跨资源/过期/撤销/身份错误立即拒绝，两个实例一致 | `HttpPictureGrantTest#ownerGrantsAndGranteeReadsUntilExpiry`、`#revocationTakesEffectOnTheNextRequest`、`#grantCannotReviveQuarantinedResource`、`#expiredGrantReleasesItsSlotSoTheResourceCanBeGrantedAgain`（到期即失效并释放唯一键，可以重新授权）；判定只查库、不做进程内缓存 | 已覆盖（单实例进程；双实例见「人工关卡」） |
| 15.4 审计记录授权与撤销主体、范围、结果，不含内容或密钥，附迁移与回滚验证 | `resource_access_grant` 记录授权人、被授权人、到期、撤销人、撤销理由与时间；`ResourceGrantVO` 不含内容与地址；`PrivateResourceMigrationTest` 五个用例覆盖前向、幂等、旧附件登记、回滚守卫，以及回滚后附件定位记录仍在归档表、重新部署时搬回 | 已覆盖（隔离库） |
| 16.1 独立操作权限校验目标、操作者与理由，后台只展示必要元数据 | `ResourceModerationController` 全部入口 `@AuthCheck(mustRole = MODERATOR_ROLE)`，理由必填、目标必须存在；`HttpResourceQuarantineTest#quarantineRequiresIndependentModerationPermission`、`#moderationViewsExposeMetadataOnly`（处置人员只有元数据权限，不能读取隔离中的内容） | 已覆盖 |
| 16.2 隔离后公开与私人访问都拒绝，保留内容、引用与可恢复状态 | `HttpResourceQuarantineTest#quarantinedPublicContentIsDeniedEverywhereButKept`、`#quarantinedPrivateContentIsDeniedIncludingExplicitGrants`（显式授权也不能绕过隔离）、`#quarantinedResourceCannotBeReplacedByReupload`（重新上传不能替换隔离资源、销毁旧对象） | 已覆盖 |
| 16.3 恢复有明确权限和审计，不错误扩展访问范围 | `HttpResourceQuarantineTest#releaseRestoresOriginalAccessRulesWithoutWidening`、`#releaseRequiresActiveQuarantineAndReason` | 已覆盖 |
| 16.4 操作者、理由、目标、状态、结果可核对，直接实体查询不绕过隔离 | `resource_isolation` 记录目标/操作者/理由/状态/恢复信息；`/moderation/list` 与 `/moderation/history` 只读元数据；`HttpResourceQuarantineTest#quarantineRequiresIndependentModerationPermission` 覆盖非处置角色被拒 | 已覆盖（隔离库） |

## 本次实际执行的验证

2026-10-23，Windows + JDK 23 / Maven / Node / PowerShell 7，原生隔离 MySQL 8.0.26 + Redis 3.2.100（本机 Docker 不可用，因此不是容器实测）：

```powershell
pwsh.exe -NoLogo -NoProfile -File ./scripts/acceptance.ps1 `
  -MySqlBin '<MySQL 8.0.26 bin>' -RedisBin '<Redis-x64-3.2.100>'
```

结果见 `target/acceptance/summary.json`：九个结果分类全部 PASS（`runner-tests`、`secret-scan`、`isolation`、`backend`、`frontend-install`、`frontend-typecheck`、`frontend-tests`、`frontend-build`、`cleanup`），退出 0。

- 后端共 294 条测试，0 失败、0 错误、0 跳过。其中本工单 `HttpPictureGrantTest` 9 条、`HttpResourceQuarantineTest` 8 条、`HttpFeedbackProtectionTest` 9 条、`PrivateResourceMigrationTest` 5 条、`PictureContentAuthManagerTest` 10 条。
- 前端共 6 个测试文件 43 条（`api-custom/__tests__/resourceProtection.spec.ts` 17 条，`utils.spec.ts` 2 条），类型检查与生产构建均通过。
- 评审提出的 6 项缺陷与 1 项 P3 异味修复后完整重跑一轮，首轮即全绿，未再出现失败。
- 前几轮完整运行各出现 1 条失败，均已定位并修正，未放宽断言：
  1. `secret-scan` 命中本会话临时生成的本地验收配置（`.local-verify/openapi/acceptance.yml`），删除该目录后通过；
  2. `PrivateResourceMigrationTest#attachmentsAlreadyTrackedAreNotRegisteredAsLegacy` 先因插入时目标表尚未建立而报错（先执行前向脚本再建数据），修正后又暴露出 `SUBSTRING` 起始位与 `CAST` 截断告警，改为按字符串比较附件标识后通过。

浏览器链路另跑一轮（同样用原生隔离 MySQL/Redis）：

```powershell
pwsh.exe -NoLogo -NoProfile -File ./scripts/browser-acceptance.ps1 `
  -MySqlBin '<MySQL 8.0.26 bin>' -RedisBin '<Redis-x64-3.2.100>' -SkipInstall
```

`isolation`、`backend`、`browser`、`cleanup` 全部 PASS，浏览器 9 条场景全通过。该套场景是 HTTPS/Secure Cookie/CSRF/SSE 同源链路，作为本工单的回归，不覆盖 #36 自身的交互。

## 人工关卡（未完成，不计入通过）

1. **生产迁移与回滚演练。** `PrivateResourceMigrationTest` 在隔离库的临时表名上验证脚本可执行、幂等、回滚守卫生效，以及「回滚归档附件定位记录 → 重新部署搬回」的往返；不等于生产库（含数据量、锁、备份恢复）演练通过。
2. **存量旧附件的人工核对。** 旧附件一律登记为 BLOCKED，需要人工按脱敏阻断清单核对归属后单独登记为 LEGACY_VERIFIED；本轮没有真实存量数据可核对。
3. **双实例验收。** 15.3 的「两个实例一致」依赖「每次请求查库判定」，本轮只在单进程内验证。
4. **#36 场景的浏览器端 E2E。** 浏览器验收脚本覆盖的是 HTTPS/Secure Cookie/CSRF/SSE 同源链路，作为回归跑通；反馈附件受控渲染、授权面板与内容处置台没有真实浏览器交互证据。
5. **真实对象存储的直链失效。** 存量 `picture.url` 与旧附件地址在源站是否可匿名访问，需人工在对象存储侧收紧；本工单只保证应用层不再返回这些地址。

## 已知取舍

- 授权面板需要输入被授权用户 ID：平台没有面向普通用户的用户目录接口，开放搜索会扩大个人信息暴露面。
- 授权的授予与撤销资格完全由服务端 `canGrant` 给出，前端不再按角色自行推断：个人资源看所有者，团队资源看团队管理员身份。平台管理员若本人就是资源所有者，或独立持有该团队的团队管理员身份，管理入口照常出现；平台身份本身不构成授权依据，只读审计仍可用。
- 授权到期后由 `ResourceGrantExpireJob`（每 5 分钟）与授予时的兜底逻辑把记录迁移为 EXPIRED 并释放唯一键 `activeKey`，否则「同一资源同一对象最多一条有效授权」会让到期记录挡住重新授权。到期与撤销同样释放唯一键。
- 隔离中的资源一律拒绝内容读取，处置人员也不例外：处置职责只看元数据，隔离不是读取私人内容的后门。隔离期间也不允许重新上传替换或删除该资源，避免原文件被销毁。
- 回滚不把附件还原成源站直链：直链会重新公开已关闭的私人内容。回滚脚本先把附件的定位、归属与绑定记录搬进 `feedback_attachment_archive_20261023` 再删业务表，重新部署本工单时搬回；旧版本因此无法展示这些附件的缩略图，业务数据本身没有丢失。
- 旧附件的 BLOCKED 记录不提供读取入口，即使资源方也无法直接查看；解除阻断只能通过人工核对后的单独登记。
- 反馈附件上传后必须随反馈提交，否则停留在 UPLOADED 状态且只有上传者本人可读。
- 雪花标识在本工单的接口上一律按十进制字符串传输（服务端 `Long` → 字符串，客户端不再 `Number()` 转换）：19 位雪花值超出 JavaScript 安全整数范围，转成 number 会丢精度，附件标识一旦按类型过滤就会静默丢失。
