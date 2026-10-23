# #38 行为统计与推荐链路正确性

工单：[38](https://github.com/yuwen773/VisionSpace/issues/38)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26)。
分支：`codex/38-stats-recommendation`。本文只记录本次实际执行到的证据，未执行的部分单列在「人工关卡」。

## 改动结构

| 位置 | 作用 |
| --- | --- |
| `user_picture_action` 表（`sql/migrations/2026-10-23-stats-action-event.sql`） | 事件幂等键 `eventId`（唯一索引）与持久消费状态 `isAccumulated` / `claimToken` / `claimedAt` / `claimExpireAt` / `attemptCount` |
| `picture_action_event_dedupe` 表 | 幂等台账：事件幂等键已被受理的独立记录，与事件载荷的保留期分开 |
| `PictureActionController` / `PictureActionServiceImpl` | 公开行为上报入口：匿名只允许曝光、点击、浏览；目标必须当前对公开访客可见（可见性走集中入口 `PictureContentAuthManager`）；客户端声明的用户 id 一律拒绝 |
| `PictureActionRateLimiter` | Redis 固定窗口限流与「受理 / 重复 / 拒绝 / 限流」四个计数 |
| `PictureStatsAggregateServiceImpl` | 一轮聚合按「领取 → 累计 → 完成」推进；租约到期可被其他实例重新领取；「已计入未完成」只补完成标记，不重新入账；清理只删事件载荷，不动幂等台账 |
| `UserPictureActionMapper.xml` / `PictureStatsMapper.xml` | 领取（租约）、行锁读取、标记已计入、标记完成、积压统计，以及按图片关联键的 `INSERT … ON DUPLICATE KEY UPDATE` 增量（新增行与更新行用同一套点击率语义） |
| `PictureStatsService` / `PictureServiceImpl` / `PictureRecommendServiceImpl` | 统一的图片关联查询契约：统计一律按 `pictureId` 关联，不按统计行自己的主键 |
| `PictureRecommendServiceImpl` / `RecommendCacheManager` | 有界候选池 + 按「类型 + 分页上下文」缓存一份候选；分页上下文与候选快照绑定，快照失效时明确结束上下文 |
| `RecommendPageVO` | 增加 `contextValid`：客户端带来的上下文是否仍然对应当前候选 |
| `AdminStatsController` | 管理端可观测入口：待处理积压与上报计数（只含数量） |
| `frontend/src/composables/usePictureAction.ts`、`useRecommendPaging.ts` | 前端事件幂等键、分页上下文，以及按真实序列化解析总数 |

## 验收对照

| 验收项 | 证据 | 状态 |
| --- | --- | --- |
| 18.1 统计行的行键与图片 ID 不同的数据集，列表显示与热门评分关联正确 | `HttpPictureStatsAssociationTest#pictureStatsFollowPictureIdNotTheirOwnRowKey`（统计行的行键 9901 与图片 ID 完全不重叠）、`#detailShowsTheStatsThatBelongToThePicture`、`#listingAttachesEachPictureItsOwnStats` | 已覆盖 |
| 18.2 聚合更新已有统计，不误判新增、不撞唯一约束、不覆盖他图统计 | `HttpPictureStatsAggregationTest#aggregationUpdatesTheExistingStatsRowInsteadOfCreatingAnother`（已有统计行被累加，图片统计行仍唯一，另一张图的统计不受影响）；`PictureStatsMapper.upsertIncrement` 以 `pictureId` 唯一键做 `ON DUPLICATE KEY UPDATE` | 已覆盖 |
| 18.3 同一专用图片关联查询契约，不再混用主键批量查询 | `PictureStatsService#mapByPictureIds` 为唯一入口，`PictureServiceImpl` 与 `PictureRecommendServiceImpl` 都只经它取统计；旧的 `UserPictureActionMapper.aggregateByPictureId` / `selectPictureIdsWithActions` 已删除 | 已覆盖 |
| 18.4 经真实 Mapper 与 HTTP 验证 | 上列用例均为 `@IsolatedSpringBootTest`：真实 HTTP、真实 MySQL、真实 Redis | 已覆盖 |
| 19.1 以持久待处理状态领取，不用启动时间窗 | `HttpPictureStatsAggregationTest#oldPendingEventsAreAggregatedRegardlessOfTheirAge`（三天前的事件照样入账）；领取语句按 `isProcessed` / `isAccumulated` / 租约判定 | 已覆盖 |
| 19.2 领取、累计、完成之间注入故障后可恢复；两实例与重复批次不漏计不重计 | `#eventHeldByAnotherInstanceIsNotTouchedUntilItsLeaseExpires`（他实例的有效租约不被抢占，到期后只入账一次）、`#eventAccumulatedButNotCompletedIsCompletedWithoutDoubleCounting`（已计入未完成只补完成标记）、`#repeatedRunsDoNotCountTheSameEventTwice`、`#concurrentAggregationsCountEachEventExactlyOnce`（两个并发领取者各持独立令牌，20 个事件恰好计 20 次） | 部分覆盖（同一隔离库上的并发领取者；跨进程双实例未实测，见「人工关卡」） |
| 19.3 只标记真正处理成功的事件，不用整窗标记；清理不丢弃待处理事件 | `markProcessed` 限定 `claimToken + isAccumulated = 1`；`#cleanupKeepsPendingEventsAndOnlyRemovesProcessedOnes`（目标图片已不存在的事件不被清理，仍留在待处理） | 已覆盖 |
| 19.4 用最终统计、事件状态与重试结果证明行为，并提供脱敏积压信息 | `HttpPictureStatsAggregationTest#reportedEventReachesTheFinalStatistics`（上报 → 聚合 → 详情接口的 viewCount）、`#backlogCountsPendingWork`；`HttpPictureStatsObservabilityTest#theBacklogIsVisibleToAdministratorsOverHttp`（管理端 HTTP 入口可见待处理、已计入未完成、租约中、反复失败、目标缺失五类计数，且不含事件、图片或用户信息） | 已覆盖 |
| 19.5 旧未处理记录迁移与回滚不重复入账，已知冲突形成维护清单 | `PictureStatsEventMigrationTest#forwardMigrationAddsTheEventAndClaimStateWithoutLosingLegacyRows`、`#rollbackRemovesTheNewStateAndDoesNotRecountAlreadyCountedEvents`（回滚先把「已计入未完成」标记为已完成，避免再入账）、`#rollbackKeepsTheDedupeLedger`（回滚保留幂等台账）、`#forwardMigrationCreatesTheDedupeLedgerAndBackfillsExistingEventKeys`、`#maintenanceListIsExecutableAndOnlyReads`（`2026-10-23-stats-action-event-maintenance.sql` 只读，列出五类冲突） | 已覆盖（隔离库演练） |
| 20.1 匿名曝光、点击、浏览贯通页面、HTTP、持久事件与最终聚合；强制登录查询不挡匿名分支 | `HttpPictureActionReportingTest#anonymousImpressionClickAndBrowseBecomePendingEvents`（匿名三种行为都落成待处理事件，userId 为 0）、`HttpPictureStatsAggregationTest#reportedEventReachesTheFinalStatistics`；前端 `usePictureAction` + `ExplorePage` / `PictureDetailPage` 上报 | 已覆盖 |
| 20.2 私有、未审核、不存在与已处置目标，非法行为与越权用户字段明确拒绝 | `#privateUnreviewedAndMissingTargetsAreRejected`、`#rejectedTargetsAreRejectedWithoutSideEffects`（审核被拒绝的目标）、`#anInvisibleTargetIsRejectedWithoutSideEffects`（集中可见性入口判定目标不可见时明确拒绝，零事件、零统计；隔离前 / 隔离后 / 恢复后与缓存刷新都覆盖）、`#anonymousAccountActionsAreRejectedWithoutSideEffects`（匿名点赞、收藏 → 40101，零事件）、`#clientDeclaredUserIsRejected`（客户端声明 userId → 40000，零事件）、`#invalidActionTypeAndSourceAreRejected` | 部分覆盖（拒绝路径已验证；真实隔离链路待 #36，隔离场景保持 BLOCKED，见「人工关卡」） |
| 20.3 匿名点赞、收藏与修改不产生账户状态变更；同一事件重放不重复累计 | `#anonymousAccountActionsAreRejectedWithoutSideEffects`（零事件、零副作用）、`#replayingTheSameEventDoesNotCreateASecondEvent`、`#replayingAnEventAfterItsPayloadWasCleanedUpDoesNotCountItTwice`（载荷被保留期清理之后重放仍不入账）；上报只写事件明细，不触碰账户状态 | 已覆盖 |
| 20.4 事件范围、限流与异常流量结果可观察，客户端不能靠声明用户或 source 取得特权 | `#excessiveTrafficIsRejectedAndObservable`（超限被拒且计数上升）、`HttpPictureStatsObservabilityTest#theReportMetricsAreVisibleToAdministratorsOverHttp`（受理 / 重复 / 拒绝 / 限流四个计数经 HTTP 可读）；客户端 source 只允许枚举内取值，客户端 userId 一律拒绝 | 已覆盖 |
| 21.1 固定数据集覆盖首页、末页、越界页与空集合，越界页不把总数设为零 | `HttpRecommendPaginationTest#homePageReturnsTheFirstSliceWithARealTotal`、`#lastPageReturnsTheRemainderAndTheTotalStaysReal`、`#outOfRangePageKeepsTheTotalOfTheNonEmptySet`、`#anEmptyLibraryReturnsNoRecordsAndAZeroTotal`、`#aHugePageNumberReturnsAnEmptyPageWithTheRealTotal`（极大页码返回空记录与真实总数，不报服务器错误） | 已覆盖 |
| 21.2 热门排序取得正确图片统计；随机总数不是固定常量，同一上下文不重复不遗漏 | `#hotOrderingUsesTheStatisticsOfThePictureItself`（统计行的行键与图片 ID 不重叠，互动量最高者排第一）、`#randomTotalIsNotAFixedConstant`、`HttpRecommendCandidateBoundTest#theTotalIsNotCappedByTheCandidatePool`（候选池压到 4、图库 12 张时总数仍是 12，不随池上限缩小）、`#randomWithinTheSameContextIsStableAndComplete`、`#aContextIsEndedWhenTheCandidatesAreRefreshed`、`#aContextIsEndedWhenTheCachedCandidatesExpire`（候选被刷新或到期后旧上下文被明确结束，不静默改绑） | 已覆盖 |
| 21.3 候选获取与评分有界，不因每页缓存未命中重载全量结果 | `HttpRecommendCandidateBoundTest`（候选池压到 4：热门与随机都只覆盖候选池）、`HttpRecommendPaginationTest#pagingThroughTheHotListReusesTheSameCandidateList`（翻三页只留一份候选缓存）、`#theCacheKeepsOneCandidateListPerContextInsteadOfOneFullResultPerPage`、`#aContextIsEndedWhenTheCandidatesAreRefreshed`（结束后候选缓存仍只有一份） | 已覆盖 |
| 21.4 页面翻页与真实 HTTP 结果一致，保留现有推荐类型，不重做推荐算法 | 前端 `useRecommendPaging` 把服务端返回的上下文带回下一页，并用真实序列化形状（`total` 是十进制字符串）解析总数（`useRecommendPaging.spec.ts`）；后端 HTTP 用例覆盖同一组数据；推荐类型仍是 hot / latest / quality / random；评分与重排沿用现有实现。改变的是候选获取方式：热门与随机改为有界候选池，随机改为确定性随机序（同一上下文可重建同一顺序），随机候选同样过现有重打散 | 已覆盖（页面层为前端单测，未做浏览器 E2E） |

## 审查问题的修复与回归对照

审查报告由评审会话产出（2026-10-23，HEAD `3a44572`，报告未入库）。六条问题逐条修复，
每条都有「修复前失败、修复后通过」的回归用例。

| 审查问题 | 根因 | 修复 | 回归用例 | 修复前的失败证据 |
| --- | --- | --- | --- | --- |
| 1 [P1] 清理后同一事件再次入账 | 事件载荷被保留期清理时，唯一索引上的事件幂等键一起消失，重放变成新的待处理事件 | 幂等依据单独存进幂等台账（`picture_action_event_dedupe`），上报先登记台账再写载荷；清理只删载荷；回滚保留台账 | `HttpPictureStatsAggregationTest#replayingAnEventAfterItsPayloadWasCleanedUpDoesNotCountItTwice`、`PictureStatsEventMigrationTest#rollbackKeepsTheDedupeLedger` | `a replayed event must not become a new pending event ==> expected: <0> but was: <1>` |
| 2 [P1] 已隔离目标的拒绝尚未实现 | 服务里自己判断 spaceId 与审核状态，绕开了集中可见性入口 | 目标可见性改由 `PictureContentAuthManager.canReadContent` 判定，不另写并行规则 | `HttpPictureActionReportingTest#anInvisibleTargetIsRejectedWithoutSideEffects`（隔离前 / 隔离后 / 恢复后 / 缓存刷新） | `expected: <40101> but was: <0>`（旧规则放行了被隔离的目标） |
| 3 [P2] 新统计行首次入账 CTR 为零 | `INSERT` 固定写 `ctr=0.000000`，只有重复键分支才重算 | 新增行与更新行用同一套点击率语义：按本批曝光与点击算出，零曝光与空值按 0 处理 | `HttpPictureStatsAggregationTest#theFirstAggregationWritesTheClickThroughRateOfANewStatsRow`、`#aStatsRowWithoutImpressionsKeepsTheClickThroughRateAtZero`、`#nullCountsAreTreatedAsZeroByTheMapper` | `a new stats row must carry the click through rate of its own batch: 0.000000` |
| 4 [P2] 热门分页上下文在缓存刷新后失效 | 热门忽略上下文、共用默认槽位；候选被刷新后新排序悄悄替换旧排序，客户端仍按旧页码继续 | 上下文与候选快照绑定：快照被刷新或到期后明确结束上下文（`contextValid=false`），交回当前候选的第一页；候选份数仍然有界 | `HttpRecommendPaginationTest#aContextIsEndedWhenTheCandidatesAreRefreshed`、`#aContextIsEndedWhenTheCachedCandidatesExpire` | `a context that no longer matches the candidates must be ended explicitly ==> expected: <false> but was: <true>` |
| 5 [P2] 前端忽略真实 HTTP 总数 | `Long` 被序列化成十进制字符串，前端只认 `number`，页面总数一直是 0 | 手写客户端如实声明 `number \| string`；组合式函数校验并解析非负安全整数，非法值明确标记且不抹成零；OpenAPI 契约同步标成字符串 | `frontend/src/composables/__tests__/useRecommendPaging.spec.ts`（真实形状 `total: "12"`、末页、越界页、空集合、非法值） | 5 条用例失败：`expected +0 to be 12`（总数一直是 0） |
| 6 [P2] 大越界页整数溢出并返回服务器错误 | 切片偏移用 int 乘法，极大页码溢出成负数 | 偏移改用 long 计算并做边界判断，越界页返回空记录与真实总数 | `HttpRecommendPaginationTest#aHugePageNumberReturnsAnEmptyPageWithTheRealTotal`（hot / random / latest / quality 一致） | `hot must not fail on a huge page ==> expected: <0> but was: <50000>` |

修复前的复现方式：把对应实现临时改回审查时的写法再跑同一批用例，得到上表最后一列的失败；
随后恢复修复并重跑，全部通过。

## 第二轮评审（同日复审）的修复

六项修复合入后又做了一轮双轴评审（标准轴 / 规格轴），按其发现追加修复：

| 评审发现 | 处理 | 回归 |
| --- | --- | --- |
| hot/random 的总数取候选池大小，图库超过候选上限时退化为常量（旧「固定总数」缺陷换个常量回来） | 总数一律按公开图库的真实数量现算（`countPublicCandidates` / `countQualityCandidates`），不再放进候选缓存；候选池有界，但对外总数不随池上限缩小 | `HttpRecommendCandidateBoundTest#theTotalIsNotCappedByTheCandidatePool`（候选池压到 4、图库 12 张时总数必须是 12），该类中原先断言「总数等于池大小」的用例一并纠正 |
| 限流请求同时计入限流与拒绝两个计数，可观测信息失真 | 拒绝计数移到各拒绝分支就地执行（`reject` 助手），去掉外层按异常统一计数；限流分支只计限流 | `HttpPictureActionReportingTest#excessiveTrafficIsRejectedAndObservable` 追加断言：被限流后拒绝计数不变 |
| 手写推荐响应类型与生成契约（`API.RecommendPageVO`）重复且形状不一致 | 手写客户端改用生成契约，删除 `RecommendPageData` / `RecommendPageResponse`；解析规则不变（非负安全整数） | `useRecommendPaging.spec.ts` 全部通过；`npm run type-check` 通过 |
| 本 diff 新增的 `CONTEXT.md` 条目排斥「统计主键 / 去重依据」，代码、测试名与文档却用了它们 | 测试改名 `pictureStatsFollowPictureIdNotTheirOwnRowKey`；注释与文档改说「统计行自己的主键」「幂等台账」 | 全仓 grep 无残留 |
| 进度文档引用未入库的审查报告路径 | 改为说明报告由评审会话产出、未入库 | — |
| 随机推荐丢了既有的重打散步骤 | 确定性随机序选出候选后重新过 `reorderPictures`，与既有行为一致；顺序仍由上下文唯一决定 | `#randomWithinTheSameContextIsStableAndComplete` 继续通过 |
| 无调用方的 `getRecommendPictureIds`、台账查询方法与实体、恒为 8 的摘要长度参数 | 一并移除（`PictureRecommendService`、`PictureActionEventDedupeMapper`、`PictureActionEventDedupe`、`HashUtils.shortHash` 收敛为单参） | 后端编译与全量验收通过 |
| 进度文档「评分与重排逻辑未改」与实际改动不符；20.2 标「已覆盖」与人工关卡 BLOCKED 矛盾 | 措辞改为如实描述（候选获取方式变了，随机加了确定性随机序）；19.2 / 20.2 状态改为「部分覆盖」，隔离场景明确保持 BLOCKED | — |
| 评审保留意见：热门候选池的「互动量 + 最新」配比与确定性随机序属于超出 21.4 字面的算法改动，但它们是 21.3（候选有界）与 21.2（同一上下文稳定）的直接后果，去掉会让新图片从热门消失、翻页无法按同一上下文稳定重建；保留并在上一行如实记录 | 记录在案，交由人工评审裁决 | — |

## 本次实际执行的验证

2026-10-23，Windows + JDK 21 / Maven 3.9 / Node 22 / PowerShell 7，原生隔离 MySQL 8.0.26 + Redis 3.2.100（本机 Docker 不可用，因此不是容器实测）：

```powershell
pwsh.exe -NoLogo -NoProfile -File ./scripts/acceptance.ps1 `
  -MySqlBin 'D:\Work\Backend\MySQL-8.0.26\mysql-8.0.26-winx64\bin' `
  -RedisBin 'D:\Work\Backend\Redis-x64-3.2.100'
```

隔离验收门禁结果：runner-tests / secret-scan / isolation / backend / frontend-install / frontend-typecheck / frontend-tests / frontend-build / cleanup 全部 PASS；后端 `Tests run: 316, Failures: 0, Errors: 0`，前端 7 个文件 35 条用例全部通过。六项修复合入后跑过一轮（315 条），第二轮评审的追加修复合入后再跑一轮（316 条，含新增的总数防回退用例），两轮均为全 PASS。

`scripts/acceptance.ps1` 与 `scripts/browser-acceptance.ps1` 里的建表数量守卫由 12 改为 13：新增的幂等台账
`picture_action_event_dedupe` 是这次唯一的新表，守卫的用途是「表数量变化必须人工复核」，本次已复核。

前端接口契约按 CLAUDE.md 重新生成（`npm run openapi2ts`，对着隔离库上跑起来的后端取 api-docs）：
`RecommendPageVO.total` 标成字符串、`contextValid` 标成布尔，重新生成后 `npm run type-check` 与前端用例通过。

## 人工关卡

- **#36 的真实隔离链路（BLOCKED）**：#36「私有资源管理与反馈保护」尚未实施，仓库里没有隔离状态表与服务。本工单把公开行为上报接到集中可见性入口 `PictureContentAuthManager.canReadContent`，隔离规则落在该入口时公开上报随之一起生效；用例用「集中入口判定目标不可见」模拟隔离生效，证明走的是同一条规则。**真实隔离状态下的端到端验证仍未完成，不得据此宣称 20.2 的隔离场景已验收**。#36 落地后需要在独立临时集成工作树验证两者组合，且不得合并远程 PR。
- **双 JVM 实例**：19.2 的「两个实例」以同一隔离库上的两个并发领取者（各自持独立领取令牌）验证，租约与行锁都在数据库层生效；未做跨进程双实例实测。
- **真实迁移与回滚**：19.5 的前向与回滚脚本只在隔离库的临时表上演练过，未在真实数据上执行；幂等台账是只增的窄表，退役时另行处置。
- **发布与部署**：本工单只提交代码与迁移脚本，未做真实迁移、凭据轮换或部署。
