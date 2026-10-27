package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Agent 会话附件绑定。
 *
 * <p>一条记录表示「某个会话引用了某个图片资源」。绑定建立后图片保留到会话或图片
 * 被明确删除；未绑定的临时图片按保留期回收。
 *
 * <p>本表不使用全局 {@code isDelete} 逻辑删除：绑定有「已绑定 / 已解除」两种
 * 状态，解除后仍需保留记录以说明这个引用曾经存在。
 */
@Data
@TableName("agent_session_attachment")
public class AgentSessionAttachment implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 会话标识 */
    private String sessionId;

    /** picture_file 资源标识 */
    private Long resourceId;

    /** 附件所有者用户 id */
    private Long ownerUserId;

    /** {@link com.yuwen.visionspace.model.enums.AgentAttachmentStatusEnum} */
    private Integer status;

    /** 1=由存量迁移登记 */
    private Integer legacy;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
