# #46 维护窗口迁移与安全回滚演练

工单：[46](https://github.com/yuwen773/VisionSpace/issues/46)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26)。
分支：`worktree-46-maintenance-window-drill`，集成基线 `8a92d22`（`origin/main`：#32/#35/#36/#37/#38/#39/#40/#41/#42 全部合并）。

本文只记录本次实际执行到的证据，未执行的部分单列在「人工关卡」，不计入通过。
本票是编排验收票：**不补做缺失业务功能、不修改真实生产数据、不重写各领域已交付的迁移脚本**。

## 改动结构

| 文件 | 作用 |
| --- | --- |
| `sql/migrations/maintenance-window.manifest.json` | 编排事实来源：窗口七阶段、磁盘上全部 30 份脚本的有序步骤、领域归属、停写/可重放/回滚配对、九个盘点维度、六类不重新开放风险不变量、八个精确人工批准点 |
| `sql/migrations/2026-11-05-maintenance-window-blocklist.sql` | 本票唯一新增 SQL：把归属未知、重复有效空间、无法安全恢复状态、未收敛补偿与「不得复活」的安全状态汇总成一份跨领域**脱敏**阻断清单，并输出阻断计数（只读） |
| `MaintenanceWindowManifestTest.java` | 纯 JUnit（不依赖隔离库）：清单完整性、脚本零遗漏零虚构、回滚配对、只读清单不得写数据、盘点维度与人工批准点覆盖 |
| `MaintenanceWindowDrillTest.java` | 隔离验收演练（真实隔离 MySQL 8.0.26 + Redis 3.2.100）：备份台账 → 停写闸门 → 重放现成脚本 → 只读盘点 → 对账 → 故障回滚 → 重新部署 → 受控旧地址/缓存检查 |
| `scripts/drill-isolated-run.py` | 单类复跑入口：全新临时数据目录、随机端口、专用库/账号、实例标记，退出时只关闭自己启动的进程；不读取已有 MySQL/Redis 配置，不占用默认端口 |
| `scripts/drill-secret-scan.py` | 用 `scripts/secret-scan.ps1` 的同一套六条规则复核本票新增文件；不依赖 PowerShell |

各业务票的 29 份 `sql/migrations/*.sql` 与 7 份 `*MigrationTest.java` **原样保留**，本票只在 `*_drill_*` 临时表上调用它们。

## 为什么这样编排

`scripts/acceptance.ps1` 从 `sql/create_vision_space.sql` 引导的隔离库已经包含 26 张**迁移后**的表；
各业务票的迁移测试也都在改名临时表上重放脚本。因此本票不再对真实表重跑前向建表脚本，
而是把窗口里真正会改变数据、且带安全守卫的步骤编排起来：
`#37` 回填、`#35` 存量清单回填/回滚、`#36` 私有资源回滚、`#39` VIP 回滚、
`#42` 偏好归档/恢复，以及四份只读盘点与一份新的跨领域阻断清单。
前向结构脚本的「已交付且可执行」由各业务票自己的 `MigrationTest` 证明，本票用清单把它们全部登记在案。

## 验收对照

| 验收项 | 证据 | 状态 |
| --- | --- | --- |
| 34.1 按备份、暂停写入、迁移、核对、恢复编排各领域现成步骤，不补做业务功能、不改真实生产数据 | `MaintenanceWindowDrillTest#windowRunsInManifestOrderAndOnlyComposesDeliveredScripts`：清单七阶段顺序、30 份脚本全部登记、备份台账、停写闸门拒绝/放行、5 份只读盘点按编排执行、12 份回滚脚本全部纳入演练、前后对账一致 | 已覆盖（隔离库） |
| 34.2 盘点图片与附件归属、原平台、配额、有效空间、事件、会员权益、执行状态与偏好，记录前后核对结果 | 清单 `inventoryDimensions` 九个维度；`#inventoryCoversEveryNamedDimensionAndIsDesensitized` 逐维度验证表存在、清单只读、前后行数不变、输出脱敏 | 已覆盖（隔离库） |
| 34.3 归属未知、重复有效空间或无法安全恢复状态形成脱敏清单并阻断，不自动认领/合并/删除/续跑 | 新增 `-maintenance-window-blocklist.sql`；`#unresolvedConflictsProduceADesensitizedListAndBlockTheMigration`（重复空间在改数据前被 CHECK 守卫阻断、零占位/零删除、四类冲突进脱敏清单、零副作用）；`#blockedRollbackKeepsMaintenanceStateInsteadOfContinuing`（未收敛任务/未解除隔离双双阻断回滚，保持停写） | 已覆盖（隔离库） |
| 34.4 演练故障回滚，证明不会复活暴露码、已撤销权限、旧秘密或已关闭私人直链；无法安全回退时保持维护状态 | `#rollbackRehearsalNeverRevivesExposedCodesRevokedGrantsOrPrivateLinks`（回滚前后风险快照逐字段相等、已用/作废码不回可用、`user.vipCode` 恒 NULL、已撤授权不复活、已清除偏好仍为空、归档保留、重新部署不复活）；清单 `riskReopenInvariants` 六条 | 已覆盖（隔离库） |
| 34.5 在受控存储与缓存环境验证旧地址失效策略，并提供真实环境精确检查清单及人工批准点 | `#controlledStorageDoesNotExposeLegacyDirectUrls`（未知归属存量图 `contentUrl` 返回 null；有归属记录只给受控入口、不返回源站地址）；`#controlledCacheInvalidationRemovesStaleEntriesWithoutServingPrivateContent`（隔离 Redis 写入 `recommend:*` 旧键 → `invalidateRecommendCache()` 后零残留、撤权后 `contentUrl` 仍为 null、不因残留缓存复活私人直链）；清单 `manualApprovalPoints` M1–M8 与下方「真实环境精确检查清单」 | 已覆盖受控存储与受控缓存（隔离库）；真实桶 ACL/CDN/公网/凭据轮换/多实例 Caffeine 为人工关卡（M4/M5/M6），不计入通过 |

## 本次实际执行的验证

2026-11-05，Windows + JDK 23（`pom.xml` source/target 21）+ Maven 3.9.9，
原生隔离 MySQL 8.0.26 + Redis 3.2.100（本机 Docker 不可用，因此不是容器实测）。

| 检查 | 结果 |
| --- | --- |
| `MaintenanceWindowManifestTest`（`mvn -Dtest=MaintenanceWindowManifestTest test`，无需隔离库） | PASS：7/7，0 失败/错误/跳过 |
| `MaintenanceWindowDrillTest`（`scripts/drill-isolated-run.py`，真实隔离 MySQL/Redis + 完整 Spring 上下文） | PASS：7/7，0 失败/错误/跳过；最终轮见下方完整门禁记录 |
| 本票用例 + 全部 7 个既有迁移用例同轮串行 | PASS：44/44，0 失败/错误/跳过（无表名/守卫/归档互相污染） |
| 完整后端门禁 `mvn verify`（`--full`，真实第二 JVM 共享同一隔离库） | 最终轮 PASS：669/669，0 失败/错误/跳过，打包成功，用时 2m59s |
| 秘密扫描（按 `scripts/secret-scan.ps1` 六条规则复核本票新增 6 个文件） | PASS：0 命中；`scripts/drill-secret-scan.py` 为等价的跨平台复核入口 |
| 隔离身份 | 每轮随机 nonce、随机端口（>10000）、专用库 `vs_acceptance_<nonce>`、专用账号；连接前只读核对 `@@datadir` 与本轮目录一致；MySQL/Redis 实例标记均校验 |
| 清理 | 每轮 MySQL 收到 `SHUTDOWN`，`mysql-error.log` 记录 `Shutdown complete`；第二实例收到 stop 文件自行退出；`wmic` 核对无 `visionspace-drill-*` 关联残留 mysqld 与 `VipSecondInstanceMain` 进程；临时目录用后清理 |
| 复跑入口 | `python -I scripts/drill-isolated-run.py --mysql-bin '<MySQL bin>' --redis-bin '<Redis bin>' --test MaintenanceWindowDrillTest`；完整后端用 `--full` |

红→绿过程如实保留：首轮 6 条中 3 条失败（清单引用临时归档表、重复插入快照偏好行、阻断汇总子查询未改名），
第二轮 1 条失败（回滚后偏好表暂不存在导致风险快照查询报错），
第三轮 1 条失败（存量事件清单引用 `claimExpireAt` 而快照缺列）；
评审修复轮另有 3 条失败（脱敏明细查询无夹具数据、停写闸门把演练脚本一并拒绝、
阻断汇总明细未改名），均通过补夹具、把真实执行统一收口到闸门并显式模拟人工批准修复；
每次都修正测试夹具/断言而非放宽安全断言，最终 7/7 PASS。
这些不是既有回归失败。

`productionAcceptance: NOT_RUN`。

## 真实环境精确检查清单（人工批准点，本票未执行）

以下 M1–M8 与 `maintenance-window.manifest.json` 的 `manualApprovalPoints` 一致。
每一项都必须留下**运行证据**，不能以「脚本已准备好」「文档已写明」「隔离库通过」代替。
任一阻断项未清零或任一批准点未签字，编排保持维护/受限状态，不自动续跑、不恢复写入。

| 编号 | 精确检查项 | 留痕 | 批准角色 |
| --- | --- | --- | --- |
| M1 | 在独立实例上还原本轮备份，逐表行数与校验和一致 | 备份文件哈希、还原后行数对照表、还原时间与操作者 | 运维负责人 |
| M2 | 全部应用实例、定时任务、队列消费者与其他写入方已停止；以只读账号验证无新写入 | 实例清单、进程/任务停止记录、停写窗口起止时间 | 运维负责人 |
| M3 | 归属未知、重复有效空间、配额偏差、事件积压、无法安全恢复状态逐条人工判定；阻断项清零 | 脱敏清单留档、逐条处置结论、`blockingCount = 0` 核对结果 | 数据负责人 |
| M4 | 桶 ACL、CDN/回源策略、对象匿名可达性；对原图、缩略图、预览、下载、附件逐类直接请求旧地址均应拒绝；不能只删数据库地址 | 每类内容 3 条旧地址的匿名直接请求响应、桶策略变更记录、CDN 刷新记录 | 安全负责人 |
| M5 | Redis 与本地 Caffeine 中推荐/会话/内容缓存键逐一核对失效，撤权后下一次请求仍拒绝 | 缓存键清单、失效前后响应对照、双实例一致性记录 | 运维负责人 |
| M6 | 真实凭据已轮换、历史访问日志已核查、网络边界已收紧、运行时 `vipCode.json` 副本**整体删除**（不是置空） | 轮换记录、日志核查结论、旧副本删除清单 | 安全负责人 |
| M7 | 仅在备份可恢复、阻断项为零、旧地址已失效、已暴露兑换码与已撤权限不会复活时批准回滚；否则保持维护/受限状态 | 六类不重新开放风险核对结果、批准时间、回滚责任人 | 发布负责人 |
| M8 | 全部实例使用支持新结构的版本、双实例读取一致、健康检查通过后，才恢复写入 | 实例版本清单、双实例读取对账、健康检查响应 | 发布负责人 |

### 旧地址失效在受控环境验证了什么、没验证什么

`RecordingStorageConfiguration` 是录制型存储替身，`contentUrl` 对无资源记录的存量图返回 null，
有归属记录只给 `/picture/content/{id}/{kind}` 受控入口；撤权后的下一次请求按当前角色重新判定。
`ExternalCallRecorder` 在 ORIGINAL / THUMBNAIL / PREVIEW 的拒绝路径与解析受控入口时零调用，
证明 `READ` / `READ-LEGACY` / `DELETE` 未被触发（零未授权副作用）；
隔离 Redis 里 `recommend:*` 旧键经 `invalidateRecommendCache()` 后零残留，残留缓存不会让私人直链复活。

它**没有**验证：真实对象桶是否可匿名访问、CDN 是否缓存了旧响应、历史直链在公网是否仍可达、
已经下载到用户设备的副本、浏览器缓存。这些必须由 M4/M5 的真实环境响应证据闭环（父规格 D08、
ADR 0003）。

## 人工关卡（未完成，不计入通过）

1. **真实生产库迁移与回滚演练。** 本票在隔离库的 `*_drill_*` 临时表上编排；数据量、锁、备份恢复、
   双实例停写协调、长周期重试均未在生产规模上观测。各业务票进度文档里的「生产迁移与回滚演练」仍保持 NOT_RUN。
2. **存量数据处置。** 归属不明条目、重复有效空间、配额偏差只有脱敏清单与阻断断言，没有真实存量数据供人工逐条判定（M3）。
3. **真实桶 ACL / CDN / 旧直链失效。** 见 M4；受控替身不能替代对象存储侧证据。
4. **真实凭据轮换与已暴露兑换码处置。** 见 M6；本票不轮换任何凭据、不删除任何运行时文件、不重写 Git 历史。
5. **发布放行。** 父规格 D23：全部必选工单、完整自动回归、仓库门禁与人工运维关卡都完成才可放行；
   本票不修改远程治理配置，不视为部署授权。

## 评审修复后的最终补记

两轴评审针对「已暂存但落后于工作区」的 diff 给出结论；本票按结论修了以下问题，
并把工作区重新暂存，使评审对象与交付对象一致：

- 停写闸门原来只在一个抛异常分支被调用，真实 `runDelivered` 绕过闸门：现在所有迁移/回滚脚本统一经
  `runStepUnderGate`，阻断场景显式模拟 M2/M7 批准后执行，守卫失败再落回停写，`assertTrue(writesPaused)`
  不再是恒真断言。
- 新增跨领域清单不再输出原始 `sessionId` / `userId`；明细结果集增加 12 位摘要前缀断言与
  `ExternalCallRecorder` 零调用断言，而不是只检查脚本文本。
- 旧地址验证补上 PREVIEW，并用录制器证明 READ / READ-LEGACY / DELETE 零调用；34.4 补上
  sha-256 摘要、已关闭私人直链前后均 `contentUrl == null` 的断言。
- 清单计数从硬编码 25/5/12 改为从 manifest 派生；`assertNotNull(path(...))` 恒真断言改成
  `isMissingNode()`；重复的守卫/存储过程名单合并为单一常量。
- 新增 `controlledCacheInvalidationRemovesStaleEntriesWithoutServingPrivateContent`（第 7 条演练用例）
  与 manifest 的 `controlledCacheCheck`，把 34.5 的「受控缓存环境」落到真实隔离 Redis 执行。
- `CONTEXT.md` 术语在非引用性文案里统一为「VIP 权益 / 已暴露兑换码」；验收项 34.2/34.4 保留工单原文。

评审后同轮复跑：本票 + 7 个既有迁移用例 **44/44 PASS**；最终完整门禁见下方。

## 已知取舍

- **清单是唯一编排事实来源。** 新增/删除迁移脚本若没有同步更新 `maintenance-window.manifest.json`，
  `MaintenanceWindowManifestTest` 会立即失败，避免某一张票被悄悄漏掉。
- **只读清单不能变成新的访问入口。** 新增跨领域清单只输出内部 id、状态短码与 SHA2 摘要前 12 位；
  「不得复活」一节只给计数。脚本本身不含任何 URL、对象键、码值或凭据字段名，测试对此有静态断言。
- **守卫表留在库里是预期状态。** `#36`/`#37`/`#42` 的 CHECK 守卫阻断时留空表供人工核对，
  演练断言它存在，而不是把阻断当成失败后清理掉。
- **回滚不恢复风险，也不丢业务数据。** `#42` 回滚先归档再删表、重新前向再原样搬回；
  `#39` 回滚只补恒为 NULL 的兼容列；`#36` 回滚在仍有未解除隔离时直接失败。
- **不重复建设第二套验收框架。** 沿用 `@IsolatedSpringBootTest`、语句切分/单一连接执行、
  录制型存储替身与各业务票既有约定；`scripts/drill-isolated-run.py` 只是单类复跑的隔离环境入口，
  不替代 `scripts/acceptance.ps1` 的完整门禁。
