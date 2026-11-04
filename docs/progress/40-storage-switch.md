# #40 存储切换与旧资源可用性 — 证据记录

分支：`feat/40-storage-switch`（基于 main 8dd98e0）
规格：`.tickets/issue-40.md`（父规格 SRF-v2 D20；测试决策 T20）

## 切换语义（候选验证 → 原子切换 → 双实例确认）

1. **候选完整构建验证**：`StorageSnapshotAssembler` 把全部启用配置逐一交给
   `StorageCandidateVerifier`（生产实现 `DefaultStorageCandidateVerifier`：平台枚举与必填字段
   显式校验 → 完整构建 x-file-storage 平台客户端 → 反射强制完成惰性 SDK 客户端初始化 →
   可选 bucketExists 探活，默认关闭）。任一配置失败即整体失败，运行时与持久状态都不触碰。
2. **原子占位**：`storage_switch_state` 单行表（迁移 `2026-11-05-storage-switch-state.sql`），
   `claimSwitch` 以 `WHERE pendingVersion IS NULL` 单语句原子占位，跨实例只允许一个进行中的
   切换；落败方收到显式错误，不静默排队。
3. **本实例生效**：`StorageSnapshotManager.apply` 原子应用候选快照（同名平台原位替换 →
   追加新增 → 移除失效），不做「先清空列表再重建」。
4. **各实例确认**：`/storage/config/switch/confirm` 原子累加 `confirmCount`；本实例重复确认
   不重复累加（内存领取标记 + 先领额度再应用，应用失败可重试）。
5. **晋升**：确认数达到 `expectedInstances` 后在同一事务里 `completeSwitch`（版本晋升）并
   `deactivateOtherConfigs` + `activateConfig`（持久激活标记迁移），保证运行生效与
   `storage_config.isActive` 一致提交。
6. **回滚**：`/storage/config/switch/rollback` 清空 pending，`activeVersion` 不变；本实例从
   持久激活配置重建快照。部分生效的其他实例呈现版本漂移（状态接口可见 `localSynced=false`），
   由管理员逐实例执行 `/storage/config/refresh` 对齐。
7. **旧资源不受影响**：切换不搬迁、不删除任何对象；资源的读取/删除/补偿始终按资源记录里的
   原平台与对象键定位（#34 已建立的行级归属）。
8. **在途上传**：`putPictureObject`/`putObject` 在任务开始时解析并钉住平台
   （`setPlatform`），切换并发时在途上传按原配置完成；无默认平台时显式拒绝上传，
   不存在「不确定落到哪个平台」的暧昧状态。

## AC → 测试 → 状态

| AC | 内容 | 测试 | 状态 |
| --- | --- | --- | --- |
| 24.1 | 候选完整验证后再原子切换；失败保留旧快照，不虚报完整成功 | 单测 `StorageSwitchServiceTest.candidateVerificationFailureKeepsOldSnapshotAndState` / `.alreadyActivePlatformIsRejectedWithoutClaiming`；单测 `DefaultStorageCandidateVerifierTest`（坏平台/缺字段/坏端点/坏 bucket 显式失败）；验收 `HttpStorageSwitchTest.singleSwitchKeepsOldSnapshotWhenCandidateFails` | NOT_RUN in isolation（单测 PASS 本地） |
| 24.2 | 持久配置 / 全局版本 / 实例版本可区分；两实例确认才报告成功；部分失败可见且可回滚 | 单测 `StorageSwitchServiceTest.reachingExpectedInstancesPromotesAndMovesActivationAtomically` / `.promotionIsHeldUntilEveryExpectedInstanceConfirms` / `.rollbackClearsPendingWithoutTouchingActiveVersion`；验收 `HttpStorageSwitchTest.switchReportsPendingUntilConfirmed` / `.rollbackRestoresPersistedSnapshotWithoutVersionRegression` / `.dualInstanceSwitchRequiresBothInstancesBeforeReportingSuccess`（双实例用例需第二 JVM，脚本未注入端口时显式 BLOCKED_ENV） | NOT_RUN in isolation（单测 PASS 本地） |
| 24.3 | 在途上传用原配置、新任务用已验证配置；双实例竞争不产生多活或无活的暧昧默认状态 | 单测 `PictureStorageServicePlatformPinTest`（平台钉住 + 无默认显式拒绝）；单测 `StorageSwitchServiceTest.concurrentSwitchLosesWithExplicitError`（占位唯一赢家）；验收 `HttpStorageSwitchTest.uploadUsesVerifiedSnapshotAndRejectsNoDefaultState` | NOT_RUN in isolation（单测 PASS 本地） |
| 24.4 | 读取/删除/补偿按资源原平台定位；切换不搬迁、不删除旧文件、不长期沿用已撤销凭据 | 验收 `HttpStorageSwitchTest.legacyResourcesKeepOriginalPlatformAcrossSwitch`（切换后旧资源平台/对象键未被改写、切换过程零存储调用、删除按原平台）；单测 `StorageSwitchServiceTest`（切换不触碰资源表） | NOT_RUN in isolation |
| 24.5 | UI 展示真实生效状态；密钥不输出；旧平台资源核对及维护回滚可执行 | 验收 `HttpStorageSwitchTest.statusEndpointShowsRealEffectAndNoSecrets`；前端 `StorageConfigManagePage.vue`（本实例生效版本 / 版本漂移 / 确认进度 / 回滚按钮）；单测 `StorageCredentialMaskerTest`（既有） | NOT_RUN in isolation；UI 为人工关卡 |

