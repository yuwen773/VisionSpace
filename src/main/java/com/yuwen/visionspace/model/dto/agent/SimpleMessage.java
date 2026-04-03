package com.yuwen.visionspace.model.dto.agent;

/**
 * 简单的消息记录，用于 Checkpoint 轻量备份
 * 不使用 Spring AI 的 Message 对象，避免序列化风险
 */
public record SimpleMessage(
        /** 角色: USER / ASSISTANT / TOOL / SYSTEM */
        String role,
        /** 消息内容 */
        String content
) {}
