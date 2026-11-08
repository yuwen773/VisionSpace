-- #54 回退只停用新检索入口，保留表、快照与历史引用，不修改图片或开放私人直链。
-- 本脚本不执行 DROP、DELETE、权限恢复或数据回填。
SELECT 'asset_search_snapshot retained; disable asset-search entry' AS rollbackAction;
