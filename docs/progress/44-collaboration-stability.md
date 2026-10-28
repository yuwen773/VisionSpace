# #44 双实例协作稳定性

工单：[44](https://github.com/yuwen773/VisionSpace/issues/44)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26)。
分支：`codex/44-collaboration-stability`。本文区分两段证据：2026-10-24 的隔离验收（基线 `513fb4f`）与 2026-10-28 集成 main 后的复核（基线 `8dd98e0`）。未执行的部分单列在「未验证范围」。

## 改动结构

| 位置 | 作用 |
| --- | --- |
| `PictureEditLeaseManager` | 租约存 Redis 并带 TTL，值编码 `sessionId\|userId`：绑定**具体连接**而不是用户，同一用户的多个标签页不会互相释放。获取 / 释放 / 续期 / 撤销各是一段 Lua 脚本，判定与写入原子完成，任何时刻只有一个有效持有者 |
| `PictureEditLeaseRenewJob` | 续期兜底：只对仍打开且确实持有租约的连接续期，续期前重新复核登录态与权限 |
| `WsEditAccessChecker` | 每条编辑消息与每次续期都复核：Redis HTTP 会话仍存活、握手身份未被替换、数据库当前用户存在、当前团队权限仍含编辑。不信任握手时缓存的快照 |
| `PictureEditBroadcastBus` | 跨实例广播：来源实例本地直发一次，订阅回放按来源实例 UUID 去重。编辑操作走 Lua「校验具体连接归属 + 发布」，避免「检查通过、发布时已易主」的窗口 |
| `PictureEditEventProducer` / `PictureEditEventWorkHandler` | 事件队列有界，满时拒绝并反馈；非法/空消息类型与单次消费异常只影响当前消息，后续合法消息继续处理 |
| `PictureEditMonitor` / `CollabMonitorVO` / `PictureCollabMonitorController` | 可观察面：接收数、队列余量、背压拒绝、非法消息、处理失败、撤权拒绝、租约冲突、广播发送/失败/拒绝与队列余量、本实例有效租约数。仅平台管理员可读 |
| `PictureEditHandler` | 消息与租约的主流程：租约冲突反馈、`leaseLost` 区分「普通错误」与「编辑权失效」、撤权/注销时收回租约并关闭连接、广播快照与实时消息共用会话锁 |
| `PictureEditWebSocket` / `ImageCropper.vue` | UI 以本连接与持有连接的 `sessionId` 判断编辑权；同用户其他标签的工具保持禁用；远端画布操作不再上行，避免回环 |
| `WsPictureEditContractTest` | 真实 TCP → 握手拦截器 → WebSocket → Disruptor → 租约（隔离 Redis）→ 广播的 13 条协议用例 |
| `scripts/collab-verification/` | 双实例协议脚本（两个真实后端进程 + 回环代理，21 项断言）与真实 Chromium 浏览器脚本（10 项断言），驱动实际裁剪界面 |

## 验收对照

| 验收项 | 证据 | 状态 |
| --- | --- | --- |
| 29.1 租约按连接绑定、跨实例共享、有期限及续期、竞争只产生一个持有者 | `WsPictureEditContractTest`（单实例语义）、`PictureEditLeaseManagerTest`、双实例脚本 21/21（两个真实进程同时请求只有一个赢家，在线续期超过原 TTL） | 已覆盖（2026-10-24 双实例 21/21；2026-10-28 复核未重跑双实例脚本） |
| 29.2 同用户关闭另一标签不释放当前租约；过期、断线、降权、注销、迟到事件不能继续写入或误释放新租约 | `WsPictureEditContractTest` 13 条：同用户不同连接、降权接管、注销即时停止写入（且零编辑广播）、空闲注销停止续期、HTTP 改登后旧连接拒绝、旧连接不能向新租约发布操作、快照与 EXIT 交错 | 已覆盖（单实例协议）；跨实例的迟到事件由双实例脚本覆盖 |
| 29.3 非法/空消息类型与单次异常隔离，后续合法消息仍能处理 | `WsPictureEditContractTest`：损坏 JSON、空类型、非法编辑动作之后合法消息仍被处理；`CollaborationBackpressureTest` 向真实 Disruptor 消费者注入单次异常后合法事件继续 | 已覆盖 |
| 29.4 队列与广播有明确有界背压、拒绝及失败策略，积压/异常/租约状态可观察 | `CollaborationBackpressureTest`（事件队列满时拒绝并可恢复）、`PictureEditBroadcastBusTest`（1024 广播队列满时丢弃并计数）、监控端点仅管理员可读 | 已覆盖（受控故障注入 + 真实队列） |
| 29.5 浏览器多连接与真实协议测试覆盖 UI 编辑权反馈、权限撤销和最终数据结果 | `scripts/collab-verification/collab-browser.ps1` 真实 Chromium 10/10：同用户第二标签禁用、普通错误保留编辑权、竞争错误可见、关闭另一标签后操作送达、空闲注销后工具禁用并可被接管；最终数据断言原图未被改写 | 已覆盖（2026-10-24）；2026-10-28 复核未重跑浏览器脚本 |

## 本次执行的验证

### 2026-10-24 隔离验收（基线 `513fb4f`）

| 检查 | 结果 |
| --- | --- |
| 原生隔离 MySQL 8.0.26 + Redis 3.2.100，`mvn clean verify` | PASS：475 tests，0 失败/错误/跳过 |
| 前端 type-check / Vitest / build-only | PASS：18 files、158 tests |
| 秘密扫描 / 验收结果分类测试 | PASS |
| 真实双实例 HTTP/WS 协议 | PASS：21/21，最终数据 `name=original`、`totalSize=0`、`totalCount=1`、`editor=viewer` |
| 真实 Chromium（HTTPS/WSS）+ 实际裁剪 UI | PASS：10/10，最终数据 original、用量 0/1 |
| Standards / Spec 两轴评审 | PASS：无剩余阻塞项 |

证据目录 `.vs44-runs/` 已在 `.gitignore` 中（本机留档，不入库）；结论以本表为准。

### 2026-10-28 集成 main 后的复核（基线 `8dd98e0`）

分支 rebase 到 `origin/main`，两个生成文件（`frontend/src/api/index.ts`、`typings.d.ts`）有冲突，按契约重新生成后解决。此后：

| 检查 | 结果 |
| --- | --- |
| 后端 `mvn verify`（原生隔离 MySQL/Redis） | PASS：545 tests，0 失败/错误/跳过；`WsPictureEditContractTest` 13 条全部通过 |
| 前端 type-check / Vitest / build-only / 契约对账 | PASS：20 files、180 tests；生成客户端与契约逐字节一致 |
| 前端 format | PASS |
| 前端 lint | 修复后单独执行 `eslint .` 退出码 0 |
| 完整门禁 | 单轮 11/12 PASS（仅 lint FAIL）；修正后**未取得**单一 12/12 的运行记录——第三次完整运行被本机内存压力中止，见下 |

**没有 12/12 的完整运行记录，这一点不粉饰。** 第三次运行被系统因内存不足中止（不是用例失败）；lint 的修复是在那次运行之外单独验证的。

## 复核中修掉的缺陷

1. **注销用例假设连接仍然打开。** 注销后服务端会收回租约并关闭 WebSocket，用例却继续往已关闭的 socket 发消息，`IOException: Output closed` 直接把用例打红。改为「收到 ERROR 拒绝」或「连接已关闭」都算预期，并补了一个更贴验收的断言：旁观连接在注销后 2 秒内**不得**收到任何 `EDIT_ACTION` 广播——29.2 要的是「不能继续写入」，不是「必须收到某条错误」。
2. **前端 8 处 `no-explicit-any`。** `pictureEditWebSocket.ts` 与协作单测从未过过 lint 关卡。改为导出 `PictureEditMessage` 接口 + `on<T>` 泛型回调，`ImageCropper` 五处回调补空值守卫。
3. **生成客户端漂移。** rebase 时手工合并的 `index.ts` / `typings.d.ts` 与契约不一致；契约关卡是逐字节比对，已用本轮 `target/openapi-contract.json` 重新生成。
4. **陈旧的 eslint 抑制条目。** `eslint-suppressions.json` 里 `pictureEditWebSocket.ts` 的 4 条抑制在 `any` 被替换后不再匹配任何问题，ESLint 以退出码 2 报错；`--prune-suppressions` 剪掉这 4 条（`ImageCropper.vue` 的 2 条仍然有效，保留）。
5. **脚本目录命名。** `scripts/tmp-44/` 更名为 `scripts/collab-verification/`，注释与测试 javadoc 里的旧路径一并更正。

## 未验证范围（诚实清单）

- **2026-10-28 复核没有重跑双实例脚本与浏览器脚本。** 29.1 的跨实例竞争、29.5 的浏览器 UI 证据仍是 2026-10-24 在 `513fb4f` 基线上的结果；rebase 之后这两项未重新取证。
- **没有单一 12/12 的完整门禁运行记录**（第三次运行被内存压力中止）。
- NOT_RUN：生产部署、Linux / Java 21 运行时、生产 Redis 版本与集群、真实公网代理与 TLS、网络分区与长期负载、真实对象存储中的裁剪上传与持久化。
- 广播是非持久 Redis Pub/Sub：队列超载的策略是丢弃并记录拒绝，不承诺断线重放或无损同步；租约仍由 Redis 权威判定。
- 前后端与两个后端实例应使用相同版本：消息新增连接标识、来源实例与 `leaseLost` 字段，混版本滚动运行未经验收。

## 已知取舍

- **租约绑连接，不绑用户。** 绑用户会让「关闭另一个标签」误放当前编辑权，这是 29.2 明确要避免的。
- **撤销与归属判断走 Lua。** 分两步读-判断-写会在中间被另一实例插入；把判定与操作合成一段脚本，过期与接管无法插进二者之间。
- **广播不持久。** 协作画布是尽力而为的实时同步，权威状态在数据库；需要无损协作日志属于另一个设计范围。
- **监控按实例观察。** 队列余量、有效租约数都是本实例视角，两个实例要分别看，不做跨实例聚合。
