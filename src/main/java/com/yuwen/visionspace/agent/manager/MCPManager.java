package com.yuwen.visionspace.agent.manager;

import com.yuwen.visionspace.model.dto.mcp.*;
import com.yuwen.visionspace.model.entity.McpServerEntity;
import com.yuwen.visionspace.utils.JsonUtils;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MCPManager {

    private static final String TOOLS_CACHE_PREFIX = "mcp:tools:";
    private static final long TOOLS_CACHE_TTL_MINUTES = 5;

    private final StringRedisTemplate redisTemplate;
    private final ExecutorService mcpExecutor = Executors.newCachedThreadPool();

    public McpSyncClient getMcpSyncClient(McpServerEntity entity) {
        McpServerDeployConfig deployConfig = parseConfig(entity.getDeployConfig());

        String remoteEndpoint = deployConfig.getRemoteEndpoint();
        if (remoteEndpoint == null || remoteEndpoint.isBlank()) {
            remoteEndpoint = "/sse";
        }

        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(entity.getHost())
            .sseEndpoint(remoteEndpoint)
            .build();

        return McpClient.sync(transport)
            .requestTimeout(Duration.ofSeconds(60))
            .initializationTimeout(Duration.ofSeconds(60))
            .capabilities(McpSchema.ClientCapabilities.builder().roots(true).build())
            .build();
    }

    public List<McpTool> getTools(McpServerEntity entity) {
        if (entity == null || !entity.getStatus().equals(1)) {
            return Collections.emptyList();
        }

        String cacheKey = TOOLS_CACHE_PREFIX + entity.getUserId() + ":" + entity.getMcpServerCode();
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JsonUtils.fromJson(cached, new com.fasterxml.jackson.core.type.TypeReference<List<McpTool>>() {});
        }

        try {
            List<McpTool> tools = CompletableFuture.supplyAsync(() -> fetchTools(entity), mcpExecutor).get(30, TimeUnit.SECONDS);
            if (tools != null && !tools.isEmpty()) {
                redisTemplate.opsForValue().set(cacheKey, JsonUtils.toJson(tools), TOOLS_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            }
            return tools;
        } catch (Exception e) {
            log.error("Failed to get MCP tools for server: {}", entity.getMcpServerCode(), e);
            return Collections.emptyList();
        }
    }

    private List<McpTool> fetchTools(McpServerEntity entity) {
        McpSyncClient client = getMcpSyncClient(entity);
        try {
            client.initialize();
            McpSchema.ListToolsResult result = client.listTools();
            List<McpTool> tools = new ArrayList<>();

            for (McpSchema.Tool t : result.tools()) {
                McpTool tool = new McpTool();
                tool.setName(t.name());
                tool.setDescription(t.description());

                InputSchema schema = new InputSchema();
                schema.setType(t.inputSchema().type());
                schema.setProperties(t.inputSchema().properties());
                schema.setRequired(t.inputSchema().required());
                tool.setInputSchema(schema);

                tools.add(tool);
            }
            return tools;
        } finally {
            client.close();
        }
    }

    public McpServerCallToolResponse callTool(McpServerCallToolRequest request, McpServerEntity entity) {
        McpSyncClient client = getMcpSyncClient(entity);
        McpServerCallToolResponse response = new McpServerCallToolResponse();

        try {
            client.initialize();
            McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest(request.getToolName(), request.getToolParams())
            );

            response.setIsError(result.isError());

            List<McpServerCallToolResponse.Content> contents = new ArrayList<>();
            for (McpSchema.Content c : result.content()) {
                if ("text".equals(c.type())) {
                    McpServerCallToolResponse.TextContent text = new McpServerCallToolResponse.TextContent();
                    text.setType("text");
                    text.setText(((McpSchema.TextContent) c).text());
                    contents.add(text);
                }
            }
            response.setContent(contents);
        } catch (Exception e) {
            log.error("MCP tool call failed: {}", request.getToolName(), e);
            response.setIsError(true);
            McpServerCallToolResponse.TextContent text = new McpServerCallToolResponse.TextContent();
            text.setType("text");
            text.setText("Tool call failed: " + e.getMessage());
            response.setContent(List.of(text));
        } finally {
            client.close();
        }
        return response;
    }

    private McpServerDeployConfig parseConfig(String configJson) {
        return JsonUtils.fromJson(configJson, McpServerDeployConfig.class);
    }

    public void invalidateToolsCache(Long userId, String mcpServerCode) {
        String cacheKey = TOOLS_CACHE_PREFIX + userId + ":" + mcpServerCode;
        redisTemplate.delete(cacheKey);
    }
}