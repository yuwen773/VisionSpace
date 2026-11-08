package com.yuwen.visionspace.agent.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuwen.visionspace.agent.service.AssetSearchService;
import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.model.dto.agent.AssetSearchRequest;
import com.yuwen.visionspace.model.vo.AssetSearchResult;
import com.yuwen.visionspace.service.UserService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/agent")
public class AssetSearchController {
    @Resource private AssetSearchService assets;
    @Resource private UserService users;

    @PostMapping("/asset-search")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = AssetSearchRequest.class)))
    public BaseResponse<AssetSearchResult> searchAssets(@RequestBody JsonNode body, HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var viewer = users.getLoginUser(request);
        return ResultUtils.success(assets.search(AssetSearchRequest.parse(body), viewer));
    }

    @GetMapping("/asset-results/{resultId}")
    public BaseResponse<AssetSearchResult> readAssetResult(@PathVariable String resultId, HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ResultUtils.success(assets.readResult(resultId, users.getLoginUser(request)));
    }

    @GetMapping("/asset-results/{resultId}/items/{position}/content")
    public ResponseEntity<byte[]> assetPreview(@PathVariable String resultId, @PathVariable int position, HttpServletRequest request) {
        try {
            var content = assets.loadPreview(resultId, position, users.getLoginUser(request));
            return ResponseEntity.ok().cacheControl(CacheControl.noStore().mustRevalidate())
                    .header("X-Content-Type-Options", "nosniff")
                    .contentType(MediaType.parseMediaType(content.contentType())).body(content.bytes());
        } catch (BusinessException e) {
            return ResponseEntity.status(e.getCode() == 40100 ? 401 : 403).cacheControl(CacheControl.noStore()).build();
        }
    }
}
