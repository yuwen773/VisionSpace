package com.yuwen.visionspace.agent.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.yuwen.visionspace.agent.manager.MCPManager;
import com.yuwen.visionspace.model.dto.mcp.*;
import com.yuwen.visionspace.model.entity.McpServer;
import com.yuwen.visionspace.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;

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
        return settle(response);
    }

    /**
     * 把 MCP 响应的语义原样带到异常边界，不在文本里丢失。
     *
     * <p>响应文本会被框架折成一条普通工具结果，动作台账只会看到「工具有返回」；
     * 「结果未知」与「明确失败」必须以异常形式区分抛出，否则超时的非幂等调用
     * 会被记成成功：
     * <ul>
     *   <li>结果未知（传输超时/响应丢失）：包成 {@code ToolExecutionException}
     *       抛出，由 {@code AgentToolExceptionPolicy} 判定——非幂等工具重抛进台账
     *       进入待核对，幂等只读工具转回文本；</li>
     *   <li>明确业务失败（服务应答 isError）：普通异常抛出，框架折成 error 响应，
     *       台账记为可安全重试的失败。</li>
     * </ul>
     */
    private String settle(McpServerCallToolResponse response) {
        if (Boolean.TRUE.equals(response.getOutcomeUnknown())) {
            // 服务端文本可能是通用兜底文案：错误细节只留在服务端日志，不外带
            log.warn("MCP 工具结果未知, serverCode={}, tool={}", mcpServerCode, mcpTool.getName());
            throw new ToolExecutionException(getToolDefinition(),
                    new McpToolOutcomeUnknownException("MCP 工具执行结果未知"));
        }
        if (Boolean.TRUE.equals(response.getIsError())) {
            throw new McpToolBusinessException(safeErrorText(response));
        }
        return formatResponse(response);
    }

    /** 服务报错文本回给模型参考；为空时给固定文案 */
    private String safeErrorText(McpServerCallToolResponse response) {
        String text = formatResponse(response);
        return text == null || text.isBlank()
                ? "MCP 工具执行失败" : "MCP 工具执行失败: " + text;
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
