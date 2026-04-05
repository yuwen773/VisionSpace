package com.yuwen.visionspace.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.agent.manager.MCPManager;
import com.yuwen.visionspace.model.dto.mcp.*;
import com.yuwen.visionspace.model.entity.McpServerEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;

@Slf4j
public class McpToolCallback implements ToolCallback {

    private final ObjectMapper objectMapper = new ObjectMapper();
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
        String inputSchemaJson;
        try {
            inputSchemaJson = objectMapper.writeValueAsString(mcpTool.getInputSchema());
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
        Map<String, Object> params = parseInput(functionInput);

        McpServerCallToolRequest request = new McpServerCallToolRequest();
        request.setMcpServerCode(mcpServerEntity.getMcpServerCode());
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
            return objectMapper.readValue(functionInput, new TypeReference<Map<String, Object>>() {});
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
        for (McpServerCallToolResponse.Content c : response.getContent()) {
            if (c instanceof McpServerCallToolResponse.TextContent textContent) {
                sb.append(textContent.getText());
            }
        }
        return sb.toString();
    }
}
