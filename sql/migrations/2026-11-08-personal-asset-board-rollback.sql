-- #55：配置 vision-space.asset-board.writes-enabled=false 后重启全部实例，停用新保存。
-- 必须保留支持当前鉴权的 readBoard/readOperation/预览实现，不退回缺少这些入口的旧版本。
-- 保留两表及全部记录，不删除原图，不恢复旧直链，不清理操作标识；不转换历史聊天。
SELECT 'asset_board and asset_board_operation retained; disable new board writes; retain authorized reads' AS rollbackAction;
