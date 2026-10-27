package com.yuwen.visionspace.model.vo;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 会员兑换结果。成功、已用、无效三种状态由服务端明确表达，
 * 前端据此展示，不通过异常文案猜测。
 */
@Data
public class VipExchangeResult implements Serializable {

    private static final long serialVersionUID = 8735650154179439662L;

    /**
     * 兑换成功：权益已生效
     */
    public static final String STATUS_SUCCESS = "success";

    /**
     * 该码已被使用过
     */
    public static final String STATUS_ALREADY_USED = "already_used";

    /**
     * 兑换码不存在或已作废
     */
    public static final String STATUS_INVALID = "invalid";

    /**
     * 结果状态：success / already_used / invalid
     */
    private String status;

    /**
     * 兑换后的会员到期时间；仅 success 时有值
     */
    private Date vipExpireTime;

    public static VipExchangeResult success(Date vipExpireTime) {
        VipExchangeResult result = new VipExchangeResult();
        result.setStatus(STATUS_SUCCESS);
        result.setVipExpireTime(vipExpireTime);
        return result;
    }

    public static VipExchangeResult of(String status) {
        VipExchangeResult result = new VipExchangeResult();
        result.setStatus(status);
        return result;
    }
}
