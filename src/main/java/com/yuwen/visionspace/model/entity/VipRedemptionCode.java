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
     * 状态域：0-可用 1-已用 2-已作废（历史暴露码）。
     * 状态值由 SQL 字面量写入（见 VipRedemptionMapper.xml 与迁移脚本）；
     * 这里定义完整状态域供读取侧比对（status == STATUS_USED）与文档。
     */
    public static final int STATUS_AVAILABLE = 0;
    public static final int STATUS_USED = 1;
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
