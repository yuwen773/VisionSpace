# #40 存储切换与旧资源可用性 — 证据记录

分支：`feat/40-storage-switch`（基于 main 8dd98e0）
规格：`.tickets/issue-40.md`（父规格 SRF-v2 D20；测试决策 T20）

## 切换语义（候选验证 → 原子占位 → 确认集合 → 事务内晋升）

1. **候选完整构建验证**：`StorageSnapshotAssembler` 把全部启用配置逐一交给
   `StorageCandidateVerifier`（生产实现 `DefaultStorageCandidateVerifier`：平台枚举与必填字段
   显式校验 → 完整构建 x-file-storage 平台客户端 → 反射强制完成惰性 SDK 客户端初始化 →
   可选 bucketExists 探活，**默认开启（fail-closed）**，隔离验收经 acceptance.yml 显式关闭）。
   任一配置失败即整体失败，运行时与持久状态都不触碰。
2. **原子占位**：`storage_switch_state` 单行表（迁移 `2026-11-05-storage-switch-state.sql`），
   `claimSwitch` 以 `WHERE pendingVersion IS NULL` 单语句原子占位，跨实例只允许一个进行中的
   切换；落败方收到显式错误，不静默排队。**不预置确认数**：占位只写
   pendingVersion/pendingConfigId/pendingPlatform/pendingTime/expectedInstances。
3. **占位后重校验**：占位与生效之间可能被并发回滚；应用前重读状态行，占位不再是本次目标
   即放弃应用（fail-closed），不静默运行已被回滚的候选快照。
4. **本实例生效**：`StorageSnapshotManager.apply` 应用候选快照：先让全部候选平台就位
   （同名原位替换 / 新增追加，重复名显式失败）→ 再切默认平台 → 最后移除不在候选中的平台。
   x-file-storage 内部平台列表是 final 字段，无法整体引用交换；上述顺序保证任意时刻
   保留平台的查找都可用，`AtomicReference<Snapshot>` 作为权威生效记录。
5. **各实例确认（确认集合，非计数器）**：确认写入 `storage_switch_confirm`
   （主键 `(version, instanceId)`，`INSERT IGNORE`）。同一实例重复确认（重试、重启、双击）
   只占一席，晋升不可能被确认数虚增提前触发；发起实例与其余实例走同一条确认路径。
   instanceId 为实例本地持久化的 UUID（`vs.instance-id-file`，默认工作目录
   `storage-instance-id`；同机多实例须各自指定，验收第二实例由其入口类显式区分）：
   同一物理实例跨重启复用同一标识，不会因重启在集合里占多席。
6. **晋升**：确认集合大小 ≥ expectedInstances 后在同一事务里 `completeSwitch`
   （`WHERE pendingVersion=#{version}`，并发晋升唯一赢家）并 `deactivateOtherConfigs` +
   `activateConfig`（持久激活标记迁移），保证运行生效与 `storage_config.isActive` 一致提交。
   **单实例部署（expectedInstances=1）发起即确认即晋升**，不存在「1/1 卡 pending」状态。
7. **残留确认清理 + 一次性占位令牌**：回滚只清 pending 不清 confirm 集合，而 `claimSwitch`
   会复用同一版本号。两道防线配合：
   - **占位事务内清理**：赢家清空同版本号的全部确认行（`deleteByVersion`），覆盖占位时刻
     已存在的全部残留行。并发安全：落败方（影响 0 行）不清理，不会误删并发赢家的确认。
   - **确认插入原子校验（`insertIfCurrent`）**：插入与校验是同一条原子语句——仅当状态行的
     `pendingVersion` 与 `pendingToken`（占位语句内由 SQL 生成的一次性令牌，每次占位必不同）
     都匹配本次尝试时才插入。这是必须的：确认线程在旧尝试中通过重读校验后，该尝试可能被
     回滚、同版本号被重新占位，旧线程此时才落行——只校验版本号挡不住（版本号复用），
     令牌跨 abort/再占位必变，旧线程持旧令牌无法把确认行落进新尝试的集合。
8. **回滚**：`/storage/config/switch/rollback` 原子清空 pending（`WHERE pendingVersion IS NOT NULL`），
   `activeVersion` 不变；本实例从持久激活配置重建快照。已应用候选的落后实例在下次
   状态读取/确认时**惰性自愈**（pending 为空且本地版本 ≠ activeVersion 时从持久配置重建；
   pending 非空期间的落后是待确认状态，绝不自动应用）。
