-- #41 回滚：删除本工单新增的两张表。
--
-- 只删除 agent_run_state / agent_action_record；不动 agent_session 及其
-- checkpointData 列，旧行为回滚后立即可用。
-- 回滚会丢失恢复点与动作台账，属于破坏性操作，须在维护窗口内执行并先备份。

DROP TABLE IF EXISTS `agent_action_record`;
DROP TABLE IF EXISTS `agent_run_state`;
