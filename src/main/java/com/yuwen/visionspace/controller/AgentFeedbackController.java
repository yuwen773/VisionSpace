package com.yuwen.visionspace.controller;

import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.model.dto.agent.FeedbackRequest;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 反馈接口
 */
@Slf4j
@RestController
@RequestMapping("/agent/image")
public class AgentFeedbackController {

    @Resource
    private com.yuwen.visionspace.agent.ImageAgent imageAgent;

    /**
     * 处理用户反馈
     */
    @PostMapping("/feedback")
    public BaseResponse<String> feedback(@RequestBody FeedbackRequest request) {
        log.info("收到反馈, threadId={}, satisfied={}, reason={}",
                request.getThreadId(), request.getSatisfied(), request.getReason());

        String result = imageAgent.handleFeedback(
                request.getThreadId(),
                request.getUserId(),
                request.getMessage(),
                request.getSatisfied(),
                request.getReason(),
                request.getAction()
        );

        return ResultUtils.success(result);
    }
}