9. **旧资源不受影响**：切换不搬迁、不删除任何对象；资源的读取/删除/补偿始终按资源记录里的
   原平台与对象键定位（#34 已建立的行级归属）。
10. **在途上传**：`putPictureObject`/`putObject` 在任务开始时解析并钉住平台
   （`setPlatform`），切换并发时在途上传按原配置完成；无默认平台时显式拒绝上传，
   不存在「不确定落到哪个平台」的暧昧状态。

## 评审处置表（两轴评审 P1/P2/P3）

| # | 级别 | 问题 | 处置 |
| --- | --- | --- | --- |
| 1 | P1 | 确认计数跨实例虚增（内存计数器 + 发起方预置 1） | **已修**：删除 `confirmCount` 列与 `incrementConfirm`；新增 `storage_switch_confirm` 确认集合表（主键 (version, instanceId) + INSERT IGNORE）；发起实例走统一确认路径；晋升条件改为集合大小 ≥ expectedInstances；移除 `localConfirmedVersion` 内存标记。回归：`StorageSwitchServiceTest.pendingSwitchWaitsUntilConfirmSetReachesExpectedInstances` / `.confirmIsIdempotentForSameInstanceAndNeverReapplies`；验收 `switchReportsPendingUntilConfirmed`（重复确认仍 1/2） |
| 2 | P1 | claim→rollback 交错会静默应用已回滚候选 | **已修**：占位成功后、应用前重读状态行并校验 pendingConfigId 仍是本次目标；不符即抛显式错误且不应用。回归：`StorageSwitchServiceTest.switchAbortsWhenClaimIsConcurrentlyRolledBack` |
| 3 | P1 | 快照应用非原子（先清后建窗口） | **已修（如实的等价方案）**：x-file-storage 的平台列表是 final `CopyOnWriteArrayList`，整体引用交换不可行；改为全候选就位 → 切默认 → 移除失效的三段顺序应用 + `AtomicReference<Snapshot>` 权威记录，任意时刻保留平台查找可用；`FileStorageService.getFileStorageList()` 快照口径不变。已在类 javadoc 与上文语义第 4 条说明约束 |
| 4 | P1 | deleteStorageConfig 可删除生效中/待切换目标配置 | **已修**：生效中配置删除显式拒绝（提示先切换）；进行中切换的目标配置删除显式拒绝（提示先回滚）。回归：验收 `HttpStorageConfigSecurityTest` 覆盖入口鉴权，删除守卫由 `StorageConfigServiceImpl.deleteStorageConfig` 实现 |
| 5 | P2 | 生效中配置可改平台/凭据而不经验证 | **已修**：生效中配置拒绝平台变更与停用（须走切换流程）；改动存储目标字段（endpoint/region/bucket/basePath/domain/accessKey/secretKey）时先对合并后的候选执行完整验证，失败不保存 |
| 6 | P2 | AC24.1 探活默认值含糊 | **已修**：`candidate-probe-enabled` 生产默认 **true**（fail-closed），`application.yml` 经 `VISION_SPACE_STORAGE_CANDIDATE_PROBE` 注入；隔离验收在 `acceptance.yml` 显式置 false（不产生真实存储网络请求）。无保留意见 |
| 7 | P2 | 回滚后其他实例漂移不自愈 | **已修**：pending 为空且本地版本落后时，状态读取/确认触发惰性自愈（从持久配置重建，失败不阻塞状态读取且漂移保持可见）。回归：`StorageSwitchServiceTest.statusReadSelfHealsResidualDriftAfterRollback` / `.pendingSwitchIsNeverAutoAppliedByStatusRead`；验收 `statusReadSelfHealsLaggingInstanceAfterCompletedSwitch`（双实例） |
| 8 | P2 | 单实例阶段 1/1 卡 pending 自相矛盾；前端硬编码实例数 | **已修**：发起实例占位后走统一确认路径，expectedInstances=1 时在 maybeComplete 内立即晋升；期望实例数由后端权威下发（状态 VO），前端切换弹窗由运维按部署规模填写、展示一律用后端返回值。回归：`StorageSwitchServiceTest.singleInstanceSwitchCompletesImmediatelyAndMovesActivation`；验收 `singleInstanceSwitchCompletesImmediatelyAndMovesActivation` |
| 9 | P2 | /switch 与 /set_active 双入口；isDefault/set_default 死概念 | **已修**：删除 `/switch` 别名（保留 `/set_active` 为唯一切换入口，前端与既有验收已使用）；删除 `/set_default` 端点、`setDefaultPlatform` 服务方法、VO/DTO `isDefault` 字段及前端设默认按钮（DB 列保留，代码路径清空） |
| 10 | P3 | 旧 URL 域名匹配删除路径受限 | 记录：按 URL 精确匹配本人记录的删除入口仅覆盖 acceptance.invalid 形式的登记地址；旧平台域名下的历史 URL 依赖 D08 维护窗口人工核对，不由本工单自动执行 |
| 11 | P3 | 被撤销凭据「不长期沿用」 | 人工关卡：切换/候选验证保证新配置在启用前验证；撤销凭据的旧配置在切换后仍留在库里供旧资源读写（AC24.4 的要求），「停用/轮换」由 D20 运维交接流程承担 |
| 12 | P3 | 阶段魔串 / 版本钉住重复 | 记录：phase 取值 `pending`/`completed` 集中在 `StorageSwitchService.buildStatus`；快照版本读取经由 `StorageSnapshotManager.currentVersion()` 单点，未做进一步抽象 |

