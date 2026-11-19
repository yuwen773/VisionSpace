package com.yuwen.visionspace.config;

import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI Model configuration for Alibaba DashScope
 */
@Configuration
public class AiModelConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    @Bean
    public ImageSynthesis imageSynthesis() {
        return new ImageSynthesis();
    }
}
