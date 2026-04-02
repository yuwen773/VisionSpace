package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Agent消息记录表
 * @TableName agent_message
 */
@TableName(value = "agent_message")
@Data
public class AgentMessage implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 角色: USER / ASSISTANT
     */
    private String role;

    /**
     * 子类型: text / reasoning / tool-call / tool-result / tool-confirm（USER 为 NULL）
     */
    private String subType;

    /**
     * 消息内容
     */
    private String content;

    /**
     * Token数量
     */
    private Integer tokenCount;

    /**
     * 0=原始 1=摘要
     */
    private Integer isSummary;

    /**
     * 创建时间
     */
    private Date createdTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