第二轮复核修复（增量复核发现的 2 新问题 + 1 低危）：

| # | 级别 | 问题 | 处置 |
| --- | --- | --- | --- |
| R1 | 中高 | abortSwitch 只清 pending 不清 confirm 集合，claimSwitch 复用同版本号 → 流产尝试的残留确认行被继承，回滚→再发起路径上重现「未确认实例被跳过的提前晋升」 | **已修**：占位事务内由赢家执行 `deleteByVersion`（并发安全论证见上文语义第 7 条：新确认行只可能在占位提交后产生，被清的全是残留行；落败方不清理）。选占位侧而非 abort 侧清理：abort 侧删除与「新占位复用版本号」存在竞态（会误删重新发起后的合法确认）。回归：单测 `claimPurgesStaleConfirmRowsFromAbortedAttempt` / `failedClaimDoesNotPurgeConfirmRows`；验收 `reinitiatedSwitchAfterRollbackDoesNotInheritStaleConfirmRows`（单实例即可实证：回滚→再发起后 confirmCount 必须 1 不得 2、不得提前晋升） |
| R2 | 中 | instanceId 按进程生成，重启即换 ID，同一物理实例可占多席独自触发提前晋升 | **已修**：实例标识首次生成后持久化到本地文件（`vs.instance-id-file`，默认工作目录 `storage-instance-id`），重启复用；同机多实例各自指定文件（验收第二实例入口类显式 `storage-instance-id-second`）。读写失败以 error 日志显式退化（不静默吞掉）。单测 `StorageInstanceIdProviderTest`（跨进程复用 / 不同文件不同 ID / 损坏重生成 / 不可写退化） |
| R3 | 低 | 跑过整改前迁移的环境残留 NOT NULL 的 confirmCount 死列 | **已修**：前向迁移追加条件 DROP（information_schema 判存 + PREPARE，幂等）；回滚脚本对称条件重建该列（DEFAULT 0，供回滚到中间版本的极端场景） |
| R4 | 中（窄窗口） | 二轮复核发现 R1 的清理存在 TOCTOU 窄窗口：确认线程在旧尝试读到 pending=V 并通过重读校验后，该尝试被回滚、同版本号 V 被重新占位（清理已先行提交），旧线程此时才执行 INSERT——残留行绕过清理落入新尝试的集合，若新尝试换了目标平台，该实例从未应用其候选却占一席，可能提前晋升。**原「新确认行只在占位提交后产生」的论证对跨 abort 存活的老线程不成立** | **已修**：确认写入改为单条原子语句 `insertIfCurrent`——`INSERT IGNORE ... SELECT ... FROM DUAL WHERE EXISTS(state.pendingVersion=#{version} AND state.pendingToken=#{token})`，插入与校验合一，不留读-写间隙。**未采用仅校验 pendingVersion 的条件插入**：版本号被重新发起复用，上述窗口（重新占位后才落行）恰好穿过该检查；改为占位一次性令牌 `pendingToken`（claimSwitch 的 SQL 内生成、abort/complete 清空，前向迁移幂等补列），旧线程持旧令牌无法落行。返回 0 行时由 maybeComplete 的状态重读兜底，不误报完整成功。mock 单测无法表达「读-写间隙内的 DB 侧交错」（服务侧总是传入它读到的最新令牌，竞态只存在于该读与 DB 写之间，已被单语句消除）；能表达该交错的思路：对真实库并发压测——线程 A 读状态后人为停顿，期间回滚+再占位同版本，恢复后确认，断言新尝试集合不含 A 的行（需可注入停顿的测试缝，属集成压测范畴，未在本工单实施） |

