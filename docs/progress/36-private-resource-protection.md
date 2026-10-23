# #36 私有资源管理与反馈保护

工单：[36](https://github.com/yuwen773/VisionSpace/issues/36)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26)。
分支：`codex/36-private-resource-protection`。本文只记录本次实际执行到的证据，未执行的部分单列在「人工关卡」。

## 改动结构

| 位置 | 作用 |
| --- | --- |
| `feedback_attachment` 表 | 反馈附件的可信记录：上传者、绑定反馈、平台、对象键、地址、状态（UPLOADED/BOUND/LEGACY_VERIFIED/BLOCKED）、旧地址摘要 |
| `feedback_attachment_archive_20261023` 表 | 回滚归档：回滚前把附件的定位、归属与绑定记录搬到这里，再删业务表；重新部署本工单时搬回。归档是「可更新、可消费」的一份快照，回滚时按业务表最新值覆盖，重新部署时以业务表为准搬回并清空 |
| `ResourceLockManager` | 资源行锁入口：按资源类型对业务行取 `SELECT ... FOR UPDATE`（图片看 `picture`，反馈附件看 `feedback_attachment`）。隔离、替换、删除共用这一把锁，锁由数据库提供，跨实例有效 |
| `PictureMapper#selectIdForUpdate` / `FeedbackAttachmentMapper#selectIdForUpdate` | 资源行锁的 SQL 出口：锁定读可见最新已提交状态，锁在事务提交或回滚时释放 |
| `resource_access_grant` 表 | 单资源限时只读授权：被授权人、授权主体、到期时间、状态（ACTIVE/REVOKED/EXPIRED）、撤销人与理由；`activeKey` 唯一约束表达「同一资源同一对象最多一条有效授权」 |
| `resource_isolation` 表 | 违规资源隔离与恢复：资源类型与标识、状态（QUARANTINED/RELEASED）、操作者、理由、恢复人与恢复理由；`activeKey` 唯一约束表达「同一资源最多一条有效隔离」 |
| `FeedbackAttachmentServiceImpl` | 登记上传、把附件绑定到反馈、按当前反馈权限读取内容、生成受控入口 `/feedback/attachment/{id}` |
| `FeedbackAccessPolicy` | 反馈可见性的唯一判定来源：创建者本人与具有反馈管理职责的平台管理员；控制器与附件服务共用，不再各写一份 |
| `FeedbackController` | 反馈详情按「创建者 + 有反馈管理职责的平台管理员」判定；附件内容每次请求按当前权限与隔离状态判定 |
| `ResourceAccessGrantServiceImpl` | 授予、撤销、按资源列出、按被授权人列出；`canGrant` 只认个人资源所有者与团队管理员身份（平台管理员独立持有的团队管理员身份照常生效） |
| `ResourceGrantExpireJob` | 每 5 分钟把到期授权迁移为 EXPIRED 并释放 `activeKey`：只改展示状态的话无法重新授权 |
| `ResourceIsolationServiceImpl` | 隔离、恢复、是否隔离、隔离中列表、处置历史；隔离时先取资源行锁再做锁定读复核，与替换、删除按同一顺序串行化 |
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
| 15.4 审计记录授权与撤销主体、范围、结果，不含内容或密钥，附迁移与回滚验证 | `resource_access_grant` 记录授权人、被授权人、到期、撤销人、撤销理由与时间；`ResourceGrantVO` 不含内容与地址；`PrivateResourceMigrationTest` 七个用例覆盖前向、幂等、旧附件登记、回滚守卫、两轮回滚往返（保留最新绑定与状态）、归档无法承载业务状态时阻断回滚，以及回滚后附件定位记录仍在归档表、重新部署时搬回 | 已覆盖（隔离库） |
| 16.1 独立操作权限校验目标、操作者与理由，后台只展示必要元数据 | `ResourceModerationController` 全部入口 `@AuthCheck(mustRole = MODERATOR_ROLE)`，理由必填、目标必须存在；`HttpResourceQuarantineTest#quarantineRequiresIndependentModerationPermission`、`#moderationViewsExposeMetadataOnly`（处置人员只有元数据权限，不能读取隔离中的内容） | 已覆盖 |
| 16.2 隔离后公开与私人访问都拒绝，保留内容、引用与可恢复状态 | `HttpResourceQuarantineTest#quarantinedPublicContentIsDeniedEverywhereButKept`、`#quarantinedPrivateContentIsDeniedIncludingExplicitGrants`（显式授权也不能绕过隔离）、`#quarantinedResourceCannotBeReplacedByReupload`（重新上传不能替换隔离资源、销毁旧对象）；并发下隔离生效后原内容不被销毁由 `HttpQuarantineRaceTest#replaceIsRejectedWhenQuarantineCommitsDuringTheUpload`、`#deleteIsRejectedWhenQuarantineCommitsAfterThePreCheck` 覆盖 | 已覆盖 |
| 16.3 恢复有明确权限和审计，不错误扩展访问范围 | `HttpResourceQuarantineTest#releaseRestoresOriginalAccessRulesWithoutWidening`、`#releaseRequiresActiveQuarantineAndReason` | 已覆盖 |
| 16.4 操作者、理由、目标、状态、结果可核对，直接实体查询不绕过隔离 | `resource_isolation` 记录目标/操作者/理由/状态/恢复信息；`/moderation/list` 与 `/moderation/history` 只读元数据；`HttpResourceQuarantineTest#quarantineRequiresIndependentModerationPermission` 覆盖非处置角色被拒 | 已覆盖（隔离库） |

## 第二轮评审返工（两项 P1）

### P1-1 回滚 / 重新部署丢失最新附件绑定

问题：回滚脚本用 `INSERT IGNORE ... SELECT` 写归档，同 id 的较新记录会被直接忽略；归档也从不消费。第二轮回滚时归档里仍是第一轮快照，重新部署搬回的是过期的绑定与状态，附件的定位与归属因此回退。

修复（`sql/migrations/2026-10-23-private-resource-protection-rollback.sql`、`2026-10-23-private-resource-protection.sql`）：

- 回滚归档改为「补齐 → 更新 → 校验」三步：先 `INSERT IGNORE` 补齐缺失行，再按 id 用业务表的最新绑定与状态 `UPDATE` 覆盖已有归档行，随后逐行比对归档与业务表（归属、反馈、平台、对象键、地址、大小、状态、旧地址摘要）。不一致时向守卫表写入违反 CHECK 约束的值让脚本失败——失败发生在删业务表**之前**，唯一有效记录不会被删掉。
- 重新部署改为「业务表优先 + 消费」：先删掉与业务表同 id 的归档行，再把剩余归档行 `INSERT` 回业务表，最后清空归档表。归档只是快照，权威永远是业务表的最新状态；搬回时若仍有冲突会直接报错而不是静默丢弃。
- 附件的定位（平台 + 对象键）、归属、绑定与状态在归档与搬回两侧都完整保留；地址不还原成源站直链，不重新开放私人直链。

回归（`PrivateResourceMigrationTest`）：

- `twoRollbackCyclesKeepTheLatestAttachmentBindingAndStatus`：前向 → 建未绑定附件 → 回滚 → 重新部署（还原未绑定状态）→ 修改绑定与状态 → 回滚（归档里是最新绑定与状态）→ 重新部署（还原最新绑定与状态，且归档被消费清空）。两轮 rollback→forward，中间真的改了绑定与状态。
- `rollbackIsBlockedWhenTheArchiveCannotHoldTheBusinessState`：归档与业务表在同一 `legacyUrlHash` 上冲突时回滚失败，业务表与业务行仍在，守卫表留在库里。

两条在修复前的脚本上都失败（归档里的反馈绑定为空导致断言失败；回滚未被阻断），修复后通过。重复执行幂等、未解除隔离时阻断回滚等既有覆盖保留。

### P1-2 隔离与替换 / 删除的竞态

问题：替换与删除只在事务前做一次普通查询，与隔离写入之间没有任何共享的并发约束。查询返回后隔离生效，替换与删除仍会销毁被隔离的原文并删掉对象。

修复：

- 新增 `manager/lock/ResourceLockManager`：隔离、替换、删除都先对资源行取 `SELECT ... FOR UPDATE`（数据库行锁，跨实例有效，事务提交才释放）。三者顺序固定为「先资源行锁、后隔离行」，因此不会形成锁环。
- `ResourceIsolationService#isQuarantinedForUpdate` 用锁定读复核隔离状态：锁定读看到的是最新已提交状态，不会读到事务开始时的旧快照。
- 替换路径的对象清理移入事务，与数据提交共享同一顺序；删除路径的对象清理仍在提交之后（行已不存在，不会再有隔离挂上来）。
- 隔离生效后，被替换 / 删除的资源保留内容、引用与可恢复状态，原对象不被删除，私人直链不重新开放。

回归（新增 `HttpQuarantineRaceTest`，用 latch / barrier 控制竞态，不依赖 sleep 的偶然时序）：

- `deleteIsRejectedWhenQuarantineCommitsAfterThePreCheck`：预检通过后拦下删除请求，提交隔离，再放行删除 → 断言返回 50001、图片行与 ACTIVE 附件仍在、地址未变、原对象的 DELETE 调用次数为 0。
- `replaceIsRejectedWhenQuarantineCommitsDuringTheUpload`：上传存储后拦下替换请求，提交隔离，再放行 → 断言返回 50001、原对象未被删除。

两条在修复前的代码上都失败（隔离后的内容仍被销毁 / 替换，返回 0），修复后通过。「请求开始前已隔离时直接拒绝」的既有回归保留。

顺带修正既有回归的失真：`HttpResourceQuarantineTest` 的团队空间所有者此前没有 `space_user` 成员行，请求在空间权限校验处就被拒，两条隔离回归实际没有走到隔离判定。补上成员行后，两条断言收紧为业务错误码 50001，确认拒绝来自隔离而不是权限。

## 本次实际执行的验证

2026-10-23，Windows + JDK 23 / Maven / Node / PowerShell 7，原生隔离 MySQL 8.0.26 + Redis 3.2.100（本机 Docker 不可用，因此不是容器实测）：

```powershell
pwsh.exe -NoLogo -NoProfile -File ./scripts/acceptance.ps1 `
  -MySqlBin '<MySQL 8.0.26 bin>' -RedisBin '<Redis-x64-3.2.100>'
```

结果见 `target/acceptance/summary.json`：九个结果分类全部 PASS（`runner-tests`、`secret-scan`、`isolation`、`backend`、`frontend-install`、`frontend-typecheck`、`frontend-tests`、`frontend-build`、`cleanup`），退出 0。

- 后端共 298 条测试，0 失败、0 错误、0 跳过。其中本工单 `HttpPictureGrantTest` 9 条、`HttpResourceQuarantineTest` 8 条、`HttpFeedbackProtectionTest` 9 条、`HttpPictureContentAuthTest` 11 条、`PrivateResourceMigrationTest` 7 条、`HttpQuarantineRaceTest` 2 条、`PictureContentAuthManagerTest` 10 条。
- 前端共 6 个测试文件 43 条（`api-custom/__tests__/resourceProtection.spec.ts` 17 条，`utils.spec.ts` 2 条），类型检查与生产构建均通过。
- 评审提出的 6 项缺陷与 1 项 P3 异味修复后完整重跑一轮，首轮即全绿，未再出现失败。
- 前几轮完整运行各出现 1 条失败，均已定位并修正，未放宽断言：
  1. `secret-scan` 命中本会话临时生成的本地验收配置（`.local-verify/openapi/acceptance.yml`），删除该目录后通过；
  2. `PrivateResourceMigrationTest#attachmentsAlreadyTrackedAreNotRegisteredAsLegacy` 先因插入时目标表尚未建立而报错（先执行前向脚本再建数据），修正后又暴露出 `SUBSTRING` 起始位与 `CAST` 截断告警，改为按字符串比较附件标识后通过。

第二轮评审返工后于同日 20:55 完整重跑一轮（同一命令）：九个分类仍全部 PASS，退出 0。实例身份：隔离 MySQL 8.0.26 监听 51394、Redis 3.2.100 监听 51395，库名 `vs_acceptance_ebc8f85454a44bbcb50a23a29e52a00a`；`summary.json` 记为 `infrastructure: native-explicit`、`productionAcceptance: NOT_RUN`。清理结果：MySQL 收到 SHUTDOWN 后正常关闭（`mysql-error.log` 有 `Shutdown complete`），Redis 进程已退出，51394 / 51395 / 6379 均不再监听；本会话早前单测用的临时 Redis 实例已一并结束。

两条新回归的「修复前会失败」是逐条验证过的：把生产代码回到评审 HEAD（`544c7e3`）后重跑，两条竞态回归分别拿到 `{"code":0,...}`（隔离后的内容仍被删除 / 替换），归档则因反馈绑定为空而断言失败；恢复修复后全部通过。

浏览器链路另跑一轮（同样用原生隔离 MySQL/Redis）：

```powershell
pwsh.exe -NoLogo -NoProfile -File ./scripts/browser-acceptance.ps1 `
  -MySqlBin '<MySQL 8.0.26 bin>' -RedisBin '<Redis-x64-3.2.100>' -SkipInstall
```

`isolation`、`backend`、`browser`、`cleanup` 全部 PASS，浏览器 9 条场景全通过。该套场景是 HTTPS/Secure Cookie/CSRF/SSE 同源链路，作为本工单的回归，不覆盖 #36 自身的交互。

## 人工关卡（未完成，不计入通过）

本轮用到的证据分四层，不互相冒充：**受控并发**（单进程内用 latch / barrier 控制的竞态）、**真实数据库**（原生隔离 MySQL 8.0.26 上的行锁与迁移脚本）、**双应用实例**（未做）、**真实对象存储**（未做）。

1. **生产迁移与回滚演练。** `PrivateResourceMigrationTest` 在隔离库的临时表名上验证脚本可执行、幂等、回滚守卫生效，两轮回滚往返保留最新绑定与状态，以及归档无法承载业务状态时阻断回滚；不等于生产库（含数据量、锁、备份恢复）演练通过。
2. **存量旧附件的人工核对。** 旧附件一律登记为 BLOCKED，需要人工按脱敏阻断清单核对归属后单独登记为 LEGACY_VERIFIED；本轮没有真实存量数据可核对。
3. **双应用实例验收。** 15.3 的「两个实例一致」依赖「每次请求查库判定」，本轮只在单进程内验证。第二轮返工引入的资源行锁由数据库提供，理论上跨实例有效，但本轮没有真的起两个应用实例同时打流量。
4. **受控并发不等于真实并发。** `HttpQuarantineRaceTest` 的两条竞态回归是单进程内用 latch / barrier 精确控制的时序，能稳定复现「预检通过后隔离生效」的窗口；真实多实例下的锁等待、连接池占用与超时行为没有观测。
5. **#36 场景的浏览器端 E2E。** 浏览器验收脚本覆盖的是 HTTPS/Secure Cookie/CSRF/SSE 同源链路，作为回归跑通；反馈附件受控渲染、授权面板与内容处置台没有真实浏览器交互证据。
6. **真实对象存储的直链失效。** 存量 `picture.url` 与旧附件地址在源站是否可匿名访问，需人工在对象存储侧收紧；本工单只保证应用层不再返回这些地址。竞态回归里的对象删除断言打在录制型存储上（`RecordingStorageConfiguration`），没有真的对 COS / OSS / MinIO 发过删除请求。

## 已知取舍

- 授权面板需要输入被授权用户 ID：平台没有面向普通用户的用户目录接口，开放搜索会扩大个人信息暴露面。
- 授权的授予与撤销资格完全由服务端 `canGrant` 给出，前端不再按角色自行推断：个人资源看所有者，团队资源看团队管理员身份。平台管理员若本人就是资源所有者，或独立持有该团队的团队管理员身份，管理入口照常出现；平台身份本身不构成授权依据，只读审计仍可用。
- 授权到期后由 `ResourceGrantExpireJob`（每 5 分钟）与授予时的兜底逻辑把记录迁移为 EXPIRED 并释放唯一键 `activeKey`，否则「同一资源同一对象最多一条有效授权」会让到期记录挡住重新授权。到期与撤销同样释放唯一键。
- 隔离中的资源一律拒绝内容读取，处置人员也不例外：处置职责只看元数据，隔离不是读取私人内容的后门。隔离期间也不允许重新上传替换或删除该资源，避免原文件被销毁。隔离、替换、删除共用同一把数据库行锁并按同一顺序取锁，因此隔离一旦生效，替换与删除一定会看到它并被拒；反过来替换先提交时，隔离会等到替换结束再落库，不存在「隔离生效但原文已被销毁」的窗口。
- 回滚不把附件还原成源站直链：直链会重新公开已关闭的私人内容。回滚脚本先把附件的定位、归属与绑定记录搬进 `feedback_attachment_archive_20261023` 再删业务表，重新部署本工单时搬回；旧版本因此无法展示这些附件的缩略图，业务数据本身没有丢失。归档是可更新、可消费的快照：回滚时按业务表最新值覆盖归档并逐行校验，重新部署时以业务表为准搬回并清空归档，因此多轮回滚不会把绑定与状态退回到第一轮的快照；归档无法承载业务状态时回滚直接失败并保留业务表。
- 旧附件的 BLOCKED 记录不提供读取入口，即使资源方也无法直接查看；解除阻断只能通过人工核对后的单独登记。
- 反馈附件上传后必须随反馈提交，否则停留在 UPLOADED 状态且只有上传者本人可读。
- 雪花标识在本工单的接口上一律按十进制字符串传输（服务端 `Long` → 字符串，客户端不再 `Number()` 转换）：19 位雪花值超出 JavaScript 安全整数范围，转成 number 会丢精度，附件标识一旦按类型过滤就会静默丢失。
