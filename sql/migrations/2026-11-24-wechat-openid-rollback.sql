-- 微信小程序 openid 列的回滚说明：不删除 `user.wxOpenId`。
--
-- 旧版本应用不读取该列，删列不会让旧版本更安全，只会丢掉「哪个微信绑定了哪个账号」
-- 的唯一记录，导致所有已绑定用户在小程序侧被静默降级为「未绑定」。
-- 回滚应用版本时保留列与唯一索引即可；唯一索引对旧版本无副作用。
--
-- 只有在确认「所有绑定数据都不再需要」时，才手工执行下面两行（不在本脚本里自动执行）：
--   DROP INDEX `uk_user_wxOpenId` ON `user`;
--   ALTER TABLE `user` DROP COLUMN `wxOpenId`;
SELECT 'retain user.wxOpenId; dropping it silently unbinds every mini program user' AS rollbackAction;