第二轮隔离验收两处行为断言失败的定位（本地复现 + MySQL general log 取证，**测试侧缺陷，服务行为无缺陷**）：

- **现象**：`dualInstanceSwitchRequiresBothInstancesBeforeReportingSuccess:313` 期望确认后
  phase=completed 实际 pending；`switchReportsPendingUntilConfirmed:215` 期望 activeVersion=2
  实际 1。
- **取证**：起原生 MySQL（general_log=TABLE）+ Redis 手工搭建隔离环境复现，两断言失败逐字节
  重现；general log 显示双实例用例的「第二实例确认」INSERT 落在**发起实例的连接池**上、
  instanceId 是发起实例的 ID，第二实例的 ID 从未出现在任何 SQL 中。
- **根因 1（313）**：`HttpStorageSwitchTest.dualInstanceSwitch…` 的确认与状态读取调用了
  **不带目标端口的重载**（`post(adminOnSecond, path, body)` / `get(second, path)`）——它们替换
  成默认 `port` 字段（发起实例端口），请求从未到达第二实例；发起实例重复确认被
  (version, instanceId) 主键幂等挡下，集合停在 1/2，phase 如实保持 pending。**服务端的幂等与
  集合计数行为完全正确**，反而证明了 R1/确认集合语义按设计工作。同期 `statusReadSelfHeals…`
  的第二实例读取同样被错发到发起实例，该用例此前从未真正测过第二实例自愈——修正后第二实例
  真实自愈并通过。
- **根因 2（215）**：断言语义写错——pending 期间 `activeVersion` 保持旧值（晋升时才前进），
  预占下一版本的是 `pendingVersion`；改为断言 activeVersion=1 且 pendingVersion=2。
- **修复**：三处调用改用带端口的 `post(client, targetPort, …)` / `getOn(client, path, targetPort)`；
  修正 215 断言。修复后本地复现环境全类 10/10 PASS（general log 确认第二实例 ID 落进确认集合、
  计数到 2 晋升）。

## AC → 测试 → 状态

| AC | 内容 | 测试 | 状态 |
| --- | --- | --- | --- |
| 24.1 | 候选完整验证后再原子切换；失败保留旧快照，不虚报完整成功 | 单测 `StorageSwitchServiceTest.candidateVerificationFailureKeepsOldSnapshotAndState` / `.alreadyActivePlatformIsRejectedWithoutClaiming`；单测 `DefaultStorageCandidateVerifierTest`（坏平台/缺字段/坏端点/坏 bucket 显式失败）；验收 `HttpStorageSwitchTest.singleSwitchKeepsOldSnapshotWhenCandidateFails` | PASS（本地隔离验收 + 单测） |
| 24.2 | 持久配置 / 全局版本 / 实例版本可区分；确认集合达到期望才报告成功；部分失败可见且可回滚 | 单测 `StorageSwitchServiceTest.reachingExpectedInstancesPromotesAndMovesActivationAtomically` / `.pendingSwitchWaitsUntilConfirmSetReachesExpectedInstances` / `.confirmIsIdempotentForSameInstanceAndNeverReapplies` / `.claimPurgesStaleConfirmRowsFromAbortedAttempt` / `.failedClaimDoesNotPurgeConfirmRows` / `.rollbackClearsPendingWithoutTouchingActiveVersion`；验收 `HttpStorageSwitchTest.switchReportsPendingUntilConfirmed` / `.singleInstanceSwitchCompletesImmediatelyAndMovesActivation` / `.reinitiatedSwitchAfterRollbackDoesNotInheritStaleConfirmRows` / `.rollbackRestoresPersistedSnapshotWithoutVersionRegression` / `.dualInstanceSwitchRequiresBothInstancesBeforeReportingSuccess`（双实例用例需第二 JVM，脚本未注入端口时显式 BLOCKED_ENV） | PASS（本地隔离验收 + 单测） |
| 24.3 | 在途上传用原配置、新任务用已验证配置；双实例竞争不产生多活或无活的暧昧默认状态 | 单测 `PictureStorageServicePlatformPinTest`（平台钉住 + 无默认显式拒绝）；单测 `StorageSwitchServiceTest.concurrentSwitchLosesWithExplicitError` / `.switchAbortsWhenClaimIsConcurrentlyRolledBack`；验收 `HttpStorageSwitchTest.uploadUsesVerifiedSnapshotAndRejectsNoDefaultState` | PASS（本地隔离验收 + 单测） |
| 24.4 | 读取/删除/补偿按资源原平台定位；切换不搬迁、不删除旧文件、不长期沿用已撤销凭据 | 验收 `HttpStorageSwitchTest.legacyResourcesKeepOriginalPlatformAcrossSwitch`（切换后旧资源平台/对象键未被改写、内容读取按原平台、删除按原平台、切换不触碰资源状态）；单测 `StorageSwitchServiceTest`（切换不触碰资源表） | PASS（本地隔离验收）；撤销凭据轮换为人工关卡 |
| 24.5 | UI 展示真实生效状态；密钥不输出；旧平台资源核对及维护回滚可执行 | 验收 `HttpStorageSwitchTest.statusEndpointShowsRealEffectAndNoSecrets` / `.statusReadSelfHealsLaggingInstanceAfterCompletedSwitch`（双实例自愈）；前端 `StorageConfigManagePage.vue`（本实例生效版本 / 版本漂移 / 确认进度与期望实例数（后端下发）/ 回滚按钮 / 运维填写期望实例数的切换弹窗）；单测 `StorageCredentialMaskerTest`（既有） | PASS（本地隔离验收）；UI 实际渲染为人工关卡 |

