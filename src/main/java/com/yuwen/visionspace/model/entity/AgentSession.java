package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Agent会话表
 * @TableName agent_session
 */
@TableName(value ="agent_session")
@Data
public class AgentSession implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 会话ID (threadId)
     */
    private String sessionId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 使用的模型
     */
    private String modelName;

    /**
     * 状态: 0-结束 1-进行中
     */
    private Integer status;

    /**
     * 最后一条消息摘要
     */
    private String lastMessage;

    /**
     * 消息条数
     */
    private Integer messageCount;

    /**
     * 对话摘要
     */
    private String summaryContent;

    /**
     * 最后一次总结时间
     */
    private Date summaryAt;

    /**
     * Checkpoint JSON 备份（gzip压缩后存储）
     */
    private byte[] checkpointData;

    /**
     * 创建时间
     */
    private Date createdTime;

    /**
     * 更新时间
     */
    private Date updatedTime;

    /**
     * 过期时间
     */
    private Date expiredTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
