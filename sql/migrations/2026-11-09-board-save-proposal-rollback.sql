-- 在所有实例配置 vision-space.board-proposal.writes-enabled=false 并重启。
-- 保留本版本的当前鉴权 readTask/取消/readBoard/readOperation；不得回退到缺少这些入口的应用。
-- 保留提案、操作和板记录；禁止重放终态、从旧 Checkpoint/聊天恢复确认或删除图库图片。
SELECT 'retain proposal/task and operation records; disable proposals and confirmations; allow authorized status and cancellation' AS rollbackAction;
