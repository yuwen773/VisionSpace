package com.yuwen.visionspace.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.agent.service.McpServerService;
import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.model.dto.mcp.McpServerDetail;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * MCP Server Controller
 */
@Slf4j
@RestController
@RequestMapping("/mcp-server")
public class McpServerController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private McpServerService mcpServerService;

    @Resource
    private UserService userService;

    @PostMapping
    public BaseResponse<String> createMcpServer(@RequestBody McpServerDetail detail, HttpServletRequest request) {
        validateCreateParams(detail);
        Long userId = getLoginUserId(request);
        String serverCode = mcpServerService.createMcp(detail, userId);
        return ResultUtils.success(serverCode);
    }

    @PutMapping
    public BaseResponse<Void> updateMcpServer(@RequestBody McpServerDetail detail, HttpServletRequest request) {
        validateUpdateParams(detail);
        Long userId = getLoginUserId(request);
        mcpServerService.updateMcp(detail, userId);
        return ResultUtils.success(null);
    }

    @DeleteMapping("/{mcpServerCode}")
    public BaseResponse<Void> deleteMcpServer(@PathVariable String mcpServerCode, HttpServletRequest request) {
        Long userId = getLoginUserId(request);
        mcpServerService.deleteMcp(mcpServerCode, userId);
        return ResultUtils.success(null);
    }

    @GetMapping("/{mcpServerCode}")
    public BaseResponse<McpServerDetail> getMcpServer(
            @PathVariable String mcpServerCode,
            @RequestParam(value = "needTools", defaultValue = "false") boolean needTools,
            HttpServletRequest request) {
        Long userId = getLoginUserId(request);
        McpServerDetail detail = mcpServerService.getMcp(mcpServerCode, userId, needTools);
        return ResultUtils.success(detail);
    }

    @GetMapping
    public BaseResponse<IPage<McpServerDetail>> listMcpServers(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") int current,
            @RequestParam(value = "size", defaultValue = "10") int size,
            HttpServletRequest request) {
        Long userId = getLoginUserId(request);
        IPage<McpServerDetail> page = mcpServerService.listMcpServers(userId, name, current, size);
        return ResultUtils.success(page);
    }

    private void validateCreateParams(McpServerDetail detail) {
        if (detail == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (!StringUtils.hasText(detail.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "name is required");
        }
        if (!StringUtils.hasText(detail.getDeployConfig())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "deployConfig is required");
        }
        // 验证 JSON 格式
        try {
            objectMapper.readTree(detail.getDeployConfig());
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "deployConfig is not valid JSON");
        }
    }

    private void validateUpdateParams(McpServerDetail detail) {
        validateCreateParams(detail);
        if (!StringUtils.hasText(detail.getMcpServerCode())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "mcpServerCode is required");
        }
    }

    private Long getLoginUserId(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return loginUser.getId();
    }
}
