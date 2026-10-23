package com.yuwen.visionspace.agent.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.yuwen.visionspace.agent.manager.MCPManager;
import com.yuwen.visionspace.model.dto.mcp.*;
import com.yuwen.visionspace.model.entity.McpServer;
import com.yuwen.visionspace.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;

/**
 * 一次 MCP 工具调用的回调。
 *
 * <p>回调只保存「服务 code + 所属用户」，不保存实体快照：执行时按当前用户回读
 * 归属与启用状态，因此停用、删除、换归属之后，已缓存的回调不会再执行。
 * 模型给出的参数不能替代这里的授权上下文。
 */
@Slf4j
public class McpToolCallback implements ToolCallback {

    private final MCPManager mcpManager;
    private final McpServerAccess serverAccess;
    private final String mcpServerCode;
    private final Long userId;
    private final McpTool mcpTool;

    public McpToolCallback(MCPManager mcpManager, McpServerAccess serverAccess,
                           String mcpServerCode, Long userId, McpTool tool) {
        this.mcpManager = mcpManager;
        this.serverAccess = serverAccess;
        this.mcpServerCode = mcpServerCode;
        this.userId = userId;
        this.mcpTool = tool;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        String inputSchemaJson;
        try {
            inputSchemaJson = JsonUtils.toJson(mcpTool.getInputSchema());
        } catch (Exception e) {
            log.warn("Failed to serialize input schema", e);
            inputSchemaJson = "{}";
        }
        return ToolDefinition.builder()
            .name(mcpTool.getName())
            .description(mcpTool.getDescription())
            .inputSchema(inputSchemaJson)
            .build();
    }

    @Override
    public String call(String functionInput) {
        // 执行前回读：归属、启用状态任一不成立都不建立连接、不执行工具
        McpServer executable = serverAccess.requireExecutable(mcpServerCode, userId);

        Map<String, Object> params = parseInput(functionInput);

        McpServerCallToolRequest request = new McpServerCallToolRequest();
        request.setMcpServerCode(mcpServerCode);
        request.setToolName(mcpTool.getName());
        request.setToolParams(params);

        McpServerCallToolResponse response = mcpManager.callTool(request, executable);
        return formatResponse(response);
    }

    private Map<String, Object> parseInput(String functionInput) {
        if (functionInput == null || functionInput.isBlank()) {
            return Map.of();
        }
        try {
            return JsonUtils.fromJson(functionInput, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse mcp input");
            return Map.of();
        }
    }

    private String formatResponse(McpServerCallToolResponse response) {
        if (response.getContent() == null || response.getContent().isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        for (McpServerCallToolResponse.Content c : response.getContent()) {
            if (c instanceof McpServerCallToolResponse.TextContent textContent) {
                sb.append(textContent.getText());
            }
        }
        return sb.toString();
    }
}