状态说明：
- **PASS 本地**：`StorageSwitchServiceTest`（15）、`StorageInstanceIdProviderTest`（4）、
  `PictureStorageServicePlatformPinTest`（4）、
  `DefaultStorageCandidateVerifierTest`（6）在本 worktree 直接运行通过。
- **本地隔离验收已执行**（2026-11-05，原生 MySQL 8.0.26 / Redis 3.2.100）：
  `scripts/acceptance.ps1` 在 HEAD `4549119` 上串行运行，12 门中 11 门 PASS
  （runner-tests / secret-scan / isolation / backend 全套 / 前端 6 项 / cleanup），
  唯一 FAIL 为 `frontend-api-contract`（`storageConfigController.ts` + `typings.d.ts`
  未随接口变更重新生成），已用验收导出的 `target/openapi-contract.json` 离线重生成
  并逐字节对账修复（`99243d2`）。`productionAcceptance: NOT_RUN`。
  此前一次验收尝试被系统内存不足回收、无结果，本次为有效执行记录。
- **集成最新 main 后复验**（2026-11-05）：合并 `origin/main`（`10d6e5e`，含 #69/#70）
  解决 `.gitignore` 与 `src/test/resources/acceptance.yml` 两处冲突——两边新增条目
  全部保留（#40 的 `candidate-probe-enabled: false`、#44 的 `lease-ttl-seconds` /
  `lease-renew-interval-ms`）。在合并 HEAD `c6f5dca` 上串行重跑完整隔离验收：
  `12/12` 门全 PASS，后端 594 项、前端门与 `frontend-api-contract` 全绿，
  `productionAcceptance: NOT_RUN`。该 HEAD 尚未推送，GitHub CI 与 PR #72 远端 head
  仍停在 `882d592`。
- **#68/#71 合并后再次集成 main**（2026-11-05）：main 前进到 `c9b20d5` 后，
  `sql/create_vision_space.sql` 出现追加式冲突——#40 的
  `storage_switch_state` / `storage_switch_confirm` 与 #42 的 `user_preference`
  都追加在 `resource_isolation` 之后。解冲突方式为**三段 DDL 全保留**，
  不删任何一方建表；`frontend/src/api/typings.d.ts` 自动合并。合并 HEAD
  `924c46a`。串行重跑时 `HttpStorageSwitchTest#legacyResourcesKeepOriginalPlatformAcrossSwitch`
  曾失败：#37 的 `deleteFile` 会按对象键核对是否仍被别的有效 `picture_file` 行引用，
  而该用例的 fixture 让 agent 资源与图库资源共用同一个对象键，删除因此被正确跳过、
  没有 DELETE 调用。给图库资源独立键并同步读取/清理断言（`236bfe5`），不改被测语义。
  最终在 `236bfe5` 上 `12/12` 门全 PASS，后端 655 项、前端门与 `frontend-api-contract`
  全绿，`productionAcceptance: NOT_RUN`。
