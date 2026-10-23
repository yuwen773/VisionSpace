# #38 行为统计与推荐链路正确性

工单：[38](https://github.com/yuwen773/VisionSpace/issues/38)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26)。
分支：`codex/38-stats-recommendation`。本文只记录本次实际执行到的证据，未执行的部分单列在「人工关卡」。

## 改动结构

| 位置 | 作用 |
| --- | --- |
| `user_picture_action` 表（`sql/migrations/2026-10-23-stats-action-event.sql`） | 事件幂等键 `eventId`（唯一索引）与持久消费状态 `isAccumulated` / `claimToken` / `claimedAt` / `claimExpireAt` / `attemptCount` |
| `PictureActionController` / `PictureActionServiceImpl` | 公开行为上报入口：匿名只允许曝光、点击、浏览；目标必须是公开图库里已审核通过的图片；客户端声明的用户 id 一律拒绝 |
| `PictureActionRateLimiter` | Redis 固定窗口限流与「受理 / 重复 / 拒绝 / 限流」四个计数 |
| `PictureStatsAggregateServiceImpl` | 一轮聚合按「领取 → 累计 → 完成」推进；租约到期可被其他实例重新领取；「已计入未完成」只补完成标记，不重新入账 |
| `UserPictureActionMapper.xml` / `PictureStatsMapper.xml` | 领取（租约）、行锁读取、标记已计入、标记完成、积压统计，以及按图片关联键的 `INSERT … ON DUPLICATE KEY UPDATE` 增量 |
| `PictureStatsService` / `PictureServiceImpl` / `PictureRecommendServiceImpl` | 统一的图片关联查询契约：统计一律按 `pictureId` 关联，不按统计主键 |
| `PictureRecommendServiceImpl` / `RecommendCacheManager` | 有界候选池 + 按「类型 + 分页上下文」缓存一份候选；翻页只做内存切片 |
| `AdminStatsController` | 管理端可观测入口：待处理积压与上报计数（只含数量） |
| `frontend/src/composables/usePictureAction.ts`、`useRecommendPaging.ts` | 前端事件幂等键与分页上下文 |

## 验收对照

| 验收项 | 证据 | 状态 |
| --- | --- | --- |
| 18.1 统计主键与图片 ID 不同的数据集，列表显示与热门评分关联正确 | `HttpPictureStatsAssociationTest#statsPrimaryKeyIsIndependentOfThePictureId`（统计主键 9901 与图片 ID 完全不重叠）、`#detailShowsTheStatsThatBelongToThePicture`、`#listingAttachesEachPictureItsOwnStats` | 已覆盖 |
| 18.2 聚合更新已有统计，不误判新增、不撞唯一约束、不覆盖他图统计 | `HttpPictureStatsAggregationTest#aggregationUpdatesTheExistingStatsRowInsteadOfCreatingAnother`（已有统计行被累加，图片统计行仍唯一，另一张图的统计不受影响）；`PictureStatsMapper.upsertIncrement` 以 `pictureId` 唯一键做 `ON DUPLICATE KEY UPDATE` | 已覆盖 |
| 18.3 同一专用图片关联查询契约，不再混用主键批量查询 | `PictureStatsService#mapByPictureIds` 为唯一入口，`PictureServiceImpl` 与 `PictureRecommendServiceImpl` 都只经它取统计；旧的 `UserPictureActionMapper.aggregateByPictureId` / `selectPictureIdsWithActions` 已删除 | 已覆盖 |
| 18.4 经真实 Mapper 与 HTTP 验证 | 上列用例均为 `@IsolatedSpringBootTest`：真实 HTTP、真实 MySQL、真实 Redis | 已覆盖 |
| 19.1 以持久待处理状态领取，不用启动时间窗 | `HttpPictureStatsAggregationTest#oldPendingEventsAreAggregatedRegardlessOfTheirAge`（三天前的事件照样入账）；领取语句按 `isProcessed` / `isAccumulated` / 租约判定 | 已覆盖 |
| 19.2 领取、累计、完成之间注入故障后可恢复；两实例与重复批次不漏计不重计 | `#eventHeldByAnotherInstanceIsNotTouchedUntilItsLeaseExpires`（他实例的有效租约不被抢占，到期后只入账一次）、`#eventAccumulatedButNotCompletedIsCompletedWithoutDoubleCounting`（已计入未完成只补完成标记）、`#repeatedRunsDoNotCountTheSameEventTwice`、`#concurrentAggregationsCountEachEventExactlyOnce`（两个并发领取者各持独立令牌，20 个事件恰好计 20 次） | 已覆盖（同一隔离库上的并发领取者；未做双 JVM 实例实测，见「人工关卡」） |
| 19.3 只标记真正处理成功的事件，不用整窗标记；清理不丢弃待处理事件 | `markProcessed` 限定 `claimToken + isAccumulated = 1`；`#cleanupKeepsPendingEventsAndOnlyRemovesProcessedOnes`（目标图片已不存在的事件不被清理，仍留在待处理） | 已覆盖 |
| 19.4 用最终统计、事件状态与重试结果证明行为，并提供脱敏积压信息 | `HttpPictureStatsAggregationTest#reportedEventReachesTheFinalStatistics`（上报 → 聚合 → 详情接口的 viewCount）、`#backlogCountsPendingWork`；`HttpPictureStatsObservabilityTest#theBacklogIsVisibleToAdministratorsOverHttp`（管理端 HTTP 入口可见待处理、已计入未完成、租约中、反复失败、目标缺失五类计数，且不含事件、图片或用户信息） | 已覆盖 |
| 19.5 旧未处理记录迁移与回滚不重复入账，已知冲突形成维护清单 | `PictureStatsEventMigrationTest#forwardMigrationAddsTheEventAndClaimStateWithoutLosingLegacyRows`、`#rollbackRemovesTheNewStateAndDoesNotRecountAlreadyCountedEvents`（回滚先把「已计入未完成」标记为已完成，避免再入账）、`#maintenanceListIsExecutableAndOnlyReads`（`2026-10-23-stats-action-event-maintenance.sql` 只读，列出四类冲突） | 已覆盖（隔离库演练） |
| 20.1 匿名曝光、点击、浏览贯通页面、HTTP、持久事件与最终聚合；强制登录查询不挡匿名分支 | `HttpPictureActionReportingTest#anonymousImpressionClickAndBrowseBecomePendingEvents`（匿名三种行为都落成待处理事件，userId 为 0）、`HttpPictureStatsAggregationTest#reportedEventReachesTheFinalStatistics`；前端 `usePictureAction` + `ExplorePage` / `PictureDetailPage` 上报 | 已覆盖 |
| 20.2 私有、未审核、不存在与已处置目标，非法行为与越权用户字段明确拒绝 | `#privateUnreviewedAndMissingTargetsAreRejected`、`#rejectedTargetsAreRejectedWithoutSideEffects`（审核被拒绝的目标）、`#anonymousAccountActionsAreRejectedWithoutSideEffects`（匿名点赞、收藏 → 40101，零事件）、`#clientDeclaredUserIsRejected`（客户端声明 userId → 40000，零事件）、`#invalidActionTypeAndSourceAreRejected` | 已覆盖 |
| 20.3 匿名点赞、收藏与修改不产生账户状态变更；同一事件重放不重复累计 | `#anonymousAccountActionsAreRejectedWithoutSideEffects`（零事件、零副作用）、`#replayingTheSameEventDoesNotCreateASecondEvent`（同一事件键只留一条）；上报只写事件明细，不触碰账户状态 | 已覆盖 |
| 20.4 事件范围、限流与异常流量结果可观察，客户端不能靠声明用户或 source 取得特权 | `#excessiveTrafficIsRejectedAndObservable`（超限被拒且计数上升）、`HttpPictureStatsObservabilityTest#theReportMetricsAreVisibleToAdministratorsOverHttp`（受理 / 重复 / 拒绝 / 限流四个计数经 HTTP 可读）；客户端 source 只允许枚举内取值，客户端 userId 一律拒绝 | 已覆盖 |
| 21.1 固定数据集覆盖首页、末页、越界页与空集合，越界页不把总数设为零 | `HttpRecommendPaginationTest#homePageReturnsTheFirstSliceWithARealTotal`、`#lastPageReturnsTheRemainderAndTheTotalStaysReal`、`#outOfRangePageKeepsTheTotalOfTheNonEmptySet`、`#anEmptyLibraryReturnsNoRecordsAndAZeroTotal` | 已覆盖 |
| 21.2 热门排序取得正确图片统计；随机总数不是固定常量，同一上下文不重复不遗漏 | `#hotOrderingUsesTheStatisticsOfThePictureItself`（统计主键与图片 ID 不重叠，互动量最高者排第一）、`#randomTotalIsNotAFixedConstant`、`#randomWithinTheSameContextIsStableAndComplete` | 已覆盖 |
| 21.3 候选获取与评分有界，不因每页缓存未命中重载全量结果 | `HttpRecommendCandidateBoundTest`（候选池压到 4：热门与随机都只覆盖候选池）、`HttpRecommendPaginationTest#pagingThroughTheHotListReusesTheSameCandidateList`（翻三页只留一份候选缓存）、`#theCacheKeepsOneCandidateListPerContextInsteadOfOneFullResultPerPage` | 已覆盖 |
| 21.4 页面翻页与真实 HTTP 结果一致，保留现有推荐类型，不重做推荐算法 | 前端 `useRecommendPaging` 把服务端返回的上下文带回下一页（`useRecommendPaging.spec.ts`）；后端 HTTP 用例覆盖同一组数据；推荐类型仍是 hot / latest / quality / random，评分与重排逻辑未改，只把候选集合改为有界 | 已覆盖（页面层为前端单测，未做浏览器 E2E） |

## 本次实际执行的验证

2026-10-23，Windows + JDK 21 / Maven 3.9 / Node 22 / PowerShell 7，原生隔离 MySQL 8.0.26 + Redis 3.2.100（本机 Docker 不可用，因此不是容器实测）：

```powershell
pwsh.exe -NoLogo -NoProfile -File ./scripts/acceptance.ps1 `
  -MySqlBin '<MySQL 8.0.26 bin>' -RedisBin '<Redis-x64-3.2.100>'
```

隔离验收门禁结果：runner-tests / secret-scan / isolation / backend / frontend-install / frontend-typecheck / frontend-tests / frontend-build / cleanup 全部 PASS；后端 299+ 用例、前端 24+ 用例全部通过。

## 人工关卡

- **双 JVM 实例**：19.2 的「两个实例」以同一隔离库上的两个并发领取者（各自持独立领取令牌）验证，租约与行锁都在数据库层生效；未做跨进程双实例实测。
- **真实迁移与回滚**：19.5 的前向与回滚脚本只在隔离库的临时表上演练过，未在真实数据上执行。
- **发布与部署**：本工单只提交代码与迁移脚本，未做真实迁移、凭据轮换或部署。
