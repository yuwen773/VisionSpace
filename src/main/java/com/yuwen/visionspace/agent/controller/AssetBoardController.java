package com.yuwen.visionspace.agent.controller;

import com.yuwen.visionspace.model.dto.agent.BoardProposalRequest;
import com.yuwen.visionspace.model.vo.BoardProposalTask;
import com.yuwen.visionspace.agent.BoardProposalAgent;
import com.fasterxml.jackson.databind.JsonNode;
import com.yuwen.visionspace.agent.service.AssetSearchService;
import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.model.dto.agent.SaveAssetBoardRequest;
import com.yuwen.visionspace.model.vo.AssetBoard;
import com.yuwen.visionspace.service.UserService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/agent")
public class AssetBoardController {
    @Resource private AssetSearchService assets;
    @Resource private UserService users;
    @Resource private BoardProposalAgent planner;

    @PostMapping("/board-proposals/plan")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = BoardProposalRequest.Plan.class)))
    public BaseResponse<BoardProposalTask> planSave(@RequestBody JsonNode body, HttpServletRequest request) {
        var viewer = users.getLoginUser(request);
        return ResultUtils.success(planner.plan(BoardProposalRequest.Plan.parse(body), viewer));
    }

    @PostMapping("/board-proposals")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = BoardProposalRequest.Input.class)))
    public BaseResponse<BoardProposalTask> proposeSave(@RequestBody JsonNode body, HttpServletRequest request) {
        var viewer = users.getLoginUser(request);
        return ResultUtils.success(assets.proposeSave(BoardProposalRequest.parse(body), viewer));
    }

    @GetMapping("/tasks/{taskId}")
    public BaseResponse<BoardProposalTask> readTask(@PathVariable String taskId, HttpServletRequest request) {
        return ResultUtils.success(assets.readTask(taskId, users.getLoginUser(request)));
    }

    /** 通知只引用已提交事实；丢失通知由 readTask 恢复，不重放保存。 */
    @GetMapping(value = "/tasks/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<reactor.core.publisher.Flux<org.springframework.http.codec.ServerSentEvent<Object>>> taskEvents(
            @PathVariable String taskId, HttpServletRequest request) {
        if (!"1".equals(request.getHeader("X-VS-AI-Event-Version"))) throw new BusinessException(ErrorCode.PARAMS_ERROR, "INVALID_REQUEST");
        var task = assets.readTask(taskId, users.getLoginUser(request));
        java.util.List<org.springframework.http.codec.ServerSentEvent<Object>> events = new java.util.ArrayList<>();
        if (task.target() != null && "AWAITING_CONFIRMATION".equals(task.status())) {
            var payload = java.util.Map.of("taskId", task.taskId(), "proposalId", task.proposalId(), "proposalRevision", 1,
                    "boardId", task.boardId(), "expectedRevision", java.math.BigInteger.valueOf(task.expectedRevision()));
            events.add(org.springframework.http.codec.ServerSentEvent.builder((Object) java.util.Map.of("schemaVersion", 1,
                    "eventId", "proposal:" + task.proposalId(), "type", "CONFIRMATION_REQUIRED", "payload", payload)).build());
        } else if (task.target() != null && "COMMITTED".equals(task.status())) {
            for (String type : java.util.List.of("BOARD_UPDATED", "OPERATION_COMMITTED")) {
                events.add(org.springframework.http.codec.ServerSentEvent.builder((Object) java.util.Map.of("schemaVersion", 1,
                        "eventId", type + ":" + task.operationId(), "type", type, "payload",
                        java.util.Map.of("operationId", task.operationId(), "boardId", task.boardId(), "revision", java.math.BigInteger.valueOf(task.revision())))).build());
            }
        }
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(reactor.core.publisher.Flux.fromIterable(events));
    }

    @PostMapping("/board-proposals/{proposalId}/decision")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = BoardProposalRequest.Decision.class)))
    public BaseResponse<BoardProposalTask> decideProposal(@PathVariable String proposalId, @RequestBody JsonNode body, HttpServletRequest request) {
        var viewer = users.getLoginUser(request);
        return ResultUtils.success(assets.decideProposal(proposalId, BoardProposalRequest.Decision.parse(body), viewer));
    }

    @ModelAttribute
    public void noCache(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

    @PostMapping("/boards")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = SaveAssetBoardRequest.class)))
    public BaseResponse<AssetBoard.Operation> createBoard(@RequestBody JsonNode body, HttpServletRequest request) {
        var viewer = users.getLoginUser(request);
        try { return ResultUtils.success(assets.saveBoard(null, SaveAssetBoardRequest.parse(body, false), viewer)); }
        catch (DataAccessException e) { throw new BusinessException(ErrorCode.SYSTEM_ERROR, "SAVE_RESULT_UNKNOWN"); }
    }

    @GetMapping("/boards/{boardId}")
    public BaseResponse<AssetBoard> readBoard(@PathVariable String boardId, HttpServletRequest request) {
        return ResultUtils.success(assets.readBoard(boardId, users.getLoginUser(request)));
    }

    @PutMapping("/boards/{boardId}")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = SaveAssetBoardRequest.class)))
    public BaseResponse<AssetBoard.Operation> replaceBoard(@PathVariable String boardId, @RequestBody JsonNode body, HttpServletRequest request) {
        var viewer = users.getLoginUser(request);
        try { return ResultUtils.success(assets.saveBoard(boardId, SaveAssetBoardRequest.parse(body, true), viewer)); }
        catch (DataAccessException e) { throw new BusinessException(ErrorCode.SYSTEM_ERROR, "SAVE_RESULT_UNKNOWN"); }
    }

    @GetMapping("/board-operations/{operationId}")
    public BaseResponse<AssetBoard.Operation> readBoardOperation(@PathVariable String operationId, HttpServletRequest request) {
        return ResultUtils.success(assets.readOperation(operationId, users.getLoginUser(request)));
    }

    @GetMapping("/boards/{boardId}/items/{itemId}/content")
    public ResponseEntity<byte[]> boardPreview(@PathVariable String boardId, @PathVariable String itemId, HttpServletRequest request) {
        try {
            var content = assets.loadBoardPreview(boardId, itemId, users.getLoginUser(request));
            return ResponseEntity.ok().cacheControl(CacheControl.noStore().mustRevalidate())
                    .header("X-Content-Type-Options", "nosniff")
                    .contentType(MediaType.parseMediaType(content.contentType())).body(content.bytes());
        } catch (BusinessException e) {
            return ResponseEntity.status(e.getCode() == 40100 ? 401 : 403).cacheControl(CacheControl.noStore()).build();
        }
    }
}
