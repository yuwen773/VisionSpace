package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * VIP 兑换流水
 * @TableName vip_redemption_record
 */
@TableName(value = "vip_redemption_record")
@Data
public class VipRedemptionRecord implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 兑换用户
     */
    private Long userId;

    /**
     * 兑换码记录 id
     */
    private Long codeId;

    /**
     * 兑换码摘要（sha-256），不保存明文
     */
    private String codeHash;

    /**
     * 兑换前到期时间
     */
    private Date previousExpireTime;

    /**
     * 兑换后到期时间
     */
    private Date expireTime;

    /**
     * 创建时间
     */
    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
