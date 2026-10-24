package com.yuwen.visionspace.service;

import com.yuwen.visionspace.model.entity.User;

/**
 * VIP 兑换核销服务（#39）。
 *
 * <p>核销、权益变更与流水写入在同一可靠事务内完成：
 * 同码跨实例只成功一次（数据库单语句原子更新），任何一步失败整体回滚——
 * 码不被吞掉，可以重试；重试不会重复加时（已用码返回明确状态）。
 */
public interface VipRedemptionService {

    /**
     * 原子核销一个兑换码并为用户延长十二个月会员。
     *
     * <p>到期时间从「当前时间」与「原到期时间」中较晚者起算；
     * 不触碰 {@code userRole}，VIP 资格独立于管理角色。
     *
     * @param user    服务端确认的当前登录用户
     * @param vipCode 用户提交的明文兑换码
     * @return success / already_used / invalid
     */
    String redeem(User user, String vipCode);
}
