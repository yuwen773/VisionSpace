package com.yuwen.visionspace.model.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 会话列表项 VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SessionVO {
    /** 会话ID */
    private String sessionId;
    /** 会话标题 */
    private String title;
    /** 最后一条消息 */
    private String lastMessage;
    /** 消息数量 */
    private Integer messageCount;
    /** 摘要内容 */
    private String summaryContent;
    /*** 创建时间*/
    private Date createdTime;
    /*** 更新时间*/
    private Date updatedTime;
}