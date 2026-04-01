package com.yuwen.visionspace.controller;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.agent.ImageAgent;
import com.yuwen.visionspace.agent.model.AgentPhase;
import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.model.dto.agent.AgentChatRequest;
import com.yuwen.visionspace.model.dto.agent.FeedbackRequest;
import com.yuwen.visionspace.model.dto.agent.MessageDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * AI Agent 接口
 */
@Slf4j
@RestController
@RequestMapping("/agent")
public class AgentController {

    @Resource
    private ImageAgent imageAgent;

    /**
     * 图片助手对话
     */
    @PostMapping("/image/chat")
    public BaseResponse<String> chat(@RequestBody AgentChatRequest request) {
        String threadId = request.getThreadId();
        if (threadId == null || threadId.isBlank()) {
            threadId = UUID.randomUUID().toString();
        }

        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        log.info("Agent 对话请求, threadId={}, message={}", threadId, request.getMessage());

        // 使用 invokeAndGetOutput 支持中断
        Optional<NodeOutput> result = imageAgent.invokeAndGetOutput(request.getMessage(), config);

        // 检查是否返回了中断
        if (result.isPresent() && result.get() instanceof InterruptionMetadata) {
            InterruptionMetadata interruption = (InterruptionMetadata) result.get();
            return ResultUtils.success(buildInterruptionResponse(interruption));
        }

        // 正常返回
        if (result.isPresent()) {
            return ResultUtils.success(result.get().toString());
        }

        return ResultUtils.success("处理完成");
    }

    /**
     * 处理用户反馈
     */
    @PostMapping("/image/feedback")
    public BaseResponse<String> feedback(@RequestBody FeedbackRequest request) {
        log.info("收到反馈, threadId={}, satisfied={}, reason={}, phase={}",
                request.getThreadId(), request.getSatisfied(),
                request.getReason(), request.getCurrentPhase());

        String result = imageAgent.handleFeedback(
                request.getThreadId(),
                request.getUserId(),
                request.getSatisfied(),
                request.getReason(),
                request.getAction(),
                request.getCurrentPhase() != null ? request.getCurrentPhase() : AgentPhase.EXPLORATION
        );

        return ResultUtils.success(result);
    }

    /**
     * 流式对话接口
     */
    @PostMapping(value = "/image/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody AgentChatRequest request) {
        String threadId = request.getThreadId();
        if (threadId == null || threadId.isBlank()) {
            threadId = UUID.randomUUID().toString();
        }

        log.info("Agent 流式对话请求, threadId={}, message={}", threadId, request.getMessage());

        return imageAgent.stream(request.getMessage(), threadId)
                .filter(Objects::nonNull)
                .map(data -> ServerSentEvent.<String>builder()
                        .data(toJson(data))
                        .build())
                .concatWith(Mono.just(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build()
                ))
                .onErrorResume(error -> {
                    log.error("流式对话错误", error);
                    String errorMessage = error.getMessage() != null ? error.getMessage() : "Unknown error";
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data(errorMessage)
                            .build());
                });
    }

    /**
     * 对象转 JSON
     */
    private String toJson(Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败", e);
            return "{}";
        }
    }

    private String buildInterruptionResponse(InterruptionMetadata interruption) {
        StringBuilder sb = new StringBuilder();
        sb.append("【需要确认】\n\n");

        for (InterruptionMetadata.ToolFeedback feedback : interruption.toolFeedbacks()) {
            sb.append("工具: ").append(feedback.getName()).append("\n");
            sb.append("评估结果: ").append(feedback.getDescription()).append("\n");
        }

        sb.append("\n请选择是否满意上述结果，或提供改进建议。");
        return sb.toString();
    }
}
