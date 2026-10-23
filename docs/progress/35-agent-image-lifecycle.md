# #35 Agent 图片完整生命周期

工单：[35](https://github.com/yuwen773/VisionSpace/issues/35)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26)。
分支：`codex/35-agent-image-lifecycle`。本文只记录本次实际执行到的证据，未执行的部分单列在「人工关卡」。

## 背景

SSE 结束时前端把本次上传的图片全部删掉，而消息里引用的地址留在历史里：历史图片必然失效。未发送的临时图片也没有回收路径，长期占用存储与用户额度。附件的归属判定只发生在上传那一刻，之后既没有「绑定」这层事实，也没有回收与重试的状态。

## 改动结构

| 位置 | 作用 |
| --- | --- |
| `agent_session_attachment` 表（`sql/migrations/2026-10-23-agent-session-attachment.sql`） | 会话与图片资源的绑定关系：会话标识、资源标识、所有者、状态（BOUND/RELEASED）、是否由存量迁移登记。唯一键 `uk_session_resource` 让重复提交幂等 |
| `agent_legacy_attachment` 表（同上脚本） | 存量会话的图片清单：会话、消息、所有者、地址摘要、确认后的资源标识、状态（待核对/已确认归属/已确认无归属）。只存 SHA2 摘要，不存原始地址 |
| `picture_file` 新增三列（同上脚本） | `deleteState` / `deleteRetryCount` / `deleteNextRetryTime`：对象存储删除未确认时保留可重试状态，不静默丢弃 |
| `agent_message` 新增 `attachmentIds`（同上脚本） | 消息携带哪些图片资源；历史按它渲染受控入口 |
| `AgentAttachmentService` | 附件的归属判定、绑定、读取、丢弃、删除、会话删除、回收与重试 |
| `AgentSessionAttachmentMapper` | 绑定的写入与查询；`insertIfAbsent` 用 `ON DUPLICATE KEY UPDATE` 让重复提交幂等，且已解除的绑定不会因为再次提交而复活 |
| `AgentAttachmentController` | `GET /agent/attachment/{resourceId}/content?sessionId=` 与 `POST /agent/attachment/{resourceId}/delete`：浏览器读附件与删附件的入口 |
| `AgentAttachmentCleanupJob` | 每小时回收未绑定的临时图片、重试未确认的删除 |
| `AgentController` | 对话与流式对话先判定附件归属再绑定；新增 `DELETE /agent/session/{threadId}` |
| `ImageAgent` | 消息正文按绑定后的可信地址拼 `<image-analysis>` 给模型；落库时存用户文字与资源标识，不落源站地址 |
| 前端 `useAgentStream` / `AgentChatPage` / `UserMessage` | 请求体带 `attachmentIds` 而不是地址；服务端接收后不再由前端删除图片；历史图片按受控入口渲染 |
| 前端 `src/api/`（`npm run openapi2ts` 重新生成） | 新增 `agentAttachmentController.ts` 与 `deleteSessionUsingDelete`；`attachmentIds` 在生成的类型里是 `string[]` |

## 关键设计

**绑定是唯一事实来源。** 「是否已绑定」只看 `agent_session_attachment`，不看 `picture_file.status`。绑定与回收并发时，双方都先拿 `picture_file` 的行锁（`SELECT ... FOR UPDATE`）再复核状态，判定与写入之间不留窗口；锁由数据库提供，跨应用实例有效。

**时间窗口一律用数据库时钟。** `createTime` 与 `deleteNextRetryTime` 都由数据库写入，保留期与重试到期也由数据库的 `NOW()` 推算。第一版把 JVM 算出的截止时间当参数传进 SQL，在 `serverTimezone=UTC` 的连接上与数据库写的 `createTime` 相差一个时区偏移，回收候选因此整体错位；改为数据库侧计算后这类偏差不再存在。

**资源标识按十进制字符串传输。** 标识是雪花值，超出 JavaScript 的安全整数范围：请求侧 `AgentChatRequest.attachmentIds` 与响应侧 `MessageVO.attachmentIds` 都声明为 `List<String>`，生成的客户端类型因此是 `string[]` 而不是 `number[]`。客户端转成 number 会丢精度，落到别的资源上。

**删除与访问分离。** 明确删除图片时先解除全部有效绑定（停止访问），再删除对象；对象删除未确认则保留可重试状态。被多个会话共享的图片，只有在没有任何有效引用时才清理对象。

**前端只删除服务端没接收的。** 流的 `onopen` 确认内容类型是 `text/event-stream` 之后才认为服务端已接收；只有没接收（拒绝、网络失败）时才丢弃尚未绑定的上传。服务端的丢弃入口也会拒绝已绑定的资源，两侧都拦得住。

## 验收对照

| 验收项 | 证据 | 状态 |
| --- | --- | --- |
| 13.1 临时资源仅能绑定到本人有权会话，消息与绑定重复提交幂等；实际附件读取经过会话和资源授权 | `HttpAgentAttachmentLifecycleTest#bindsOnlyToOwnSession`、`#repeatedBindingIsIdempotent`、`#contentRequiresSessionAndResourceAuthorization`、`#attachmentIdsAreAcceptedAsDecimalStrings`；绑定前 `AgentSessionGuard.requireOwned` 判定会话归属，读取时同时判定会话归属、绑定有效性与资源归属 | 已覆盖 |
| 13.2 SSE 成功、异常、取消及断开均不直接删除已绑定图片，刷新历史仍可访问有效图片 | 前端 `useAgentStream` 只在服务端未接收时删除上传；`useAgentStream.spec.ts` 三条用例（请求体带资源标识、成功后不删、拒绝时每张各删一次）；`#historyCarriesAttachmentIdsAndServesThemThroughTheControlledEntry` 验证刷新后历史按受控入口仍可访问 | 已覆盖（HTTP + 前端单测，无浏览器 E2E） |
| 13.3 未绑定临时图片默认二十四小时后回收，清理与绑定并发不误删；明确删除会话或图片后停止相应访问 | `#unboundTemporaryImagesAreReclaimedAfterTheRetentionWindow`（过期的是候选、未过期的不是）；`#boundImagesAreNeverReclaimed`；`#deletingTheSessionStopsAccessToItsImages`、`#deletingTheImageStopsAccessToIt`；并发安全由资源行锁 + 绑定状态复核保证 | 已覆盖 |
| 13.4 存储删除失败有可重试状态，共享有效引用不会被误清理；跨用户删除零副作用 | `#storageDeleteFailureKeepsARetryableState`（`deleteState=1`、重试次数、下次重试时间都在，重试成功后收敛为已删除）；`#deletingOneSessionKeepsImagesSharedWithAnotherSession`；`#crossUserDeleteHasNoSideEffects`（拒绝且零存储调用） | 已覆盖 |
| 13.5 保留旧会话图片清单并附带迁移、归属不明阻断和安全回滚方案 | `AgentAttachmentMigrationTest` 两条用例：前向迁移建表加列、回滚只移除本工单的对象；存量回填按归属确认建立绑定、归属不明登记为待核对，回滚撤销回填结果；`sql/migrations/2026-10-23-agent-session-attachment-blocklist.sql` 输出脱敏的待核对清单 | 已覆盖（隔离库） |

## 本次实际执行的验证

2026-10-23，Windows + JDK 21 / Maven 3.9 / Node 22 / PowerShell 7，原生隔离 MySQL 8.0.26 + Redis 3.2.100（本机 Docker 不可用，因此不是容器实测）：

```powershell
pwsh.exe -NoLogo -NoProfile -File ./scripts/acceptance.ps1 `
  -MySqlBin '<MySQL 8.0.26 bin>' -RedisBin '<Redis-x64-3.2.100>'
```

结果：九个结果分类全部 PASS，退出 0（隔离服务、后端测试与打包、前端安装/类型检查/测试/构建、资源清理）。这是修完下列缺陷之后的完整重跑。

- 合并 main（#38 合入）之后重跑：提交 `0965fc0`，九个分类全部 PASS。
- 后端 466 条测试全部通过（其中本工单新增 `HttpAgentAttachmentLifecycleTest` 13 条、`AgentAttachmentMigrationTest` 2 条）。
- 前端 18 个测试文件 157 条全部通过（其中本工单新增 `useAgentStream.spec.ts` 3 条）。

首次运行有 4 条失败，都是实现缺陷而不是断言写错，按下述方向修掉，未放宽断言：

1. **回收候选为空。** 判定把 JVM 算出的截止时间当参数传入，而 `createTime` 是数据库写的，UTC+8 环境下两者差一个时区偏移，25 小时前的资源看起来只有 17 小时。改为保留期以小时数交给数据库、用 `NOW()` 推算截止点；删除重试的到期判定同样改为数据库时钟。
2. **附件内容的缓存头重复。** `Cache-Control` 同时由 `CacheControl` 构造器和 `header(...)` 各写了一次，响应里出现两个值。改为只写 `no-store`。
3. **存量回填的字符集冲突。** `picture_file.url`（`utf8mb4_unicode_ci`）与 `agent_message.content`（`utf8mb4_general_ci`）比较时报 "Illegal mix of collations"。改为二进制比较。
4. **迁移脚本的作用域替换漏了字符串字面量。** 存储过程按表名字符串定位待改的表，只替换反引号形式会让 ALTER 打到真实的 `picture_file`。测试改为同时替换两种形式。

合并 main（#38 合入 `picture_action_event_dedupe`）时冲突三处：`scripts/acceptance.ps1` 与 `scripts/browser-acceptance.ps1` 的建表数守卫（12 → 15 → 17）与 `frontend/src/api/typings.d.ts` 的分页类型属性顺序（两侧字段相同，只是顺序不同）。建表数按合并后的 `sql/create_vision_space.sql` 实际统计取 17。

## 人工关卡（未完成，不计入通过）

以下事项本轮没有执行，不能表述为已验证：

1. **生产迁移与回滚演练。** `AgentAttachmentMigrationTest` 在隔离库的临时表名上验证脚本可执行且可回滚，不等于生产库（含数据量、锁、备份恢复）演练通过。存量回填在生产上需要维护窗口。
2. **待核对清单的人工核对。** 归属不明的存量图片只登记为待核对，脚本只输出脱敏摘要；逐条判定归属需要人工在维护窗口完成。
3. **双实例验收。** 13.3 的并发安全依赖数据库行锁，测试在单进程内验证，没有实际起两个实例交叉压测。
4. **浏览器端 E2E。** 前端只做了类型检查、单测与构建；历史图片按受控入口渲染是代码层改动，没有真实浏览器截图或交互证据。
5. **对象存储真实删除。** 存储为测试替身，真实桶上的删除失败、幂等与重试行为没有实测。

## 已知取舍

- **Agent 临时图片的 `url` 仍然返回给浏览器。** 该地址要同时交给服务端的模型与工具读取，模型侧不会携带用户 Cookie，因此不能像图库图片那样改成受控入口。历史图片的渲染已改走受控入口，但上传响应里的 `url` 仍存在。
- **绑定只由服务端建立。** 客户端提交的是资源标识，服务端按资源记录解析可信地址后绑定；客户端声明的地址不被信任。
- **回收只处理未绑定的临时图片。** 绑定过的图片保留到会话或图片被明确删除；会话被删除且没有其他引用时才清理对象。
- **删除重试的退避上限是二十四小时。** 超过上限后按上限重试，不无限拉长。
