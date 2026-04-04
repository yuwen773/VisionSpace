package com.yuwen.visionspace.agent.tool;

import com.yuwen.visionspace.agent.manager.MCPManager;
import com.yuwen.visionspace.model.dto.mcp.*;
import com.yuwen.visionspace.model.entity.McpServerEntity;
import com.yuwen.visionspace.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.definition.ToolMetadata;
import org.springframework.ai.tool.support.AbstractToolCallback;

import java.util.Map;

@Slf4j
public class McpToolCallback extends AbstractToolCallback {

    private final MCPManager mcpManager;
    private final McpServerEntity mcpServerEntity;
    private final McpTool mcpTool;

    public McpToolCallback(MCPManager mcpManager, McpServerEntity entity, McpTool tool) {
        this.mcpManager = mcpManager;
        this.mcpServerEntity = entity;
        this.mcpTool = tool;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
            .name(mcpTool.getName())
            .description(mcpTool.getDescription())
            .inputSchema(JsonUtils.toJson(mcpTool.getInputSchema()))
            .build();
    }

    @Override
    public ToolMetadata getMetadata() {
        return ToolMetadata.builder().returnDirect(false).build();
    }

    @Override
    public String call(String functionInput) {
        Map<String, Object> params = parseInput(functionInput);

        McpServerCallToolRequest request = new McpServerCallToolRequest();
        request.setToolName(mcpTool.getName());
        request.setToolParams(params);

        McpServerCallToolResponse response = mcpManager.callTool(request, mcpServerEntity);
        return formatResponse(response);
    }

    private Map<String, Object> parseInput(String functionInput) {
        if (functionInput == null || functionInput.isBlank()) {
            return Map.of();
        }
        try {
            return JsonUtils.fromJsonToMap(functionInput);
        } catch (Exception e) {
            log.warn("Failed to parse tool input: {}", functionInput);
            return Map.of();
        }
    }

    private String formatResponse(McpServerCallToolResponse response) {
        if (response.getContent() == null || response.getContent().isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        for (Content c : response.getContent()) {
            if (c instanceof TextContent) {
                sb.append(((TextContent) c).getText());
            }
        }
        return sb.toString();
    }
}
