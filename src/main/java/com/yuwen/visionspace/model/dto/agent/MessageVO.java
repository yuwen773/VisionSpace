package com.yuwen.visionspace.model.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 消息 VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageVO {
    /** 消息ID */
    private Long id;
    /** 角色 */
    private String role;
    /** 子类型 */
    private String subType;
    /** 消息内容 */
    private String content;
    /** 是否为摘要 */
    private boolean isSummary;
    /** 创建时间 */
    private Date createdTime;
}