状态说明：
- **PASS 本地**：`StorageSwitchServiceTest`（10）、`PictureStorageServicePlatformPinTest`（4）、
  `DefaultStorageCandidateVerifierTest`（6）在本 worktree 直接运行通过。
- **NOT_RUN in isolation**：验收测试需要 `scripts/acceptance.ps1` 注入的隔离 MySQL/Redis 环境
  （及双实例用例的第二 JVM 端口），由编排者在交付后串行运行。
- **人工关卡**：真实云凭据下的 bucketExists 探活（`VISION_SPACE_STORAGE_CANDIDATE_PROBE=true`）、
  密钥轮换与仍在使用旧配置任务的运维交接（D20 明确要求交接，不由本工单自动执行）、
  前端 UI 实际渲染核对。

## 变更清单

后端（新增）：
- `manager/storage/StorageCandidateVerifier.java` / `DefaultStorageCandidateVerifier.java`
- `manager/storage/StorageSnapshotManager.java` / `StorageSwitchService.java`
- `manager/storage/init/StorageSnapshotAssembler.java`
- `mapper/StorageSwitchStateMapper.java` + `resources/mapper/StorageSwitchStateMapper.xml`
- `model/entity/StorageSwitchState.java`、`model/vo/StorageSwitchStatusVO.java`、
  `model/dto/storage/StorageSwitchRequest.java`
- `resources/mapper/StorageConfigMapper.xml`（受限查询 + 激活迁移原子语句）

后端（修改）：
- `PictureStorageService`：上传前钉住平台；无默认平台显式拒绝（BusinessException 传播）
- `FileStorageInit`：启动改为「保证状态行 → 完整构建候选快照 → 原子应用」；失败保持空快照并显式报错
- `StorageConfigController`：`set_active` 改为切换语义（返回状态 VO）；新增
  `/switch`、`/switch/confirm`、`/switch/rollback`、`GET /switch/status`；`refresh` 重建持久快照
- `StorageConfigService(Impl)`：移除 `setActivePlatform` 直改路径；`add`/`update` 拒绝直接写 `isActive=1`
  （激活只能走切换流程），移除「先 deactivateAll 再 save」的先清后写模式
- `StorageConfigMapper`：新增受限查询与原子激活迁移语句

前端：
- `api-custom/storage.ts`：切换/确认/回滚/状态 API 与 `StorageSwitchStatus` 类型
- `pages/admin/StorageConfigManagePage.vue`：真实生效状态卡片（本实例版本、漂移标记、
  pending 确认进度与回滚）、切换改为带确认弹窗且失败提示「已保留当前生效平台」

SQL：
- `sql/migrations/2026-11-05-storage-switch-state.sql`（+ rollback）：`storage_switch_state` 单行表，
  幂等，无存量回填（启动 `ensureRow` 兜底）
- `sql/create_vision_space.sql`：新增同表（验收 bootstrap 从该文件提取 DDL）

## 已知缺口

- 双实例验收用例依赖编排脚本注入 `-Dvs.acceptance.second-port`（与 #39 相同机制），
  本机无法单独运行。
- 凭据撤销后的「不能长期沿用旧凭据」由运维交接流程承担（D20 原文要求明确交接，
  不在本工单自动执行范围）；本工单提供的是旧平台资源仍可按原平台读写删除的能力。
- 合并后需运行 `npm run openapi2ts` 重新生成前端 API 客户端（`set_active` 响应类型已变，
  `api-custom/storage.ts` 为手写模块不受影响，生成版 `storageConfigController.ts` 待对齐）。
