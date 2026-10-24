package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * VIP 兑换码（只存摘要）
 * @TableName vip_redemption_code
 */
@TableName(value = "vip_redemption_code")
@Data
public class VipRedemptionCode implements Serializable {

    /**
     * 状态：可用
     */
    public static final int STATUS_AVAILABLE = 0;

    /**
     * 状态：已用
     */
    public static final int STATUS_USED = 1;

    /**
     * 状态：已作废（历史暴露码）
     */
    public static final int STATUS_VOIDED = 2;

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 兑换码摘要（sha-256），不保存明文
     */
    private String codeHash;

    /**
     * 状态: 0-可用 1-已用 2-已作废
     */
    private Integer status;

    /**
     * 核销用户
     */
    private Long usedBy;

    /**
     * 核销时间
     */
    private Date usedTime;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
