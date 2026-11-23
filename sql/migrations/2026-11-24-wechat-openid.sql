-- 微信小程序端（M1）：把微信身份与现有账号一对一绑定。
--
-- 现状：登录态只有「账号 + 密码」，user 表没有任何第三方身份列，
-- 小程序拿到的 openid 无处存放，也就无法在后续请求里认回同一个账号。
--
-- 设计取舍：
--   * 只存小程序 openid。个人主体小程序未绑定微信开放平台时拿不到 unionid，
--     多存一列只会给出「看起来支持多端统一身份」的错觉。
--   * 列名沿用本库 camelCase 约定（MyBatis-Plus 关闭了下划线转驼峰），
--     不能写成 wx_open_id，否则实体字段映射不到。
--   * 唯一索引保证「一个微信只绑一个账号」（一对一约束的落库形态）。
--     MySQL 唯一索引允许多行 NULL，因此未绑定用户与解绑后的用户不冲突。
--   * 解绑即把列置 NULL，不删行，历史绑定不保留——重新绑定由用户再次输入账号密码完成。
--
-- 脚本必须在「遇错即停」的客户端执行（mysql 命令行默认行为、JDBC 逐条 execute），
-- 不能加 --force。

SET NAMES utf8mb4;

ALTER TABLE `user`
  ADD COLUMN `wxOpenId` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL
    COMMENT '微信小程序 openid；与账号一对一，解绑置 NULL' AFTER `userRole`;

CREATE UNIQUE INDEX `uk_user_wxOpenId` ON `user`(`wxOpenId`) USING BTREE;
