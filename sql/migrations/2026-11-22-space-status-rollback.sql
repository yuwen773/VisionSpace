-- 管理端空间状态列的回滚说明：不删除 `space.status`。
-- 旧版本应用不读取该列，删列不会让旧版本更安全，只会丢掉「哪些空间曾被禁用」的唯一记录。
-- 回滚放行前必须人工确认 status=0 的空间不会因旧版本缺少禁用语义而重新开放；
-- 无法确认时保持维护/受限状态，不得回滚到不认识该列的版本。
SELECT 'retain space.status; confirm no status=0 space reopens under the previous app version before rollback' AS rollbackAction;
