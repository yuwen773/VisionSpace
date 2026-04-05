package com.yuwen.visionspace.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map.Entry;
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
        if (!StringUtils.hasText(detail.getDeployConfig())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "deployConfig is required");
        }
        // 验证 JSON 格式
        ObjectNode configNode;
        try {
            configNode = (ObjectNode) objectMapper.readTree(detail.getDeployConfig());
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "deployConfig is not valid JSON");
        }
        // 如果 name 为空，尝试从 deployConfig 中提取
        if (!StringUtils.hasText(detail.getName())) {
            String extractedName = extractNameFromConfig(configNode);
            if (!StringUtils.hasText(extractedName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "name is required");
            }
            detail.setName(extractedName);
        }
    }

    private String extractNameFromConfig(ObjectNode configNode) {
        // 尝试从 mcpServers.{server}.name 提取
        JsonNode mcpServers = configNode.get("mcpServers");
        if (mcpServers != null && mcpServers.isObject()) {
            Iterator<Entry<String, JsonNode>> fields = mcpServers.fields();
            if (fields.hasNext()) {
                Entry<String, JsonNode> firstServer = fields.next();
                JsonNode nameNode = firstServer.getValue().get("name");
                if (nameNode != null && nameNode.isTextual() && !nameNode.asText().isBlank()) {
                    return nameNode.asText();
                }
                // 如果没有 name 字段，返回 server key 作为名称
                if (!firstServer.getKey().isBlank()) {
                    return firstServer.getKey();
                }
            }
        }
        // 尝试从顶层 name 字段提取
        JsonNode nameNode = configNode.get("name");
        if (nameNode != null && nameNode.isTextual() && !nameNode.asText().isBlank()) {
            return nameNode.asText();
        }
        return "";
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
