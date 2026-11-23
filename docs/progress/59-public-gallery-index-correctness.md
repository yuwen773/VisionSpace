# 59 · 公开图库索引正确性整改（B1-0）

日期：2026-11-23
分支：`t3/rag-agent-audit-fixes`（基线 `origin/main` @ 7c9f2fa）
范围：公开图库 Chroma 索引的写入生命周期与查询侧有效性。不涉及 RRF、不新增 Agent、不更换向量数据库，也不改权限架构。

## 问题

审计报告 P1-01 ~ P1-04 的核心是同一个事实：索引与数据库是两份状态，而此前只有写入时刻那一份快照在决定写什么。

| 现象 | 代码位置（整改前） | 后果 |
| --- | --- | --- |
| 编辑时用 `oldPicture` 重建索引 | `PictureServiceImpl#editPicture` | 编辑后转待审核的图片带着旧内容留在索引里 |
| 撤审只在 `PASS` 时写索引，从不删除 | `PictureServiceImpl#doPictureReview` | 撤审、拒绝、删除的图片旧向量一直可召回 |
| 写入是先删后写 | `PictureEmbeddingServiceImpl#updateEmbedding` | 写失败留下空洞，图片从检索里彻底消失且无人重试 |
| 检索直接信任向量元数据里的 URL | `InternalImageSearchTool` | 旧文档的旧地址被当作可展示内容交出去 |

## 改动

### 唯一判定入口：索引应当是什么

`src/main/java/com/yuwen/visionspace/rag/PictureIndexState.java:56` — `decide(picture, quarantined)`：图片为空（已删除）、隔离中、不属于公开图库、未审核通过 → `DELETE`；否则 `UPSERT`。写入侧与读取侧引用同一个判定（`rag/PictureIndexState.java:37`、`:44`）。

### P0-01 编辑按数据库当前状态重建索引

- `service/PictureServiceImpl.java:288`：`editPicture` 只把图片 id 交给同步；不再传 `oldPicture`，也不再自己推断审核状态。
- `service/PictureServiceImpl.java:241`：`doPictureReview` 同样只给 id——审核通过、撤审、拒绝走同一条同步。
- `service/PictureServiceImpl.java:299-305`：`syncPictureIndex(id)` 经 `AfterCommitExecutor` 在事务提交后触发；没有事务时立即执行。
- `service/impl/PictureEmbeddingServiceImpl.java:89-92`：同步时重新 `getById` 读取当前状态并查隔离，再决定动作。迟到的同步任务读到的也是最新状态，乱序执行自然收敛。

### P0-02 覆盖写 + 失败留下可重试的待办

- `service/impl/PictureEmbeddingServiceImpl.java:94-106`：`UPSERT` 走 `vectorStore.add`（Spring AI 1.1.8 `ChromaVectorStore.doAdd` → `upsertEmbeddings`，按文档标识覆盖），不再先删后写；`DELETE` 与「无内容可索引」都只删不写。
- `service/impl/PictureEmbeddingServiceImpl.java:109-112`：失败不吞掉，交给 `recordFailure`。
- `service/impl/PictureEmbeddingServiceImpl.java:232-255`：写入 `picture_index_task`（一张图一行，`ON DUPLICATE KEY` 覆盖），退避 1/2/4/…/60 分钟；失败原因脱敏（URL 替换）并截断到 400 字。
- `model/entity/PictureIndexTask.java` + `mapper/PictureIndexTaskMapper.java:33-76`：待办表与三个语句（登记、取到期、成功后清理）。
- `job/PictureIndexRetryJob.java:40`：每分钟重试到期待办，逐条 try/catch；只装配在 `vectorstore.type=chroma` 下。
- `sql/migrations/2026-11-23-picture-index-task.sql` 与 `-rollback.sql`；`sql/create_vision_space.sql:644` 同步加入（隔离验收库由这份 dump 建表）。两份脚本都按既有约定登记进 `sql/migrations/maintenance-window.manifest.json`（order 17 前向 + order 45 回滚演练），否则「每份迁移脚本都被维护窗口编排恰好一次」的用例会失败。

