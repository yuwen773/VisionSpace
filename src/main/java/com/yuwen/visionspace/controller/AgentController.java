package com.yuwen.visionspace.controller;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.agent.config.AgentConstants;
import com.yuwen.visionspace.agent.ImageAgent;
import com.yuwen.visionspace.agent.model.AgentPhase;
import com.yuwen.visionspace.agent.recovery.AgentRecoveryService;
import com.yuwen.visionspace.agent.service.AgentAttachmentService;
import com.yuwen.visionspace.agent.service.AgentSessionGuard;
import com.yuwen.visionspace.agent.service.ChatHistoryService;
import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.model.dto.agent.ActionReviewRequest;
import com.yuwen.visionspace.model.dto.agent.AgentChatRequest;
import com.yuwen.visionspace.model.dto.agent.ChatHistoryVO;
import com.yuwen.visionspace.model.dto.agent.RecoveryStateVO;
import com.yuwen.visionspace.model.dto.agent.FeedbackRequest;
import com.yuwen.visionspace.model.dto.agent.MessageDTO;
import com.yuwen.visionspace.model.dto.agent.SessionVO;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private ImageAgent imageAgent;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private AgentSessionGuard sessionGuard;

    @Resource
    private AgentRecoveryService agentRecoveryService;

    @Resource
    private AgentConstants agentConstants;

    @Resource
    private UserService userService;

    @Resource
    private AgentAttachmentService agentAttachmentService;

    /**
     * 图片助手对话
     */
    @PostMapping("/image/chat")
    public BaseResponse<String> chat(@RequestBody AgentChatRequest request,
                                      HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        Long userId = loginUser.getId();
        String threadId = resolveThreadId(request.getThreadId());

        // 附件归属先判定：拒绝时不创建会话、不产生绑定、不进入模型
        List<Long> resourceIds = agentAttachmentService.resolveOwnedIds(
                request.getAttachmentIds(), request.getImageUrls(), userId);
        // 归属在 ImageAgent 进入模型前判定；会话不属于当前用户时这里就拒绝
        sessionGuard.bindOwner(threadId, userId, request.getMessage());
        // 绑定到会话：重复提交同一批资源标识是幂等的
        List<AgentAttachmentService.BoundAttachment> attachments =
                agentAttachmentService.bind(threadId, userId, resourceIds);

        log.info("Agent 对话请求, threadId={}, userId={}", threadId, userId);

        // 使用 invokeAndGetOutput 支持中断；归属在 ImageAgent 进入模型前判定
        Optional<NodeOutput> result = imageAgent.invokeAndGetOutput(request.getMessage(), threadId, userId, attachments);

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
    public BaseResponse<String> feedback(@RequestBody FeedbackRequest request,
                                          HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        String threadId = resolveThreadId(request.getThreadId());

        log.info("收到反馈, threadId={}, userId={}, satisfied={}, reason={}, phase={}",
                threadId, loginUser.getId(), request.getSatisfied(),
                request.getReason(), request.getCurrentPhase());

        // 归属在 handleFeedback 进入模型前核对；不属于当前用户时不写偏好、不执行
        String result = imageAgent.handleFeedback(
                threadId,
                loginUser.getId(),
                request.getSatisfied(),
                request.getReason(),
                request.getAction(),
                request.getCurrentPhase() != null ? request.getCurrentPhase() : AgentPhase.EXPLORATION
        );

        return ResultUtils.success(result);
    }

    /**
     * 流式对话接口
     *
     * <p>归属与登录校验在建立流之前完成。拒绝时直接写 JSON 错误体，而不是抛给
     * 全局异常处理器：本方法的 {@code Accept} 只有 {@code text/event-stream}，
     * 异常处理器协商不出 JSON，会把拒绝变成 500/406，客户端读不到原因。
     */
    @PostMapping("/image/chat/stream")
    public ResponseEntity<?> chatStream(@RequestBody AgentChatRequest request,
                                        HttpServletRequest httpRequest) {
        Long userId;
        String threadId;
        List<AgentAttachmentService.BoundAttachment> attachments;
        try {
            // 未登录的流式请求不建立连接：userId 只来自服务端会话
            User loginUser = userService.getLoginUser(httpRequest);
            userId = loginUser.getId();
            threadId = resolveThreadId(request.getThreadId());
            // 附件归属先判定：拒绝的请求不会建立会话、不会产生绑定
            List<Long> resourceIds = agentAttachmentService.resolveOwnedIds(
                    request.getAttachmentIds(), request.getImageUrls(), userId);
            // 归属判定发生在返回 Flux 之前：拒绝的请求不会建立流、不会插入消息、不会调用模型
            sessionGuard.bindOwner(threadId, userId, request.getMessage());
            // 绑定到会话：图片此后保留到会话或图片被明确删除，流结束不删除
            attachments = agentAttachmentService.bind(threadId, userId, resourceIds);
        } catch (BusinessException e) {
            return writeJsonError(e);
        }

        log.info("Agent 流式对话请求, threadId={}, userId={}, enabledMcpServers={}, disableMcpDefault={}",
                threadId, userId, request.getEnabledMcpServers(), request.getDisableMcpDefault());

        Flux<ServerSentEvent<String>> stream =
                imageAgent.stream(request.getMessage(), attachments, threadId, userId,
                request.getEnabledMcpServers(), request.getDisableMcpDefault())
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
                    // 只返回通用错误信息，不泄露内部错误细节
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data("系统繁忙，请稍后重试")
                            .build());
                });

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(stream);
    }

    /**
     * 在 SSE 端点上写一个普通 JSON 错误体。
     *
     * <p>响应类型由这里显式指定，不参与内容协商；客户端按 Content-Type
     * 判断这是拒绝而不是流。业务错误码沿用全站约定（HTTP 200 + code）。
     */
    private ResponseEntity<BaseResponse<?>> writeJsonError(BusinessException e) {
        log.warn("拒绝流式对话请求, code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ResultUtils.error(e.getCode(), e.getMessage()));
    }

    /**
     * 对象转 JSON
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
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

    /**
     * 获取聊天历史（分页）
     * @param threadId 会话ID
     * @param beforeId 游标（可选，用于分页）
     * @param limit 每页数量（默认40，最大100）
     */
    @GetMapping("/history/{threadId}")
    public BaseResponse<ChatHistoryVO> getHistory(
            @PathVariable String threadId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "40") int limit,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        // 归属不成立时拒绝；不区分「不存在」与「属于他人」
        sessionGuard.requireOwned(threadId, loginUser.getId());
        return ResultUtils.success(chatHistoryService.loadHistory(threadId, beforeId, Math.min(limit, agentConstants.getMaxHistoryPageSize())));
    }

    /**
     * 查询会话的恢复状态。
     *
     * <p>归属不成立时与历史接口一样拒绝，且不区分「不存在」与「属于他人」。
     */
    @GetMapping("/recovery/{threadId}")
    public BaseResponse<RecoveryStateVO> getRecoveryState(
            @PathVariable String threadId,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(agentRecoveryService.describe(threadId, loginUser.getId()));
    }

    /**
     * 续跑会话。
     *
     * <p>只在存在可安全续跑的完整状态、且没有待核对动作时执行；
     * 否则返回明确原因并保持暂停，不用消息历史伪造恢复。
     */
    @PostMapping("/recovery/{threadId}/resume")
    public BaseResponse<String> resume(
            @PathVariable String threadId,
            @RequestParam(required = false) Long expectedVersion,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        Long userId = loginUser.getId();

        // 上次执行遗留的「执行中」动作先收敛，再判断能不能续跑
        agentRecoveryService.reconcileBeforeRun(threadId, userId);
        AgentRecoveryService.ResumeClaim claim =
                agentRecoveryService.requireResumable(threadId, userId, expectedVersion);

        // 认领到的租约必须释放：否则用户要等到租约到期才能再次续跑
        try {
            return ResultUtils.success(imageAgent.resume(threadId, userId));
        } finally {
            agentRecoveryService.releaseResumeLease(threadId, userId, claim.leaseToken());
        }
    }

    /**
     * 人工核对：确认外部动作已经发生。
     */
    @PostMapping("/recovery/action/confirm")
    public BaseResponse<RecoveryStateVO> confirmAction(@RequestBody ActionReviewRequest request,
                                                       HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(agentRecoveryService.confirmAction(
                resolveThreadId(request.getThreadId()), loginUser.getId(),
                request.getActionId(), request.getNote()));
    }

    /**
     * 人工核对：确认外部动作没有发生（或接受不再执行）。
     */
    @PostMapping("/recovery/action/dismiss")
    public BaseResponse<RecoveryStateVO> dismissAction(@RequestBody ActionReviewRequest request,
                                                       HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(agentRecoveryService.dismissAction(
                resolveThreadId(request.getThreadId()), loginUser.getId(),
                request.getActionId(), request.getNote()));
    }

    /**
     * 获取用户的会话列表（分页）
     * @param beforeId 游标（可选，用于分页）
     * @param limit 每页数量（默认20，最大100）
     */
    @GetMapping("/sessions")
    public BaseResponse<List<SessionVO>> getSessions(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(chatHistoryService.getUserSessions(loginUser.getId(), beforeId, Math.min(limit, 100)));
    }

    /**
     * 删除会话。
     *
     * <p>会话被删除后历史与附件都不再可访问：先解除该会话对图片的引用，
     * 再清理没有其他会话引用的图片对象；被别的会话共享的图片保留。
     *
     * @return 受影响的图片数量
     */
    @DeleteMapping("/session/{threadId}")
    public BaseResponse<Integer> deleteSession(@PathVariable String threadId,
                                               HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        // 归属不成立时拒绝；不区分「不存在」与「属于他人」
        return ResultUtils.success(agentAttachmentService.deleteSession(threadId, loginUser.getId()));
    }

    /**
     * 客户端未提供会话标识时生成一个；客户端提供的标识在进入任何执行前由
     * {@link AgentSessionGuard} 校验格式与归属，这里只做格式清洗。
     */
    private String resolveThreadId(String requested) {
        if (requested == null || requested.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requested.trim();
    }
}
