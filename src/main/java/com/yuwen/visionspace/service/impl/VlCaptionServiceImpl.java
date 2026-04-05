package com.yuwen.visionspace.service.impl;

import com.yuwen.visionspace.service.VlCaptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.core.io.UrlResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.MimeTypeUtils;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VlCaptionServiceImpl implements VlCaptionService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(VlCaptionServiceImpl.class);

    private final ChatModel chatModel;
    private final StringRedisTemplate redisTemplate;
    private final ChatClient chatClient;

    private static final String CACHE_PREFIX = "vl:caption:";
    private static final Duration CACHE_TTL = Duration.ofDays(7);
    private static final String VL_MODEL = "qwen3-vl-flash";
    private static final String PROMPT = """
            请对这张图片进行**结构化富描述**（中文），严格按以下格式输出：
            {
              "scene": "场景描述（一句话）",
              "ocr": "提取的所有可见文字，无则写\"无\"",
              "objects": ["关键对象1", "关键对象2"],
              "tags": ["标签1", "标签2", "标签3", "标签4", "标签5"],
              "description": "200字以内自然语言详细描述，适合用于语义检索"
            }
            请只返回 JSON，不要其他内容。
            """;

    @Override
    public String generateRichCaption(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            log.warn("imageUrl 为空，跳过 VlCaption 生成");
            return "{}";
        }

        String cacheKey = CACHE_PREFIX + DigestUtils.md5DigestAsHex(imageUrl.getBytes());

        // 1. 缓存检查
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("VlCaption 命中缓存, imageUrl={}", imageUrl);
                return cached;
            }
        } catch (Exception e) {
            log.warn("Redis 缓存读取失败，继续调用 AI, imageUrl={}", imageUrl, e);
        }

        // 2. 构建多模态消息
        Media media = new Media(MimeTypeUtils.IMAGE_JPEG, new UrlResource(imageUrl));
        UserMessage userMessage = new UserMessage(PROMPT, List.of(media));

        // 3. 调用轻量视觉模型
        try {
            String response = chatClient.prompt()
                    .messages(userMessage)
                    .call()
                    .chatResponse()
                    .orElseThrow(() -> new RuntimeException("AI 调用返回为空"))
                    .getResult()
                    .getOutput()
                    .getText();

            if (response == null || response.isBlank()) {
                log.warn("AI 返回为空, imageUrl={}", imageUrl);
                return "{}";
            }

            // 4. 缓存 7 天
            try {
                redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL);
            } catch (Exception e) {
                log.warn("Redis 缓存写入失败, imageUrl={}", imageUrl, e);
            }

            log.info("VlCaption 生成并缓存, imageUrl={}", imageUrl);
            return response;
        } catch (Exception e) {
            log.error("VlCaption 生成失败, imageUrl={}", imageUrl, e);
            return "{}";
        }
    }
}
