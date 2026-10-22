-- #34 图片归属与实际内容鉴权：资源记录表（回滚）
--
-- 仅删除本工单新增的表。picture 表与存量文件不受影响；回滚后应用须退回到
-- 不依赖 picture_file 的版本，否则内容入口会因缺少资源记录而拒绝读取。

DROP TABLE IF EXISTS `picture_file`;
