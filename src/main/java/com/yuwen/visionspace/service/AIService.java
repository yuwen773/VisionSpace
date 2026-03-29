package com.yuwen.visionspace.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

/**
 * AI 服务（Spring AI 集成占位）
 */
@Slf4j
@Service
public class AIService {
    private final ChatModel chatModel;

    public AIService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 发送聊天消息
     * @param message 消息内容
     * @return AI 回复
     */
    public String chat(String message) {
        try {
            return chatModel.call(message);
        } catch (Exception e) {
            log.error("AI chat failed", e);
            return "AI 服务暂时不可用";
        }
    }
}
