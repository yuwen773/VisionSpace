-- =====================================================
-- 添加 picture.visibility 字段
-- =====================================================

-- MySQL 存储过程：仅当字段不存在时才添加
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

DELIMITER //
CREATE PROCEDURE add_column_if_not_exists()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'picture'
          AND COLUMN_NAME = 'visibility'
    ) THEN
        ALTER TABLE picture ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'public' COMMENT '可见性: public/private/team';
    END IF;
END //
DELIMITER ;

CALL add_column_if_not_exists();
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

-- 将已审核通过的图片设为 public
UPDATE picture SET visibility = 'public' WHERE reviewStatus = 1 AND visibility IS NULL;
