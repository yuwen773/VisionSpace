# #55 个人候选素材板与持久化

规格：[VS-AI-02 frozen-v1.1](https://github.com/yuwen773/VisionSpace/issues/55)。本轮用户明确授权实现，确认 HTTP/MySQL/Redis 与 Vue/浏览器两层测试边界；历史发布阶段的 deferred-by-user 不代表本轮授权状态。
起点：`223ee44698504875c4e70bcdec9aee34078ad5ab`，已包含 #54；#35 前置工程记录已核对。生产放行保持独立。

## 实现

- `AssetSearchService` 收拢 search/readResult/readBoard/saveBoard/readOperation；Controller 和 Vue 不另设来源或授权旁路。
- POST `/agent/boards` 从本人有效快照创建，PUT `/agent/boards/{boardId}` 全量替换。新条目回传 resultId/sourcePosition/sourceVersion；已有条目回传 boardItemId/sourceVersion。position 表示板内排序，不与 sourcePosition 混用。
- 保存仅接受内部图库引用；来源事实与内容版本由持久快照/既有板解析，板内不保存永久文件 URL。已有来源独立保留，删除来源会话不删除板或图库图，也不重新开放该会话快照导入。
- 新增 `asset_board`、`asset_board_operation`。标题、组和有序条目作为有界聚合 JSON 持久化，同一事务替换并记录操作。owner 来自登录身份，space 创建后不可变；revision 从 1 开始，以 JSON 数字输出并限制在 JS 安全整数范围。
- `(ownerId, operationId)` 主键绑定类型、目标和规范化请求摘要；同内容只返回历史回执，当前板另行读取。proposalId 预留标识不能由普通保存消费，未新增 #56 提案能力。
- 同所有者操作使用数据库行锁串行裁决；板更新仍带 expectedRevision 条件。图片按数值 ID 排序锁定，然后锁定空间和成员资格；授权、来源与版本判定持有到提交，撤权/隔离/替换由数据库约束串行化。未使用 Redis 锁替代版本条件。
- 不可用项只投影 boardItemId、position、UNAVAILABLE；隐藏旧事实、引用、标题和分组正文。当前空间失权拒绝板及预览，只允许本人核对最小 COMMITTED/NOT_RECORDED 状态。
- 资源面板提供明确保存、选择/取消、移除、排序、分组、删组和 boardId 路由恢复。保存结果未知时保留原操作请求，NOT_RECORDED 只允许原标识重试；撤权后仅保留最小操作标识，账号切换则全部清空。
- 未保存内容只在当前页面；离开有丢失提示。切换 Agent 会话不丢弃独立板草稿。失权/隔离投影及重新核对会关闭已打开的预览；标题与组名使用纯文本。

## 证据入口

| 验收项 | 回归与证据 |
| --- | --- |
| AI02-01、09、10 | `HttpAssetBoardTest`：viewer 创建、外部身份/平台角色拒绝、当前成员角色、撤权回执最小化、隔离投影、旧 Cookie、CSRF；Vue 旧身份响应丢弃 |
| AI02-02、03、14 | HTTP 严格字段/顺序/名称/组和条目上限、重复图、取消选择与显式移除；Vue 删组保留条目、排序与稳定标识；录制存储断言无修改调用 |
| AI02-04 | HTTP 过期引用整体拒绝，原 revision 与操作状态不变；显式移除后空板可保存 |
| AI02-05、12、13 | HTTP 同操作重试、类型/目标冲突、提案保留标识拒绝；真实浏览器在后端提交后丢弃首次创建响应，再按 operationId 恢复未知 boardId |
| AI02-06、08 | `HttpAssetBoardDualInstanceTest` 启动独立 JVM，同键及同 revision 竞争，停止/重启实例后核对持久状态；浏览器刷新与重新登录 |
| AI02-07 | HTTP 临时 CHECK 约束注入操作记录写入失败；公开读取验证板聚合和成功回执一起回滚，同键可安全重试 |
| AI02-12、14 | HTTP 持有图片行锁，观察原保存真实阻塞 SQL，GET 返回 NOT_RECORDED，再观察同键重试阻塞 owner 锁；释放后仅一次提交。撤权用未提交成员 DELETE 和成员锁语句观察确定事务时序 |
| AI02-11、15 | `AssetBoardBrowserIT`：真实编辑、保存、冲突、刷新/重登、切换会话、打开预览后的隔离/恢复与撤权，存储录制仅允许 READ；HTTP 删除来源会话后已有板仍可读写 |

MySQL 在途观察使用相同隔离用户的 `SHOW FULL PROCESSLIST`，只匹配 fixture 的锁定 SQL，不增加权限，也不输出完整 SQL 进程清单。
浏览器使用生产构建、同源 HTTP 入口、真实隔离后端和固定 PNG。模型不参与保存正确性判定，存储为录制替身。
前端客户端由隔离后端导出的 OpenAPI 重新生成。新测试接入既有 acceptance 和 SRF 清单。

## 迁移、兼容和回退

在维护窗口备份并停写后执行 `sql/migrations/2026-11-08-personal-asset-board.sql`；仅新增两张表与索引，不强制转换旧聊天、图片、附件或配额。
脚本及回退说明登记在 maintenance-window.manifest.json；维护演练使用独立 scratch 表。

回退保留本版本的鉴权读取入口，将 `vision-space.asset-board.writes-enabled=false` 配置到全部实例并重启。新保存拒绝，已有操作回执仍能核对，readBoard/readOperation/鉴权预览继续可用。
两表及操作记录持久保留，无自动清理器；不删除原图，不重新开放旧直链，不退回缺失读取能力的旧应用。
独立 JVM 重启测试同时验证此停写模式的新请求拒绝、旧回执与读取可用。

## 验证状态

2026-11-08，代码提交 `0ecd4819a9be78c9f732723b34854dcda9c0242b` 的完整 `scripts/acceptance.ps1` 入口退出 0，14 项全部 PASS。

- 后端：701/701，失败、错误、跳过均为 0；包含 11 条本票 HTTP 测试和独立 JVM 启停/重启测试。
- 前端：26 个文件、201/201；lint、格式、类型检查和生产构建通过。
- 真实浏览器：2/2，失败、错误、跳过均为 0；分别保留 #54 找图和新增 #55 候选板闭环。
- 编排测试、秘密扫描、隔离、颜色标定、OpenAPI 生成对账与资源清理全部通过。
- 证据：`target/acceptance/3956e78e818a49e7a0f1c9e3f669e66b/summary.json`、同目录 `surefire/` 和 `browser-surefire/`；runId=`c5d6ae9013f1449aac66c68ce8e7ff57`。报告根副本为 `target/acceptance/summary.json`。
- 浏览器结果、请求路径记录和截图：`target/asset-board-browser/`，含保存、冲突恢复和撤权后的最终 DOM。

本轮为原生显式隔离 MySQL/Redis；存储是录制替身，图片是固定 PNG。不等同于 Docker、真实桶或生产验收；未执行远端 CI、推送、部署或关单。最终记录文档提交晚于代码验收提交，不把文档提交冒称已重新跑过后端。

已完成创建、替换、引用拒绝、预览、未知结果与两项读取恢复竞态的红→绿。首批完整运行因新增矩阵后旧测试按最后一项查人工关卡、旧资源面板测试缺少应用上下文而失败；已修复并保留原断言，以上最终独立批次不混用首批证据。

## Standards

首轮审查发现第二 JVM 使用 Windows 专属可执行名，以及一组引用拒绝测试缺少零调用断言；均已修复。独立审查代理复核 `223ee44...0ecd481`：0 项剩余发现，仅静态只读审查。

## Spec

首轮审查发现会话切换丢草稿、撤权丢待核对标识、个别失效项未关闭全局预览，以及在途事务证据不确定；均已修复并补回归。后续复核发现的恢复后按钮状态、旧读取覆盖也已完成红→绿。独立审查代理最终复核 `223ee44...0ecd481`：0 项剩余发现，仅静态只读审查。

双轴最终复核：Standards 0、Spec 0；运行证据与生产边界以上文验证状态为准。

真实对象存储、生产迁移/回滚、生产发布和 #56 Agent 提案：NOT_RUN。本轮不据此放行 #29/#48。
