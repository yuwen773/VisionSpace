-- 回滚公开图库索引的待重试任务：删除待办表，索引同步本身继续可用。
-- 索引同步失败将只留日志、不再有持久待办；已写入向量库的文档不受影响。
-- 删除本表不影响图片、权限、空间额度与已审核状态。
DROP TABLE IF EXISTS `picture_index_task`;
SELECT 'picture_index_task dropped; index sync keeps working with log-only failures' AS rollbackAction;