### P0-03 默认关闭 + 隔离的 RAG 验收配置

- 开发默认仍是 `spring.ai.vectorstore.type=none`（未改动）。
- `src/test/resources/rag-acceptance.yml`：只在显式加载时启用 `chroma`，地址指向必定拒绝连接的端口，嵌入模型由 `acceptance/StubEmbeddingConfiguration.java` 提供确定性替身。
- `rag/RagAcceptanceConfigurationTest.java:42-56`：真实 `ResilientVectorStore` Bean 装配、启动期不连库；`:62-74` 检索降级为空结果；`:80-93` 健康信号 UNKNOWN → DOWN；`:99-107` 不开配置时三个 Bean 都不存在。

### P0-04 查询侧实时复核

- `rag/PublicGalleryIndexReader.java:49`：候选图片 id 一次批量查库、隔离状态一次批量查，逐条在内存判定；`url` 取自数据库当前记录（`:93`），不用向量文档里的旧地址。
- `rag/PublicGalleryIndexReader.java:56`、`:107-131`：元数据里的 id 按 `Number`/`String` 解析，缺失时回退到 `picture_` 文档标识后缀。
- `agent/tools/InternalImageSearchTool.java:61`：检索结果一律经复核后再返回。

### 处置流程与索引联动

- `controller/ResourceModerationController.java:77`、`:94`、`:150-160`：隔离与恢复之后同步索引（仅 `picture` 类型；RAG 关闭时 Bean 不存在，判空跳过）。
- `service/impl/PictureDeletionServiceImpl.java:119`：删除事务之后同步（删除后的同步会判定为移除）。
- `service/impl/PictureUploadServiceImpl.java:307`：上传的公开已过审图片触发同步；调用点在事务提交之后。
- `service/impl/PictureEmbeddingServiceImpl.java:160-178`：`fullSync` 先按清单把「未过审」与「隔离中」的公开图片移出索引，再批量写入，只写不移会让这些图片永远留在索引里。

## 测试证据

| 用例 | 覆盖 | 结果 |
| --- | --- | --- |
| `rag.PictureIndexStateTest`（7） | 判定矩阵 | 通过 |
| `service.impl.PictureEmbeddingServiceImplTest`（9） | 覆盖写不先删、撤审/删除/隔离/空文本移除、写失败保留旧文档并登记重试、成功后清理、乱序按当前状态写、退避计数 | 通过 |
| `rag.PublicGalleryIndexReaderTest`（8） | 复核矩阵、URL 取库、id 解析与回退 | 通过 |
| `rag.PictureIndexRoundTripTest`（4） | 内存向量库按 id 覆盖往返、过滤条件、旧文档被复核丢弃 | 通过 |
| `agent.tools.InternalImageSearchToolTest`（4） | 候选交给复核、复核丢弃不返回、模型无法扩大可见范围 | 通过 |
| `rag.RagAcceptanceConfigurationTest`（4） | 配置门控、真实 Bean、健康信号、降级路径 | 通过 |
| `acceptance.HttpPictureIndexLifecycleTest`（5） | 真实隔离 MySQL：旧向量不返回；编辑/撤审/删除触发同步；越权编辑零写入 | 通过 |
| `acceptance.PictureIndexTaskMapperTest`（3） | 真实 MySQL：同图多失败收敛为一行、退避到期过滤、成功清理 | 通过 |

运行方式：`python -I scripts/drill-isolated-run.py --mysql-bin <MySQL 8.0.26 bin> --redis-bin <redis bin> --test <类名>`；整库回归用 `--full`。

