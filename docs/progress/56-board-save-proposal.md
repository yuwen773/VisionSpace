# #56 Agent 驱动的候选板确认保存

规格：[VS-AI-03 frozen-v1.1](https://github.com/yuwen773/VisionSpace/issues/56)。用户本轮授权实现、三层测试、双轴审查与本地提交。历史发布阶段的 deferred-by-user 不代表本轮授权状态；本轮不推送、关单或部署。

基线：`ddde8f8ce720242bb63c5c275f7ef237754ad6ed`，包含已关闭 #55/#33/#41 的实现与工程证据。#54 真实素材与人工颜色标签关卡仍独立。

## 行为与持久状态

- `AssetSearchService` 增加 proposeSave/decideProposal/readTask；提案与普通保存使用同一来源校验及板保存实现。只改选图、顺序、分组，不能通过提案改板标题或图片。
- 新表 `asset_board_proposal` 保存可信 owner、会话/板/空间绑定、稳定请求键、规范化目标及摘要、基线、唯一任务/提案/操作标识和权威终态；proposalRevision 固定为 1。目标不可变，新目标原子替代同 owner/board/thread 的旧未决提案。旧终态不覆盖。
- `(ownerId, proposalRequestId)` 幂等；同键不同内容拒绝。规划入口额外绑定原始规划请求摘要，重试不再次调用模型。operationId 由服务端生成，并在 #55 操作表预留；普通 saveBoard 无法消费预留标识。
- owner 数据库锁串行裁决双实例请求；同一事务完成决定、共用板保存、操作回执和 COMMITTED。板版本/来源变化进入 CONFLICT，目标删除/隔离/失权进入 REJECTED；普通事务异常全部回滚，保持 AWAITING_CONFIRMATION。
- 决定先校验当前登录、会话/归属、严格形状、proposalRevision 与 operationId，再判断终态；重复同决定返回既有结果，相反决定返回既有终态与 INVALID_OPERATION_STATE。历史提交不会因后来的资源变化被改写。
- 撤权或不可用引用使完整投影隐藏；本人有效会话仍能读最小任务状态并取消。取消不要求目标恢复，不产生板写入。删除会话后读取、决定和规划均拒绝，独立板与图库图保留。

## Agent、界面与事件

- POST `/agent/board-proposals/plan` 是受限规划适配器；只接受稳定请求标识、会话、已有板、expectedRevision 和整理要求。模型只返回当前板的选图/顺序/分组，不指定身份、来源版本、操作标识或决定；无工具 callbacks、工具名或内部工具执行。
- 新增 HTTP 接口遵循工单：POST `/agent/board-proposals`、GET `/agent/tasks/{taskId}`、POST `/agent/board-proposals/{proposalId}/decision`。Controller 从服务端登录态取身份，沿用 CSRF/错误/无缓存策略；客户端从隔离后端 OpenAPI 重新生成。
- 资源面板展示当前/目标的选择、位置和分组对照、来源与提案版本及“只保存个人草稿”的效果；纯文本渲染组名。只点击显式决定按钮才保存/取消，聊天文本和停止展示不构成决定。
- taskId 进入路由，规划成功后绑定已有会话；刷新、重登按权威接口恢复。未知决定结果先核对，复用原 operationId。账号切换丢弃旧响应与私人内容；失权只保留合法的最小核对/取消。
- GET `/agent/tasks/{taskId}/events` 是有限只读通知适配器，协商 v1 后按当前权限投影已持久化事实；返回 CONFIRMATION_REQUIRED 或 BOARD_UPDATED/OPERATION_COMMITTED。无权时不推送目标。版本保持 JSON 数字，不受全局 Long ID 字符串序列化影响。
- 前端兼容并归一 tool-confirm/mcp-confirm，旧无提案确认只读。结构化通知去重并忽略旧板版本；未知协议不执行动作。#41 外部 NEEDS_REVIEW 继续通过既有恢复界面/受限核对机制，本期板任务不存在该转移。
- 板任务不进入外部 ImageAgent Checkpoint 执行器。恢复只读本表/原回执，旧 Checkpoint 或迟到会话备份不能修改任务终态，不另建外部执行账本。

## 验收映射

| 条目 | 可观察证据 |
| --- | --- |
| AI03-01、02、04 | `HttpBoardProposalAgentTest`：受控模型只有规划调用，恶意输出/工具/身份字段拒绝，原板不变；HTTP 外部身份拒绝零出站；浏览器真实差异与纯文本 |
| AI03-03、06、16 | `HttpBoardProposalTest`：确认只加一次，保留图片/配额与零存储副作用；预留操作不能从普通保存使用；历史回执不重写终态；双实例同请求/同确认竞争 |
| AI03-05、15 | 固定请求键绑定、同键不同目标零变化、旧 proposalRevision 拒绝、新提案替代及新提案/旧确认并发、来源替换/元数据变化冲突 |
| AI03-07 | `HttpBoardProposalDualInstanceTest`：两个独立 JVM 的确认/取消竞争只有一个终态；重复与相反决定不会再次保存 |
| AI03-08 | HTTP 最终任务 UPDATE 的真实 MySQL CHECK 故障使板、回执和任务一起回滚；持图片锁观察在途原确认与重试真实阻塞 SQL，NOT_RECORDED 不能绕过裁决；浏览器提交后丢响应、通知丢失后读取 |
| AI03-09、10、14 | `BoardProposalBrowserIT`：刷新、重登、通知断开、隔离隐藏及显式取消；HTTP 空间失权后最小状态与取消、失权确认 REJECTED；第二 JVM 重启恢复 |
| AI03-11 | Vue 旧事件归一、重复/乱序通知和账号切换旧响应回归；浏览器历史任务引用恢复；HTTP 事件无私人正文及版本数值契约 |
| AI03-12、17 | 板任务状态限制；全套继承 #41 外部未知结果回归。规划、提案、决定通过当前登录/CSRF/会话/资源边界，旧 Cookie 注销重放拒绝；无工具/存储变更 |
| AI03-18 | 第二 JVM 以停写配置重启：拒绝创建/确认，保留读取/取消；晚到 Checkpoint 数据不能改写 CANCELLED，已删除会话全部拒绝；板仍有权可读 |

上述受控故障覆盖不能冒称真实模型执行、实际硬崩溃时序或生产回滚证据。#41 原有故障回归与本期独立任务的数据库恢复分列，不推断外部副作用恰好一次。

## INT-01～10 集成报告

| 场景 | 工程证据及边界 |
| --- | --- |
| 01 | 继承 `HttpAssetSearchTest` 权限/会话/零出站矩阵，加本期跨用户任务与规划拒绝；完整套件覆盖 |
| 02 | 继承可信宽高硬条件与 UNKNOWN 回归，不以模型补事实 |
| 03 | 继承版本化蓝紫 OR、合成标定/排序回归；人工真实颜色标签仍 NOT_RUN，颜色功能门控不变 |
| 04 | 继承 #54/#55 当前授权内容入口；本期浏览器固定 PNG 经真实内容接口读取/隔离 |
| 05 | 同 resultId 的聊天/面板快照保持原契约；新提案消息只保存 taskId，通知不抽 Markdown 图 |
| 06 | 共用板事务与录制存储断言，只改变个人整理草稿，不改图片、配额或对象 |
| 07 | #55 完整回归，加本期浏览器刷新/重登与第二 JVM 重启 |
| 08 | 单/双 JVM 同请求和决定的幂等、板版本与新旧提案竞争 |
| 09 | 当前权限与不可用投影、最小任务状态、取消、注销/CSRF 和跨用户拒绝；继承 #36 单资源授权边界 |
| 10 | 取消/断流不删图，取消/确认竞争唯一、终态不重放；继承 #35 生命周期和 #41 外部待核对回归，不新增外部工具 |

工程映射不放行真实产品效果或生产；INT-03 人工素材关卡、真实模型、真实桶和生产迁移/部署均为 NOT_RUN。

## 迁移与回退

维护窗口备份并停写后执行 `sql/migrations/2026-11-09-board-save-proposal.sql`；只新增表与索引，无旧聊天授权推断。正向和停写回退说明已登记 maintenance-window.manifest.json；隔离演练只操作 scratch 表。

回退在全部实例配置 `vision-space.board-proposal.writes-enabled=false` 并重启。保留本版本的当前鉴权读状态、取消、readBoard/readOperation；保留提案/操作/板，不删除图片，不回退到缺少这些安全能力的旧应用。开关拒绝新提案/新确认，既有终态与取消仍可核对。生产执行独立授权与验证。

## 本轮验证

已完成公共接口的红→绿：显式确认闭环、来源替换冲突、可确认状态与旧事件归一、受限 Agent、Vue 差异与取消；真实浏览器曾检出并修复新会话标识被清空、路由切换时旧表单输入被丢弃。

最近一批针对性入口：14 条提案 HTTP、1 条真实浏览器、1 条 OpenAPI 导出，16/16，失败/错误/跳过均为 0。另有受控 Agent 2 条、独立第二 JVM 竞争/重启 1 条、维护演练 12 条及 manifest 7 条通过；最终以同候选完整验收结果为准。

完整 `scripts/acceptance.ps1` 与 Standards/Spec 双轴审查：待本次候选提交后执行。以上为隔离 MySQL/Redis、受控模型与录制存储证据，无真实模型或生产外发。
