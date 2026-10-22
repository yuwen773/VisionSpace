# #34 图片归属与实际内容鉴权

工单：[34](https://github.com/yuwen773/VisionSpace/issues/34)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26)。
分支：`feat/34-picture-resource-auth`。本文只记录本次实际执行到的证据，未执行的部分单列在「人工关卡」。

## 改动结构

| 位置 | 作用 |
| --- | --- |
| `picture_file` 表（`sql/migrations/2026-10-22-picture-file.sql`） | 每个存储对象一条记录：所有者、空间、图片、用途、内容类型、平台、对象键、地址、大小、状态（TEMP/ACTIVE/DELETED/SUPERSEDED） |
| `PictureFileService` | 登记上传、绑定图片、替换、按图片/地址精确查找、按平台+对象键删除、用户已用字节数 |
| `PictureContentAuthManager` | 原图/缩略图/预览/下载共用的读取判定，每次请求重新判定，不缓存 |
| `PictureContentService` + `/picture/content/{id}/{kind}` | 浏览器使用的唯一内容入口；`Cache-Control: no-store`、`X-Content-Type-Options: nosniff`、下载时 `Content-Disposition: attachment` |
| `UploadQuotaManager` | Redis 固定窗口的每日上传次数 + 资源记录汇总的用户级字节额度；Redis 异常按失败处理（不放行） |
| `AgentImageUploadService` | Agent 临时上传登记资源记录并返回资源标识；删除只按资源标识与所有者；按 URL 删除仅在精确匹配本人记录时允许 |
| `PictureController` / `PictureServiceImpl` / `PictureRecommendServiceImpl` | 所有 `PictureVO`/`RecommendPictureVO` 产出统一填充受控入口，不再返回源站地址；管理入口返回无内容地址的元数据视图 |
| `FileUploadService.deleteFileByUrl` | 删除。该实现按 URL 解析路径并用前缀校验，是「仅匹配前缀即删除」的入口 |

## 验收对照

| 验收项 | 证据 | 状态 |
| --- | --- | --- |
| 11.1 资源标识/所有者/平台/对象键/临时状态，额度与速率可验证 | `HttpPictureContentAuthTest#uploadRecordsTrustedOwnershipAndStorageLocation` 校验上传后 `picture_file` 的所有者、平台、对象键、状态与缩略图记录；`UploadQuotaManager` 在存储调用前执行速率与额度检查，拒绝路径不产生存储调用 | 已覆盖 |
| 11.2 上传/删除需认证；跨用户标识、伪造 URL、错误域名或前缀不能删除 | `#agentUploadReturnsResourceIdAndDeleteIgnoresForgedUrls`（跨用户删除拒绝且零存储调用、`url + "-forged-suffix"` 拒绝、正确删除产生 DELETE 记录、重复删除无副作用）；`#legacyUrlDeleteOnlyWorksForExactOwnedRecord`（他人记录、纯前缀、未知地址全部拒绝） | 已覆盖 |
| 11.3 旧调用兼容仅在精确匹配时允许，不自动认领未知旧文件 | `PictureFileServiceImpl#findByExactUrl` 为等值查询；`#legacyUrlDeleteOnlyWorksForExactOwnedRecord` 与 `#legacyPictureWithoutResourceRecordIsKeptButNotGranted`（旧图片保留、不产生资源记录、视图不返回内容地址） | 已覆盖 |
| 11.4 客户端用资源标识完成上传与删除；失败/重试/重复删除副作用明确 | 前端 `api-custom/agent.ts` 改为提交 `{resourceId}`；`useAgentStream` 在发送失败时按 `resourceId` 清理；重复删除返回成功且不再调用存储 | 已覆盖（工程层） |
| 11.5 资源记录与定位接口可被图库与会话复用，附带迁移与回滚验证 | 图库（`PictureServiceImpl` 上传/读取/删除）与 Agent（`AgentImageUploadService`）共用 `PictureFileService`；`PictureFileMigrationTest` 在临时表名上执行前向脚本与回滚脚本，校验列、索引及「回滚只删除本工单新增表」 | 已覆盖（隔离库） |
| 12.1 图库上传记录平台与全部衍生内容，浏览器用授权入口而非长期直链 | `#uploadRecordsTrustedOwnershipAndStorageLocation`（原图 + 缩略图记录）；`PicturePreviewServiceImpl` 把预览对象也登记为 ACTIVE 记录；`#viewsExposeControlledEndpointsInsteadOfOriginAddresses` | 已覆盖 |
| 12.2 每次请求按当前角色判定；移除或降权成员后立即拒绝 | `#teamContentFollowsMembershipAndRevocationOnEveryRequest`（同一 Cookie 会话中删除成员后立即拒绝原图与缩略图）；`PictureContentAuthManagerTest` 覆盖私有空间、团队 viewer、降权、缺空间/缺图片 | 已覆盖（单实例进程） |
| 12.3 管理原始查询、列表、缓存及下载不返回旁路地址；公开已审核内容仍允许匿名 | `#adminEntryPointsDoNotReturnBypassAddresses`；`/list/page/vo/cache` 拒绝 `spaceId`；`#approvedPublicContentIsReadableAnonymouslyForEveryKind`；推荐流限定 `spaceId IS NULL` | 已覆盖 |
| 12.4 隔离存储验证源站绕过不可行；真实桶 ACL 与存量旧地址交由人工关卡 | `RecordingStorageConfiguration` 在存储边界记录每次 READ/DELETE（含平台与对象键），断言拒绝路径零调用；内容入口只按记录中的平台+对象键读取 | 部分覆盖，见「人工关卡」 |
| 12.5 端到端页面与 HTTP 覆盖各内容类型；旧资源保留但不自动授权 | 后端 12 条 HTTP 用例覆盖 original/thumbnail/preview/download；前端页面统一经 `contentSrc()` 渲染受控入口；旧资源用例见 11.3 | 已覆盖（HTTP + 页面渲染，无浏览器 E2E） |

## 本次实际执行的验证

2026-10-22，Windows + JDK 21 / Maven 3.9 / Node 22 / PowerShell 7，原生隔离 MySQL 8.0.26 + Redis 3.2.100（本机 Docker 不可用，因此不是容器实测）：

```powershell
pwsh.exe -NoLogo -NoProfile -File ./scripts/acceptance.ps1 `
  -MySqlBin '<MySQL 8.0.26 bin>' -RedisBin '<Redis-x64-3.2.100>'
```

结果见 `target/acceptance/summary.json`：六个结果分类场景、隔离服务、后端测试与打包、前端安装/类型检查/测试/构建、资源清理全部 PASS，退出 0。

- 后端共 147 条测试（新增 `HttpPictureContentAuthTest` 12 条、`PictureContentAuthManagerTest` 7 条、`PictureFileMigrationTest` 1 条）。
- 前端新增 `contentSrc` 单元用例 2 条，共 14 条。
- 首次完整运行曾出现 1 条失败：`legacyPictureWithoutResourceRecordIsKeptButNotGranted` 断言旧图片视图不返回内容地址，而实现仍按旧 URL 给出了入口。按 11.3 的口径修正实现（只有存在资源记录才给入口），未放宽断言。

## 人工关卡（未完成，不计入通过）

以下事项本轮没有执行，不能表述为已验证：

1. **真实桶 ACL 与直链失效。** 本轮存储为测试替身，没有连接 COS/OSS/MinIO。存量 `picture.url` 仍指向真实源站，源站地址在对象存储侧是否可匿名访问、是否需要收紧桶策略，必须由维护窗口核对后处理。
2. **存量旧直链的回收。** 迁移脚本不回填、不认领旧记录，旧图仍可按授权入口读取，但历史直链不会因为本工单自动失效。是否吊销、何时吊销由人工决定。
3. **双实例验收。** 12.2 的「两个实例均拒绝」本轮只在单进程内验证。结论依赖「每次请求查库判定、不做进程内缓存」，但未实际起两个实例交叉验证。
4. **生产迁移与回滚演练。** `PictureFileMigrationTest` 在隔离库的临时表名上验证脚本可执行且可回滚，不等于生产库（含数据量、锁、备份恢复）演练通过。
5. **浏览器端 E2E。** 前端只做了类型检查、单测与构建；页面渲染受控入口是代码层改动，没有真实浏览器截图或交互证据。

## 已知取舍

- 存量图片（无资源记录）在视图里 `url`/`thumbnailUrl`/`previewUrl` 为 `null`。这是「不自动认领」的直接后果：宁可前端显示占位，也不返回无法证明归属的地址。
- `picture_file` 的 `url` 列只作记录与旧客户端精确匹配使用，不参与删除定位；删除一律用记录中的 `platform + objectKey`。
- 预览生成仍是异步任务，失败不影响上传结果；预览记录缺失时内容入口回退到原图。
- Agent 临时图片（`purpose = agent-chat`）仍返回上传时的存储地址，没有改成受控入口：该地址要同时交给服务端的模型与工具读取，模型侧不会携带用户 Cookie。它的归属与删除已按资源记录收紧（跨用户与伪造 URL 均拒绝），但「浏览器不接触源站地址」这一条只对图库图片成立。
