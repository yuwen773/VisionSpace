package com.yuwen.visionspace.agent.controller;

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