补充的 HTTP 级验收：本机 `Redis-7.4-windows` 目录里的二进制实际是 5.0.14，Sa-Token 1.46 的 `setStringAndKeepTTL` 用到 Redis 6 才有的 `KEEPTTL`，原生 Redis 上登录必报 `ERR syntax error`（A/B：未改动的 `HttpPublicGalleryTest` 同样失败）。改用 Docker 的 `mysql:8.0` + `redis:7` 按 `IsolationSettings` 的 nonce 协议搭隔离环境后，HTTP 用例可正常登录并执行：

| 用例 | 结果 |
| --- | --- |
| `HttpBaselineTest` | 5/5 |
| `HttpPictureActionReportingTest` | 10/10 |
| `HttpPictureGrantTest` | 9/9 |
| `HttpPublicGalleryTest` | 4/4 |
| `HttpResourceQuarantineTest` | 8/8 |
| `PictureIndexLifecycleTest`（HTTP 级隔离库） | 5/5 |
| `PictureIndexTaskMapperTest`（真实 MySQL） | 3/3 |
| `HttpPictureContentAuthTest` | 11/12，失败的是浏览器矩阵用例：本 worktree 的 `frontend/node_modules` 缺 `playwright-core`（`ERR_MODULE_NOT_FOUND`），与改动无关 |

整库回归（`mvn verify` + 第二实例，Docker MySQL/Redis）：822 个用例，8 处失败。逐条归因后：

| 失败 | 归因 | 处理 |
| --- | --- | --- |
| `HttpPictureActionReportingTest` ×3 | 复用同一个隔离库连跑两轮，`picture_action_event_dedupe` 的幂等键仍在，重放被判重（受理成功但不落事件） | 重建库后 10/10 通过 |
| `HttpPictureStatsAggregationTest` ×1 | 手工环境漏了 `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC`；`scripts/acceptance.ps1:23` 本来就会加，MySQL 跑在 UTC | 加上后 14/14 通过 |
| `HttpPictureContentAuthTest` ×1 | 缺 `playwright-core` | 环境依赖 |
| `MaintenanceWindowManifestTest` ×1 | **真问题**：新迁移脚本没登记进 `maintenance-window.manifest.json` | 已补 order 17 / 45，7/7 通过 |
| `WsPictureEditContractTest` ×2 | 心跳续约与洪泛预算的时序用例；`origin/main` 基线（临时 worktree，同一环境）同样失败这 2 条并多失败 2 条 | 与改动无关 |

即：除已修复的清单登记外，本机剩余失败全部可复现于未改动的基线或来自环境（Redis 版本、UTC 时区、前端依赖）。

## 剩余风险

1. **没有显式的任务版本或跨实例锁**。同一张图片的两次同步如果交错，后完成的一次可能覆盖先完成的一次；由于每次同步都重读数据库，最终会收敛，但短暂窗口里索引内容可能落后。失效素材不会被错误展示——查询侧复核是兜底。彻底解决需要给待办加版本号或在同步入口加图片级锁。
2. **待办表 SQL 依赖 MySQL 8.0.19+**（`INSERT ... AS incoming ON DUPLICATE KEY UPDATE` 行别名语法）。隔离验收跑在 8.0.26 上；更旧的 MySQL 上 `recordFailure` 会失败并只留日志（有 try/catch）。
3. **`fullSync` 不清理「已硬删除/历史遗留」的孤立文档**：清单只覆盖 `isDelete = 0` 的未过审与隔离图片。正常删除路径会移除索引，漏网文档由查询侧复核丢弃，不会被展示。
4. **每次检索多两次批量查询**（图片状态、隔离状态），按候选批处理，代价不随图库规模线性放大。
5. RAG 关闭时（默认配置）以上写侧逻辑整体不装配，行为与整改前一致。
6. **本机验收的既有坑**（与本次改动无关，但会干扰后续复核）：原生 Redis 实为 5.0，登录必失败；手工搭隔离环境必须设 `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC` 并每轮重建库、清 Redis；`WsPictureEditContractTest` 的心跳/洪泛用例在本机不稳定。
