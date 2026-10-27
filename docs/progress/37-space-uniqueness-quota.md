# #37 空间唯一性、配额与文件一致性

工单：[37](https://github.com/yuwen773/VisionSpace/issues/37)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26)。
分支：`worktree-37-space-quota-file-consistency`。本文只记录本次实际执行到的证据，未执行的部分单列在「人工关卡」。

## 改动结构

| 位置 | 作用 |
| --- | --- |
| `space.activeKey` + `uk_space_active_key` | 有效空间唯一占位（`userId:spaceType`）。删除时置空，唯一索引只约束非空值，因此「删除后再创建」与反复重建都不会被历史墓碑挡住 |
| `SpaceMapper#reservePictureSlot` / `#applyPictureSizeDelta` / `#releasePictureSlot` | 配额判定与写入是同一条条件更新：超出上限影响 0 行；释放用 `GREATEST(..., 0)` 收敛，存量偏差不会把计数压成负数，也不会卡住删除 |
| `SpaceMapper#softDeleteAndReleaseActiveKey` | 逻辑删除与释放唯一占位在同一条语句完成，条件带 `isDelete = 0`，重复删除影响 0 行 |
| `SpaceServiceImpl#addSpace` | 去掉进程内 `synchronized(userId.intern())` 与「先查再插」；唯一性交给数据库唯一索引，`DuplicateKeyException` 转成明确的业务错误 50001 |
| `SpaceServiceImpl#deleteSpace` | 新增：逻辑删除 + 释放占位；`SpaceController#/space/delete` 改走它 |
| `PictureMapper#selectForUpdate` | 替换路径在行锁内读旧图大小，差额基于最新值而不是请求开始前读到的值 |
| `PictureFileMapper#selectByPictureAndStatusForUpdate` | 替换时挑旧记录用锁定读，两个实例同时替换不会把同一批旧对象清理两遍 |
| `PictureFileMapper#selectLiveByPlatformAndKey` / `#countLivePictureBinding` / `#countLiveReferences` | 删除对象前核对引用：同一对象键仍被有效记录引用时不删，避免让别的记录指向不存在的对象 |
| `storage_cleanup_task` 表 | 数据库与对象存储没有共同事务：删除未确认的对象落成可重试、可观察的任务 |
| `StorageCleanupService` / `StorageCleanupServiceImpl` | 登记、重试、阻断。重试前**重新核对引用**，不沿用登记时的判断；缺少平台标识的存量对象直接转为等待人工处理，不按地址猜测删除 |
| `StorageCleanupJob` | 每 15 分钟（首次延迟 10 分钟）重试到期任务，单批 100 条，单条失败不影响其余；退避 5 分钟起翻倍、上限 24 小时 |
| `PictureServiceImpl#uploadPicture` | 进入存储前不再做「读-判断」配额校验；改为事务内条件更新，超限时后续写入随事务一起回滚 |
| `PictureServiceImpl#deletePicture` | 只对属于空间的图片扣额度；公开图库不扣任何空间 |
| `PictureServiceImpl#discardUploadedObjects` / `#discardRegisteredFiles` / `#cleanupFiles` | 回收失败不再只写一行日志：登记清理任务，原图与缩略图各自独立重试 |
| `PictureServiceImpl#clearPictureFile` | 存量对象只有地址、没有平台与对象键：登记为等待人工核对，不进入自动重试 |
| `PictureFileServiceImpl#deleteFile` | 已被替换/已删除的记录直接收敛状态、不重复调用存储；同一对象键仍被别的有效记录引用时跳过删除 |
| `AgentAttachmentService#removeObject` | 复用资源记录的删除入口（含引用核对与幂等），删除未确认时同时登记统一清理任务 |
| `Space#activeKey` 上的 `@JsonIgnore` | `/space/get` 与 `/space/list/page` 直接返回实体：不屏蔽会把内部占位写进对外契约（验收的契约对账已抓到） |

## 迁移与回滚

| 脚本 | 作用 |
| --- | --- |
| `sql/migrations/2026-10-25-space-uniqueness-quota.sql` | 前向：加 `activeKey` 与唯一索引、建 `storage_cleanup_task`。条件 DDL（`information_schema` + `PREPARE`）可重复执行；**不回填**存量 `activeKey` |
| `sql/migrations/2026-10-25-space-uniqueness-quota-backfill.sql` | 回填 `activeKey`。存在同一 `(userId, spaceType)` 的多条有效空间时，在改动任何数据**之前**用 CHECK 守卫失败，不自动选择/合并/删除 |
| `sql/migrations/2026-10-25-space-uniqueness-quota-rollback.sql` | 回滚：删索引、列、表。仍有未收敛清理任务（status 0/2）时用 CHECK 守卫阻断——删表等于丢掉「还有对象没删掉」这份事实 |
| `sql/migrations/2026-10-25-space-uniqueness-quota-blocklist.sql` | 只读清单：重复有效空间（含有效图片数与成员数）、配额偏差与负数、未收敛清理任务 |

## 验收对照

| 验收项 | 证据 | 状态 |
| --- | --- | --- |
| 17.1 新增只加一次；重复替换十次数量不变、按新旧大小差额调整；公开删除不扣空空间 | `HttpSpaceQuotaConsistencyTest#addingCountsOnceAndReplacingTenTimesKeepsTheCount`（同一张图连替十次，`totalCount` 恒为 1、`totalSize` 回到首次值）、`#publicDeletionDoesNotTouchAnySpaceQuota`（`spaceId` 为空的公开图删除后，无关空间仍是 5/500）、`#deletionNeverDrivesTheQuotaNegative` | 已覆盖 |
| 17.2 两实例并发不能突破额度或出现负数；重复请求与重试不重复计额 | 单进程受控并发：`#concurrentUploadsCannotExceedTheSpaceQuota`（只剩 1 个名额，2 个并发恰好 1 成功且不留图片记录）、`#concurrentUploadsCannotExceedTheSizeLimit`（30 字节只够一个）、`#repeatedReplacementsDoNotDoubleCountTheSize`（同一图并发替换 5 次，数量恒为 1、大小不累加、原图与缩略图各只有一条有效记录）；真实双实例：`HttpSpaceDualInstanceTest#concurrentSpaceCreationAcrossTwoRealInstancesSucceedsExactlyOnce`（实例 A 与真实第二 JVM 并发创建同类空间，恰好 1 成功，落败侧拿到 50001） | 已覆盖（额度并发为单进程受控并发 + 真实数据库条件更新；空间唯一性为真实双实例） |
| 17.3 存储写失败不产生成功图片；存储成功而数据库失败时新对象可追踪补偿，旧图在真正替换成功前可用 | `#storageWriteFailureProducesNoPicture`（存储抛错后：无图片、无配额消耗、无资源记录）、`#databaseFailureAfterStorageLeavesATraceableCompensationTask`（存储成功后条件更新影响 0 行 → 事务回滚、无图片、无有效资源记录、存储收到 DELETE、无残留任务）、`#failedCompensationKeepsARetryableTaskAndTheOldPictureStaysUsable`（替换失败且回收也失败：旧图地址与有效记录不变，新对象留下带平台与对象键的可重试任务） | 已覆盖 |
| 17.4 补偿失败可重试并有可观察状态，不误删其他引用或原平台对象；核验数据库与对象最终状态 | `#failedCompensationOfAnUnboundUploadKeepsATraceableTask`（数据库里没有任何记录时，对象仍能被任务定位：平台 + 对象键 + 下次重试时间）、`#cleanupTaskIsRetriedAndNeverDeletesAReReferencedObject`（任务指向仍被有效记录引用的对象 → 重试阻断、DELETE 调用次数为 0、任务转为 status=2 等待人工）、`SpaceQuotaMigrationTest#rollbackIsBlockedWhileCleanupTasksAreUnresolvedThenSucceeds`（未收敛任务阻断回滚） | 已覆盖（录制型存储；真实云存储见「人工关卡」） |
| 17.5 存量配额通过维护清单核对，不静默覆盖冲突，附迁移及安全回滚步骤 | `SpaceQuotaMigrationTest#forwardAddsTheColumnAndIndexAndIsIdempotent`、`#backfillRefusesToChooseBetweenDuplicateLiveSpaces`（有重复时守卫失败，两条都没有写入占位、一行没删）、`#backfillFillsOnlyLiveSpacesAndNeverTouchesDeletedOnes`、`#blocklistReportsDuplicateLiveSpacesWithoutChangingData`、`#rollbackIsBlockedWhileCleanupTasksAreUnresolvedThenSucceeds`（阻断时不删表不删列；收敛后回滚成功、守卫表清干净、空间数据保留；再前向可恢复结构） | 已覆盖（隔离库；生产维护窗口见「人工关卡」） |
| 22.1 两实例同时创建同类型空间只成功一次，失败返回明确业务结果 | `HttpSpaceDualInstanceTest#concurrentSpaceCreationAcrossTwoRealInstancesSucceedsExactlyOnce`（真实第二 JVM，端口 53916，共享同一隔离 MySQL）；单进程侧 `HttpSpaceQuotaConsistencyTest#onlyOneConcurrentSpaceCreationSucceeds`（4 个并发，恰好 1 成功，落败侧均为 50001） | 已覆盖 |
| 22.2 团队空间与创建者管理员成员记录一致提交，失败不残留无主空间或成员 | 成员记录与空间在同一 `transactionTemplate` 内提交；`#teamSpaceAndCreatorMembershipCommitTogether`、`#onlyOneConcurrentSpaceCreationSucceeds`（并发落败侧不留成员行）、`#deletingAndRecreatingTheSameSpaceWorksTwice`（两轮后无无主空间） | 已覆盖 |
| 22.3 删除再创建至少两轮均正确，约束不因历史墓碑阻止后续操作 | `#deletingAndRecreatingTheSameSpaceWorksTwice`（两轮「创建 → 重复创建被拒 → 删除」）、`HttpSpaceDualInstanceTest#deletionOnOneInstanceAllowsRecreationOnTheOther`（在 A 创建、在 B 删除、在 B 重建，且是新记录） | 已覆盖 |
| 22.4 存量重复有效空间先出清单并阻断相应迁移，不自动选择/合并/删除；附维护窗口迁移及回滚验证 | `#blocklistReportsDuplicateLiveSpacesWithoutChangingData`（清单报出重复组且不写占位）、`#backfillRefusesToChooseBetweenDuplicateLiveSpaces`（回填在改数据前失败，重复行一条不少）、`#rollbackIsBlockedWhileCleanupTasksAreUnresolvedThenSucceeds` | 已覆盖（隔离库） |

## 本次实际执行的验证

2026-10-27，Windows + JDK 23 / Maven / Node / PowerShell 7，原生隔离 MySQL 8.0.26 + Redis 3.2.100（本机 Docker 不可用，因此不是容器实测）：

```powershell
pwsh.exe -NoLogo -NoProfile -File ./scripts/acceptance.ps1 `
  -MySqlBin '<MySQL 8.0.26 bin>' -RedisBin '<Redis-x64-3.2.100>'
```

最终一轮（`head` 8dd98e0）十二个结果分类全部 PASS：

```json
{
  "runner-tests": "PASS", "secret-scan": "PASS", "isolation": "PASS",
  "backend": "PASS", "frontend-install": "PASS", "frontend-lint": "PASS",
  "frontend-format": "PASS", "frontend-typecheck": "PASS",
  "frontend-tests": "PASS", "frontend-build": "PASS",
  "frontend-api-contract": "PASS", "cleanup": "PASS"
}
```

`infrastructure: native-explicit`、`productionAcceptance: NOT_RUN`；`summary.json` 在 `target/acceptance/`。

- 后端共 542 条测试，0 失败、0 错误、0 跳过。本工单新增：`HttpSpaceQuotaConsistencyTest` 14 条、`SpaceQuotaMigrationTest` 5 条、`HttpSpaceDualInstanceTest` 2 条。
- 双实例证据：`second-instance-out.log` 有 `VipSecondInstance ready on port 53916`，`second-instance.port` 记录 53916；清理阶段脚本写 stop 文件，日志有 `VipSecondInstance stopping`，无残留进程。
- 前端未改业务代码；lint / format / type-check / 单测 / 构建 / 契约对账全部通过。

### 过程中真实抓到并修掉的问题

1. **内部占位泄漏进对外契约。** 首轮 `frontend-api-contract` FAIL：`Space` 实体被 `/space/get`、`/space/list/page` 直接返回，新增的 `activeKey` 出现在生成的 `typings.d.ts` 里。加 `@JsonIgnore` 后契约恢复一致。这是契约对账关卡真实拦下的缺陷，不是产物过期。
2. **测试自身四处断言写错**（不是生产缺陷，逐条核对后修正，未放宽断言）：
   - 回收路径把资源记录收敛成 `DELETED`，不是物理删除——断言改为只数仍占用存储的记录（`status IN (0,1)`）。
   - 一次上传登记原图与缩略图**两条**记录，断言「只有一条有效记录」本身写错——改为按 `kind` 各断言一条。
   - 替换夹具用了同尺寸文件，差额为 0 本来就不该被大小上限拒绝——改为让新图更大，差额为正才走到拒绝分支。
   - 清理任务表是全局的，别的用例（Agent 附件删除未确认）也会留下任务——断言与清理改为按空间对象键过滤，并按对象键清理，避免用例间互相污染。

## 人工关卡（未完成，不计入通过）

1. **生产库迁移与回滚演练。** `SpaceQuotaMigrationTest` 在隔离库的临时表名上验证脚本可执行、幂等、回填守卫与回滚守卫生效；不等于生产库（数据量、锁、备份恢复）演练通过。
2. **存量数据处置。** 存量重复有效空间与配额偏差没有真实数据可核对，`-blocklist.sql` 只做了「能报出重复组且不改数据」的验证；真正选择保留哪一个空间、何时回填、如何校正计数，仍需维护窗口内人工按清单执行。
3. **真实对象存储。** 所有对象删除断言打在录制型存储（`RecordingStorageConfiguration`）上，没有对 COS / OSS / MinIO 真正发过删除请求；「不误删原平台对象」在真实云厂商侧的语义（删除成功判定、最终一致性）未观测。
4. **长时间退避重试。** `StorageCleanupJob` 的 15 分钟调度与 5 分钟→24 小时退避没有真实跑满一个周期；`retryOne` 的引用复核、阻断、退避写入是用例直接驱动的。
5. **真实高并发。** 额度并发是单进程内用 latch 控制的并发，打到的是真实 MySQL 条件更新；多实例下的锁等待、连接池占用与超时行为没有观测。空间唯一性的双实例用例是真实两个 JVM，但并发度只有 2。

## 已知取舍

- **唯一性交给数据库，不用进程内锁。** 原来的 `synchronized(userId.intern())` 只在单 JVM 内生效，两个实例同时创建时都会看到「不存在」。改用 `activeKey` 唯一索引后，冲突方拿到 `DuplicateKeyException` 并转成 50001，事务整体回滚，不会留下半条记录。
- **不能用 `(userId, spaceType)` 唯一索引。** 逻辑删除只把 `isDelete` 改成 1，行还在表里；第二次删除或重建必然冲突。`activeKey` 删除时置空、唯一索引只约束非空值，才能同时满足「同类只一个」和「删了能重建」。
- **前向迁移不回填 `activeKey`。** 回填会让存量重复数据在唯一索引上冲突，也可能替人工「选中」某个空间。回填单独成脚本，遇到重复先失败。
- **配额不做「读-判断-写」。** 读到的计数随时会被另一实例改掉。判定与写入合并成一条条件更新，超限影响 0 行。
- **释放用 `GREATEST(..., 0)`。** 存量偏差不能让删除失败，也不能让计数变负；偏差由维护清单核对后人工校正。
- **清理任务删除前重新核对引用。** 登记时的判断可能已经过期：资源记录可能被重新绑定、同一对象键可能被多条记录引用。核对不通过就阻断成 status=2，宁可留下待处理任务，也不按猜测删除。
- **只有地址的存量对象不进入自动重试。** 按地址猜测删除会误删别人的对象，直接登记为等待人工核对。
- **`storage_cleanup_task` 的唯一键是 `platform|kind|objectKey`，进入终态后置空。** 同一个对象不会被排队删除多次，也不会在第一次失败后多出一批重复重试；收敛之后同一个键可以重新排队。
- **附件删除与图库清理共用一份对象事实。** 谁先确认删除，另一个在重试前的引用核对里都会看到并停下。
