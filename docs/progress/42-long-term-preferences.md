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
| 加载给模型 | `loadForModel` 合并视图 | 推断内容打底，显式键覆盖同名键——显式设置优先于模型推断；反馈历史不进入模型上下文 |

清除的实现是「内容置空 + 版本与纪元递增」，**不删除行**：行是挡住在途学习写回的闸门；
删行会让在途学习任务随后 `insertIfAbsent` 重建出行，看似数据复活。清除不影响
`autoLearn` 开关本身（清除数据 ≠ 关闭学习），开关开着时清除后的新反馈按新规则重新积累。

存量数据迁移的诚实声明：旧实现把偏好放在进程内 `MemoryStore`
（`AgentStoreConfig#memoryStore`，无磁盘形态），实例停止即丢失，不存在可恢复的存量数据。
迁移脚本不做任何回填，也不把进程内旧值虚构为已迁移；升级后从空偏好开始。

## 改动结构

- `sql/migrations/2026-11-05-user-preference.sql` + `-rollback.sql`（`CREATE TABLE IF NOT EXISTS`，回滚只删新表）；`sql/create_vision_space.sql` 同步
- `model/entity/UserPreference.java`、`mapper/UserPreferenceMapper.java`（全部语句带 `userId` 归属条件；学习写入双重 CAS）
- `agent/service/UserPreferenceService.java` 重写为 DB 持久化（原 MemoryStore 实现删除）；`agent/hook/UserPreferenceLoaderHook.java` 改为委托服务（原进程内负缓存删除——负缓存跨实例会掩盖他人刚写入的偏好）
- `controller/AgentPreferenceController.java`：`GET /agent/preference`、`POST /agent/preference/update|clear|auto-learn`，身份只取 `userService.getLoginUser`，接口不接收 `userId` 参数
- 前端：`api/agentPreference.ts`（手写客户端，合并后需 `npm run openapi2ts` 以生成产物为准）、`components/user-center/PreferenceSection.vue`、`UserCenterPage.vue` 新增「AI 偏好」菜单
- `ImageAgent` 反馈路径改传 `Long userId`（不再字符串化）

## 逐项验收

| 验收项 | 证据 | 状态 |
| --- | --- | --- |
| 27.1 偏好持久保存并按可信身份隔离，其他用户及默认无权管理员不能读写 | `HttpAgentPreferenceTest#unauthenticatedRequestsAreRejected`、`#otherUsersAndAdminsCanOnlySeeTheirOwnPreferenceRow`（真实 HTTP + 隔离 MySQL）；`UserPreferenceMapper` 所有语句带 `userId` 条件；控制器不接收 `userId` 参数 | NOT_RUN（隔离测试，等待验收运行） |
| 27.2 UI 和接口打通查看、显式修改、清除与开关；自动学习仅在明确开启后发生 | 接口：`AgentPreferenceController` 四个入口；`HttpAgentPreferenceTest#defaultViewHasAutoLearnOffAndEmptyContent`、`#explicitUpdateTogglesAndViewReflectIt`、`#clearWipesContentButKeepsTheRowAsTombstone`；UI：`PreferenceSection.vue`（查看/编辑/清除确认/开关）；单测：`UserPreferenceServiceTest#saveFeedback_autoLearnOff_skipsLearningAndHistory`（本机 PASS） | 单测 PASS；隔离测试 NOT_RUN |
| 27.3 显式设置优先于模型推断，关闭或清除与在途旧任务交错时数据不被复活 | 单测（本机全部 PASS）：`UserPreferenceServiceTest#loadForModel_explicitOverridesLearnedOnSameKey`、`#saveFeedback_learningWritesOnlyLearnedColumns_neverExplicit`、`#saveFeedback_casConflictAfterClear_abortsAndNeverRewrites`、`#saveFeedback_casConflictAfterDisable_abortsAndNeverRewrites`、`#saveFeedback_casConflictFromConcurrentLearning_retriesWithFreshVersion`；SQL 层守卫：`HttpAgentPreferenceTest#inFlightLearningCannotRewriteAfterClear`、`#inFlightLearningCannotRewriteEvenIfLearningIsReenabled` | 单测 PASS；隔离测试 NOT_RUN |
| 27.4 重启及双实例读取一致，存量可迁移信息保留，无法恢复的进程内旧值不虚构为已迁移 | `HttpAgentPreferenceTest#preferenceSurvivesRestartAndIsConsistentAcrossInstances`（mapper 直读共享库 = 另一实例视角）；持久化结构（唯一键 + 全部读路径查库）使一致性成为结构性质；存量迁移的诚实声明见上文 | NOT_RUN（隔离测试，等待验收运行） |
| 27.5 提供兼容迁移和安全回滚，自动测试不调用真实模型 | `2026-11-05-user-preference.sql`（`IF NOT EXISTS` 幂等）+ `-rollback.sql`（只删新表，无数据回填）；偏好控制与学习路径全部不经 LLM：单测直接测 `UserPreferenceService`（Mockito），隔离测试打 HTTP 与 mapper，零模型调用 | PASS（迁移脚本只在验收临时库执行，未在真实库运行） |

## 本次实际执行的验证

2026-11-05，Windows 11 + JDK（`--release 21`）+ Maven：

```bash
mvn -q test -Dtest=UserPreferenceServiceTest   # 13 条全部通过
mvn -q test-compile                             # 全部测试源码编译通过（含 HttpAgentPreferenceTest）
cd frontend && npm run type-check               # 通过（vue-tsc --build）
```

说明：按编排约定本工单不运行 `scripts/acceptance.ps1`（并发会产生假失败），
隔离环境用例由编排者在验收窗口串行执行。

## 未验证与 NOT_RUN 项

- `HttpAgentPreferenceTest`（7 条）与 `UserPreferenceServiceTest` 之外的全部既有回归：等待隔离验收运行。
- 迁移脚本未在真实存量库执行（本票无存量数据需要回填，见诚实声明）。
- `UserPreferenceLoaderHook` 在改造前就没有接入 ImageAgent 的提示词构建（死代码状态维持）；
  本票实现并测试了「显式优先」的加载语义（`loadForModel`），是否把偏好注入模型上下文
  属于功能广度，留待后续工单，不在本票宣称完成。
- 前端 `agentPreference.ts` 为手写客户端；合并后运行 `npm run openapi2ts` 时若接口已进规范，以生成产物为准。
