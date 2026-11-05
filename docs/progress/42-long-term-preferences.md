# #42 用户可控制的持久长期偏好

工单：[42](https://github.com/yuwen773/VisionSpace/issues/42)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26)。
基线：`8dd98e0`（origin/main）。分支：`feat/42-long-term-preferences`。

本文只记录本次实际执行到的证据；没有执行的部分单列在「未验证与 NOT_RUN 项」，不计入通过。

## 偏好存取契约

| 概念 | 存储 | 契约 |
| --- | --- | --- |
| 偏好持久化 | `user_preference` 表，每用户一行（`uk_userId`） | 跨重启与双实例读取一致：所有读路径直接查库，进程内不缓存偏好内容 |
| 显式设置 | `explicitContent`（JSON） | 用户在界面直接编辑；白名单键 `liked_styles` / `disliked_styles` / `liked_colors` / `notes`，白名单外键不保存；列表去重限长、notes 截断 |
| 自动学习产物 | `learnedContent`（JSON） | 反馈路径的关键词推断；学习永远不改写 `explicitContent` |
| 反馈历史 | `feedbackHistory`（JSON） | 条数上限 `vision-space.agent.maxFeedbackHistorySize`（默认 100），防无界增长 |
| 自动学习开关 | `autoLearn`，默认 `0`（关闭） | 关闭时 `saveFeedback` 直接返回：不积累历史、不做推断——自动学习只在明确开启后发生 |
| 偏好版本 | `preferenceVersion`，单调递增 | 学习写入按它 CAS：条件更新不命中影响 0 行 |
| 数据纪元 | `dataEpoch` | 用户每一次控制操作（显式修改/清除/开关）都递增。清除或关闭与在途学习交错时，旧任务持有的 `(version, epoch)` 不再命中；即使任务 CAS 失败后重读，纪元不同照样终止——旧学习任务不能把已清除的数据重放成一次新的写入 |
| 学习放弃可观察 | `learnError` 列 | 学习写入连续版本冲突被放弃时写入短码 `LEARN_GAVE_UP_CAS_CONFLICT`（不含反馈正文）；学习成功或用户的任何控制操作都清空。与 #41 `agent_run_state.backupError` 同一纪律，不静默吞掉。已知权衡：放弃的反馈不自动重试，用户下一次反馈正常学习 |
| 加载给模型 | `loadForModel` 合并视图 + `renderPreferenceBlock` 注入 | 推断内容打底，显式键覆盖同名键——显式设置优先于模型推断；反馈历史不进入模型上下文。偏好片段在 `ImageAgent#buildUserMessage` 附加到发给模型的消息（不写入聊天历史），无偏好不注入；读取失败时对话不中断（fail-open），但记 warn 日志 |
| 显式修改语义 | 整节替换 | `POST /agent/preference/update` 的 explicit 完整覆盖显式设置——未携带的白名单键被清掉（学习产物与反馈历史不受影响）；客户端先 GET 再整体提交（前端即此实现）。白名单外键被忽略 |
| 回滚与重新部署 | `-rollback.sql` 把偏好行搬进 `user_preference_rollback_archive` 后删业务表，不随应用降级丢数据；下一次前向迁移在业务表为空时把归档原样搬回（显式/学习/历史、开关、版本、纪元、墓碑行保持不变），归档表随后清理。业务表已有新写入时守卫阻断，归档留库供人工核对 | 回滚只动本票的表；恢复期间应用停写（维护窗口） |

清除的实现是「内容置空 + 版本与纪元递增」，**不删除行**：行是挡住在途学习写回的闸门；
删行会让在途学习任务随后 `insertIfAbsent` 重建出行，看似数据复活。清除不影响
`autoLearn` 开关本身（清除数据 ≠ 关闭学习），开关开着时清除后的新反馈按新规则重新积累。

存量数据迁移的诚实声明：旧实现把偏好放在进程内 `MemoryStore`
（`AgentStoreConfig#memoryStore`，无磁盘形态），实例停止即丢失，不存在可恢复的存量数据。
迁移脚本不做任何回填，也不把进程内旧值虚构为已迁移；升级后从空偏好开始。

## 改动结构

- `sql/migrations/2026-11-05-user-preference.sql` + `-rollback.sql`（前向 `CREATE TABLE IF NOT EXISTS` 且可从回滚归档恢复；回滚先把偏好行搬进 `user_preference_rollback_archive` 再删业务表，幂等，守卫阻断不完整归档/在线新写入）；`sql/create_vision_space.sql` 同步
- `src/test/java/com/yuwen/visionspace/acceptance/UserPreferenceMigrationTest.java`：在隔离 MySQL 的临时表上执行两份脚本，断言前向幂等、回滚保留数据、重新部署原样搬回、已清除内容不复活、其他表不受影响、守卫阻断在线新写入
- `model/entity/UserPreference.java`、`mapper/UserPreferenceMapper.java`（全部语句带 `userId` 归属条件；学习写入双重 CAS）
- `agent/service/UserPreferenceService.java` 重写为 DB 持久化（原 MemoryStore 实现删除）；`agent/hook/UserPreferenceLoaderHook.java` 改为委托服务并渲染提示词片段（原进程内负缓存删除——负缓存跨实例会掩盖他人刚写入的偏好）；`ImageAgent#buildUserMessage` 把偏好片段注入发给模型的消息（chat / stream / invokeAndGetOutput 三个入口，历史仍存原始消息）
- `controller/AgentPreferenceController.java`：`GET /agent/preference`、`POST /agent/preference/update|clear|auto-learn`，身份只取 `userService.getLoginUser`，接口不接收 `userId` 参数
- 前端：契约生成客户端 `api/agentPreferenceController.ts` + `typings.d.ts` + `index.ts`（由 `target/openapi-contract.json` 离线生成并逐字节对账，见下）、`components/user-center/PreferenceSection.vue`、`UserCenterPage.vue` 新增「AI 偏好」菜单
- `ImageAgent` 反馈路径改传 `Long userId`（不再字符串化）

## 逐项验收

| 验收项 | 证据 | 状态 |
| --- | --- | --- |
| 27.1 偏好持久保存并按可信身份隔离，其他用户及默认无权管理员不能读写 | `HttpAgentPreferenceTest#unauthenticatedRequestsAreRejected`、`#otherUsersAndAdminsCanOnlySeeTheirOwnPreferenceRow`（真实 HTTP + 隔离 MySQL）；`UserPreferenceMapper` 所有语句带 `userId` 条件；控制器不接收 `userId` 参数 | PASS（隔离验收） |
| 27.2 UI 和接口打通查看、显式修改、清除与开关；自动学习仅在明确开启后发生 | 接口：`AgentPreferenceController` 四个入口；`HttpAgentPreferenceTest#defaultViewHasAutoLearnOffAndEmptyContent`、`#explicitUpdateTogglesAndViewReflectIt`、`#clearWipesContentButKeepsTheRowAsTombstone`；UI：`PreferenceSection.vue`（查看/编辑/清除确认/开关）；单测：`UserPreferenceServiceTest#saveFeedback_autoLearnOff_skipsLearningAndHistory` | PASS（单测 + 隔离验收） |
| 27.3 显式设置优先于模型推断，关闭或清除与在途旧任务交错时数据不被复活 | 单测：`UserPreferenceServiceTest#loadForModel_explicitOverridesLearnedOnSameKey`、`#saveFeedback_learningWritesOnlyLearnedColumns_neverExplicit`、`#saveFeedback_casConflictAfterClear_abortsAndNeverRewrites`、`#saveFeedback_casConflictAfterDisable_abortsAndNeverRewrites`、`#saveFeedback_casConflictFromConcurrentLearning_retriesWithFreshVersion`、`#saveFeedback_repeatedCasConflictWithSameEpoch_givesUpObservably`；注入渲染：`UserPreferenceLoaderHookTest` 3 条（无偏好不注入、显式+学习条目渲染、读取失败 fail-open）；SQL 层守卫：`HttpAgentPreferenceTest#inFlightLearningCannotRewriteAfterClear`、`#inFlightLearningCannotRewriteEvenIfLearningIsReenabled` | PASS（单测 + 隔离验收） |
| 27.4 重启及双实例读取一致，存量可迁移信息保留，无法恢复的进程内旧值不虚构为已迁移 | `HttpAgentPreferenceTest#preferenceReadsGoToTheSharedStoreWithoutProcessLocalCaching`（mapper 直读共享库 + 新会话重读，无进程内偏好缓存）；真实双实例：`#preferenceWrittenOnOneInstanceIsVisibleOnTheOther`（A 写 B 读、清除后 B 读到空；验收脚本注入 `-Dvs.acceptance.second-port`，第二 JVM 真实执行）；持久化结构（唯一键 + 全部读路径查库）使一致性成为结构性质；存量迁移的诚实声明见上文 | PASS（隔离验收，含真实第二实例） |
| 27.5 提供兼容迁移和安全回滚，自动测试不调用真实模型 | `2026-11-05-user-preference.sql`（`IF NOT EXISTS` 幂等 + 从回滚归档恢复）+ `-rollback.sql`（先归档偏好行再删业务表，守卫阻断不完整归档/在线新写入）；`UserPreferenceMigrationTest` 在隔离 MySQL 临时表上逐项验证前向幂等、回滚保留数据、重新部署原样搬回、已清除内容不复活、其他表不受影响、守卫阻断；偏好控制与学习路径全部不经 LLM：单测直接测 `UserPreferenceService`（Mockito），隔离测试打 HTTP 与 mapper，零模型调用 | PASS（迁移脚本只在验收临时库执行，未在真实库运行） |

## 隔离验收（编排者串行执行）

实现代理按编排约定不运行 `scripts/acceptance.ps1`（并发会产生假失败）。定稿后，编排者
对同一 HEAD `9a4c091` 串行运行隔离验收三轮，逐轮收敛：

| 轮次 | 结果 | 处置 |
| --- | --- | --- |
| 1 | backend FAIL：`explicitUpdateTogglesAndViewReflectIt` 把 Jackson `ArrayNode.toString()` 与 Java `List.toString()` 比对；`inFlightLearningCannotRewriteAfterClear` 对清除后的 NULL 列调 `readTree(null)` | 两处均为测试断言缺陷（服务端语义正确），`b336d8f` 修正断言并补两条空内容路径回归单测 |
| 2 | backend PASS（547 测试），frontend-api-contract FAIL：手写 `agentPreference.ts` 与契约生成的客户端不一致 | `5abc017` 用验收导出的 `target/openapi-contract.json` 离线重生成客户端、切换组件导入、删除手写文件 |
| 3 | **12/12 门全 PASS** | 放行 |

第 3 轮 `target/acceptance/summary.json`（head `9a4c091`）：

```
runner-tests PASS | secret-scan PASS | isolation PASS | backend PASS（549 测试）
frontend-install PASS | frontend-lint PASS | frontend-format PASS
frontend-typecheck PASS | frontend-tests PASS | frontend-build PASS
frontend-api-contract PASS | cleanup PASS
productionAcceptance: NOT_RUN
```

原生 MySQL 8.0.26 / Redis 3.2.100，命令：
`pwsh -File scripts/acceptance.ps1 -MySqlBin 'D:\Work\Backend\MySQL-8.0.26\mysql-8.0.26-winx64\bin' -RedisBin 'D:\Work\Backend\Redis-x64-3.2.100'`。
GitHub `acceptance` check 亦在 PR #71 head 上 success（2026-11-04 18:00 UTC）。

## 本次实际执行的验证

2026-11-05，Windows 11 + JDK（`--release 21`）+ Maven：

```bash
mvn -q test -Dtest='UserPreferenceServiceTest,UserPreferenceLoaderHookTest'   # 16 + 3 条全部通过
mvn -q test-compile                                                           # 全部测试源码编译通过（含 HttpAgentPreferenceTest）
cd frontend && npm run lint && npm run type-check && npm run build-only        # 全部通过
```

## 未验证与 NOT_RUN 项

- **`productionAcceptance: NOT_RUN`**：真实生产环境的重启/双实例观察、迁移维护窗口执行、
  生产数据库与真实对象存储上的长周期运行，均未在本次验收中执行；隔离验收使用临时库与
  受控替身，不能替代生产放行。
- 迁移脚本未在真实存量库执行（本票无存量数据需要回填，见诚实声明）；回滚归档与恢复只在隔离验收的临时表上验证，真实维护窗口的停写、备份与人工处置仍属人工关卡。
- 偏好注入是对话消息层（`buildUserMessage` 附加片段），不覆盖 Agent 内部工具调用的
  二次提示词构建；反馈入口 `handleFeedback` 不注入偏好（其输入是学习来源，不是生成请求）。
- 前端客户端为契约生成产物（`api/agentPreferenceController.ts` + `typings.d.ts` + `index.ts`，
  由 `target/openapi-contract.json` 离线生成并逐字节对账），用户中心 `PreferenceSection.vue`
  从生成客户端导入；不再有手写 `agentPreference.ts`。后续重新生成时以 `npm run openapi2ts`
  产物为准（真实 `src/api` 会被生成器重建，手写客户端一律放 `src/api-custom/`）。