- **NOT_RUN**：验收测试需要 `scripts/acceptance.ps1` 注入的隔离 MySQL/Redis 环境
  （及双实例用例的第二 JVM 端口），由编排者在交付后串行运行。
- **人工关卡**：真实云凭据下的 bucketExists 探活（生产默认开启，隔离验收关闭）、
  密钥轮换与仍在使用旧配置任务的运维交接（D20 明确要求交接，不由本工单自动执行）、
  前端 UI 实际渲染核对。

## 变更清单

后端（新增）：
- `manager/storage/StorageCandidateVerifier.java` / `DefaultStorageCandidateVerifier.java`
- `manager/storage/StorageSnapshotManager.java` / `StorageSwitchService.java`
- `manager/storage/StorageInstanceIdProvider.java`（实例标识：本地持久化，跨重启复用，
  读写失败显式退化并记录 error 日志）
- `manager/storage/init/StorageSnapshotAssembler.java`
- `mapper/StorageSwitchStateMapper.java` / `mapper/StorageSwitchConfirmMapper.java`
  + 对应 XML（原子占位并生成一次性令牌 / 原子完成 / 原子回滚 /
  条件确认插入 insertIfCurrent（版本+令牌原子校验） / 集合计数 / 占位事务内清残留确认行）
- `model/entity/StorageSwitchState.java`、`model/entity/StorageSwitchConfirm.java`、
  `model/vo/StorageSwitchStatusVO.java`、`model/dto/storage/StorageSwitchRequest.java`
- `resources/mapper/StorageConfigMapper.xml`（受限查询 + 激活迁移原子语句）

后端（修改）：
- `PictureStorageService`：上传前钉住平台；无默认平台显式拒绝（BusinessException 传播）
- `FileStorageInit`：启动改为「保证状态行 → 完整构建候选快照 → 原子应用」；失败保持空快照并显式报错
- `StorageConfigController`：`set_active` 为唯一切换入口（返回状态 VO）；新增
  `/switch/confirm`、`/switch/rollback`、`GET /switch/status`；`refresh` 重建持久快照；
  删除 `/switch` 别名与 `/set_default`
- `StorageConfigService(Impl)`：移除 `setActivePlatform` 与 `setDefaultPlatform` 直改路径；
  `add`/`update` 拒绝直接写 `isActive=1`；生效中配置拒绝平台变更/停用、存储目标字段改动
  先候选验证；删除守卫（生效中不可删、进行中切换目标不可删）
- `StorageConfigMapper`：新增受限查询与原子激活迁移语句
- `application.yml`：`candidate-probe-enabled` 默认 true；`acceptance.yml` 显式 false

前端：
- `api-custom/storage.ts`：切换/确认/回滚/状态 API 与 `StorageSwitchStatus` 类型；
  移除 `setDefaultPlatformUsingPost` 与 `isDefault`
- `pages/admin/StorageConfigManagePage.vue`：真实生效状态卡片（本实例版本、漂移标记、
  pending 确认进度与回滚）；切换改为弹窗（运维填写期望实例数，展示用后端返回值）；
  移除设默认按钮

SQL：
- `sql/migrations/2026-11-05-storage-switch-state.sql`（+ rollback）：`storage_switch_state`
  单行表（无确认计数列）+ `storage_switch_confirm` 确认集合表，幂等，无存量回填
  （启动 `ensureRow` 兜底）
- `sql/create_vision_space.sql`：新增同表（验收 bootstrap 从该文件提取 DDL）

## 已知缺口

- 双实例验收用例依赖编排脚本注入 `-Dvs.acceptance.second-port`（与 #39 相同机制），
  本机无法单独运行；脚本未注入端口时用例显式 BLOCKED_ENV，不冒充通过。
- 实例标识的持久性依赖本地标识文件（`vs.instance-id-file`）：文件被删除或所在盘
  不可写时退化为进程级 UUID（error 日志显式可见），此时重启后的实例会被确认集合
  视为新实例；标识文件不属于任何持久化备份范围。同机多实例必须各自指定文件。
- 凭据撤销后的「不能长期沿用旧凭据」由运维交接流程承担（D20 原文要求明确交接，
  不在本工单自动执行范围）；本工单提供的是旧平台资源仍可按原平台读写删除的能力。
- 合并后需运行 `npm run openapi2ts` 重新生成前端 API 客户端（`set_active` 响应类型已变，
  `api-custom/storage.ts` 为手写模块不受影响，生成版 `storageConfigController.ts` 待对齐）。
