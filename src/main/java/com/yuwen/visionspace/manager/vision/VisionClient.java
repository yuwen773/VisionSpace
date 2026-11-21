package com.yuwen.visionspace.manager.vision;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 视觉识别客户端：端点、模型与凭据只在这里读一次。
 *
 * <p>主对话模型是纯文本模型，读图要走同一套 {@code vision-space.vision-recognition}
 * 配置。此前「图片描述」与「图片特征分析」各自构建一份完全相同的
 * {@link OpenAiChatModel}，配置键、温度、凭据散在两处；改端点要记得改两个文件。
 *
 * <p>这里给出的是不带系统提示的客户端：调用方各自带自己的提示词，
 * 客户端本身不预设业务语义。
 */
@Component
public class VisionClient {

    private static final double DEFAULT_TEMPERATURE = 0.2;

    @Value("${vision-space.vision-recognition.base-url}")
    private String baseUrl;

    @Value("${vision-space.vision-recognition.api-key}")
    private String apiKey;

    @Value("${vision-space.vision-recognition.model}")
    private String model;

    private OpenAiChatModel chatModel;

    @PostConstruct
    void init() {
        OpenAiApi visionApi = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(DEFAULT_TEMPERATURE)
                .build();
        this.chatModel = OpenAiChatModel.builder()
                .openAiApi(visionApi)
                .defaultOptions(options)
                .build();
    }

    /** 每次返回新的 {@link ChatClient} 包装：客户端本身无状态，系统提示由调用方提供。 */
    public ChatClient chatClient() {
        return ChatClient.builder(chatModel).build();
    }
}
