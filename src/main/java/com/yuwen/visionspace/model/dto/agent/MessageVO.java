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
    /**
     * 消息携带的图片资源标识。
     *
     * <p>按十进制字符串给出：资源标识是雪花值，超出 JavaScript 的安全整数范围，
     * 客户端转成 number 会丢精度。
     */
    private java.util.List<String> attachmentIds;
    /** 是否为摘要 */
    private boolean isSummary;
    /** 创建时间 */
    private Date createdTime;
}
