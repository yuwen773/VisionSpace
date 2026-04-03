package com.yuwen.visionspace.model.dto.agent;

import java.util.List;

/**
 * 轻量级 Checkpoint 备份数据
 * 只存储必要的消息历史和摘要信息，避免序列化整个 Checkpoint 对象
 */
public record LightweightCheckpoint(
        /** 会话 ID */
        String threadId,
        /** 消息列表（简化版） */
        List<SimpleMessage> messages,
        /** 对话摘要内容 */
        String summaryContent
) {}
