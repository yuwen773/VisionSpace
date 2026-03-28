package com.yuwen.visionspace.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

/**
 * AI 服务（Spring AI 集成占位）
 */
@Service
public class AIService {
    private final ChatModel chatModel;

    public AIService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String chat(String message) {
        return chatModel.call(message);
    }
}
