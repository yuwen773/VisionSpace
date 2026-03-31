package com.yuwen.visionspace.controller;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.yuwen.visionspace.agent.ImageAgent;
import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.model.dto.agent.AgentChatRequest;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
