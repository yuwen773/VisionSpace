package com.yuwen.visionspace.agent.manager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.agent.mcp.McpCapabilityGuard;
import com.yuwen.visionspace.agent.mcp.McpServerAccess;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.manager.outbound.OutboundPolicy;
import com.yuwen.visionspace.model.dto.mcp.*;
import com.yuwen.visionspace.model.entity.McpServer;
import com.yuwen.visionspace.utils.JsonUtils;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.customizer.McpAsyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * MCP 客户端与工具调用的执行层。
 *
 * <p>本类不做授权判定：传入的 {@link McpServer} 必须已由调用方按当前用户回读并确认
 * 可执行。即便如此，创建客户端前仍会复核危险能力（生产拒绝 STDIO）与出站目标，
 * 因为「创建客户端」本身就会发起连接或启动进程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MCPManager {

    private static final String TOOLS_CACHE_PREFIX = "mcp:tools:";
    private static final long TOOLS_CACHE_TTL_MINUTES = 5;

    private final StringRedisTemplate redisTemplate;
    private final McpCapabilityGuard capabilityGuard;
    private final OutboundPolicy outboundPolicy;
    private final ExecutorService mcpExecutor = Executors.newCachedThreadPool();

    /**
     * 创建 MCP 客户端（支持 SSE + Streamable HTTP）。
     *
     * <p>STDIO 在禁用环境下于此处被拒绝，拒绝发生在构造进程参数之前，
     * 因此不会启动任何进程；远程目标先过出站策略，再在每次请求时复核。
     */
    public McpSyncClient getMcpSyncClient(McpServer entity) {
        McpServerDeployConfig cfg = parseConfig(entity.getDeployConfig());
        String url = cfg.getUrl() != null ? cfg.getUrl() : entity.getHost();

        // 1. STDIO 类型：生产环境一律拒绝，且必须发生在任何进程创建之前。
        //    installType 可能被客户端声明成 SSE，因此还要按实际配置判定。
        if (isStdio(entity, cfg)) {
            capabilityGuard.assertStdioAllowed("MCPManager#getMcpSyncClient");
            return buildStdioClient(cfg);
        }

        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("MCP 服务必须提供 url 或 host！");
        }
        // 2. 远程目标：协议、主机、解析结果先过同一份出站策略
        outboundPolicy.requireAllowed(url);
        log.info("创建 MCP 客户端 - serverCode: {}, installType: {}",
                entity.getMcpServerCode(), entity.getInstallType());

        if (url.contains("mcp.draw.io") || url.endsWith("/mcp")) {
            return buildStreamableClient(url);
        }
        return buildSseClient(url);
    }

    /**
     * STDIO 判定同时看 installType 与部署配置里的 command。
     *
     * <p>判定本身在 {@link McpCapabilityGuard#isStdio} 里，配置保存与客户端创建
     * 共用同一份规则，避免两处结论不一致。
     */
    static boolean isStdio(McpServer entity, McpServerDeployConfig cfg) {
        return McpCapabilityGuard.isStdio(entity.getInstallType(), cfg.getCommand());
    }

    private McpSyncClient buildStdioClient(McpServerDeployConfig cfg) {
        ServerParameters serverParameters = ServerParameters.builder(cfg.getCommand())
            .args(cfg.getArgs())
            .env(cfg.getEnvValue())
            .build();

        StdioClientTransport transport = new StdioClientTransport(
            serverParameters,
            new JacksonMcpJsonMapper(new ObjectMapper())
        );

        return McpClient.sync(transport)
            .requestTimeout(Duration.ofSeconds(60))
            .initializationTimeout(Duration.ofSeconds(60))
            .capabilities(McpSchema.ClientCapabilities.builder().roots(true).build())
            .build();
    }

    private McpSyncClient buildStreamableClient(String url) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(url)
            .asyncHttpRequestCustomizer(outboundRequestCustomizer())
            .connectTimeout(Duration.ofSeconds(outboundPolicy.connectTimeoutSeconds()))
            .build();

        McpSyncClient client = McpClient.sync(transport)
            .requestTimeout(Duration.ofSeconds(30))
            .initializationTimeout(Duration.ofSeconds(30))
            .capabilities(McpSchema.ClientCapabilities.builder().roots(true).build())
            .build();

        // draw.io 需要先初始化 session
        client.initialize();
        return client;
    }

    private McpSyncClient buildSseClient(String url) {
        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(url)
            .asyncHttpRequestCustomizer(outboundRequestCustomizer())
            .connectTimeout(Duration.ofSeconds(outboundPolicy.connectTimeoutSeconds()))
            .build();

        return McpClient.sync(transport)
            .requestTimeout(Duration.ofSeconds(60))
            .initializationTimeout(Duration.ofSeconds(60))
            .capabilities(McpSchema.ClientCapabilities.builder().roots(true).build())
            .build();
    }

    /**
     * 每次 HTTP 请求（含重定向后的新地址、缓存客户端的重连）都重新判定目标。
     *
     * <p>只在创建客户端时校验一次是不够的：服务端可以把后续地址指向别处，
     * 已缓存的客户端也会在很久之后重连。这里对实际请求的 URI 再解析一次，
     * 命中受限范围就让请求失败，而不是发出去。
     */
    private McpAsyncHttpClientRequestCustomizer outboundRequestCustomizer() {
        return (requestBuilder, method, uri, body, context) -> {
            try {
                outboundPolicy.assertConnectTarget(uri);
            } catch (RuntimeException e) {
                return reactor.core.publisher.Mono.error(e);
            }
            return reactor.core.publisher.Mono.just(requestBuilder);
        };
    }

    /**
     * 获取工具列表。缓存按「用户 + 服务」隔离，且读取缓存前调用方已确认归属与启用状态。
     */
    public List<McpTool> getTools(McpServer entity) {
        if (entity == null || !Integer.valueOf(McpServerAccess.STATUS_ENABLED).equals(entity.getStatus())) {
            return Collections.emptyList();
        }

        String cacheKey = cacheKey(entity);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JsonUtils.fromJson(cached, new TypeReference<List<McpTool>>() {});
        }

        try {
            List<McpTool> tools = CompletableFuture
                    .supplyAsync(() -> fetchTools(entity), mcpExecutor)
                    .get(30, TimeUnit.SECONDS);
            if (tools != null && !tools.isEmpty()) {
                redisTemplate.opsForValue().set(cacheKey, JsonUtils.toJson(tools),
                        TOOLS_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            }
            return tools;
        } catch (ExecutionException e) {
            // 策略拒绝与能力禁用必须让调用方看到：吞掉会变成"这个服务没有工具"
            if (e.getCause() instanceof BusinessException business) {
                throw business;
            }
            log.error("Failed to get MCP tools for server: {}", entity.getMcpServerCode(), e);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get MCP tools for server: {}", entity.getMcpServerCode(), e);
            return Collections.emptyList();
        }
    }

    private List<McpTool> fetchTools(McpServer entity) {
        log.info("开始 fetchTools - serverCode: {}", entity.getMcpServerCode());
        try (McpSyncClient client = getMcpSyncClient(entity)) {
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
        }
    }

    /**
     * 执行工具调用。
     *
     * <p>入参 {@code entity} 必须来自执行前的回读（见 {@code McpServerAccess#requireExecutable}），
     * 本方法在建立连接前再复核一次危险能力与出站目标。
     */
    public McpServerCallToolResponse callTool(McpServerCallToolRequest request, McpServer entity) {
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
            // 异常信息可能带目标地址、请求头或服务端回显的凭据，只留在服务端日志
            log.error("MCP call failed: serverCode={}, tool={}", entity.getMcpServerCode(),
                    request.getToolName(), e);
            // 客户端异常 ≠ 服务明确失败：请求可能已送达并产生了外部副作用，
            // 只是本地没拿到结果。必须保留「结果未知」语义，不能与业务失败混同。
            response.setIsError(true);
            response.setOutcomeUnknown(true);
            McpServerCallToolResponse.TextContent text = new McpServerCallToolResponse.TextContent();
            text.setType("text");
            text.setText("MCP 工具调用失败，请稍后重试或检查服务配置");
            response.setContent(List.of(text));
        } finally {
            client.close();
        }
        return response;
    }

    private McpServerDeployConfig parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return new McpServerDeployConfig();
        }

        McpServerDeployConfig config = new McpServerDeployConfig();

        try {
            Map<String, Object> root = JsonUtils.fromJson(configJson, new TypeReference<Map<String, Object>>() {});

            if (root.containsKey("mcpServers")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> servers = (Map<String, Object>) root.get("mcpServers");

                if (servers != null && !servers.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> serverConfig = (Map<String, Object>) servers.values().iterator().next();

                    if (serverConfig.containsKey("url")) {
                        config.setUrl((String) serverConfig.get("url"));
                    }
                    if (serverConfig.containsKey("command")) {
                        config.setCommand((String) serverConfig.get("command"));
                    }
                    if (serverConfig.containsKey("args")) {
                        @SuppressWarnings("unchecked")
                        List<String> args = (List<String>) serverConfig.get("args");
                        config.setArgs(args);
                    }
                    if (serverConfig.containsKey("env")) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> env = (Map<String, String>) serverConfig.get("env");
                        config.setEnvValue(env);
                    }
                    if (serverConfig.containsKey("headers")) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> headers = (Map<String, String>) serverConfig.get("headers");
                        config.setRemoteHeader(headers);
                    }
                }
            }
        } catch (Exception e) {
            // 配置内容可能含凭据，日志只记录服务标识
            log.warn("解析 deployConfig 失败: 配置不是合法 JSON");
        }

        return config;
    }

    public void invalidateToolsCache(Long userId, String mcpServerCode) {
        redisTemplate.delete(TOOLS_CACHE_PREFIX + userId + ":" + mcpServerCode);
    }

    private String cacheKey(McpServer entity) {
        // 归属维度必须在键里：不同用户配置同名 code 时不能共享工具定义
        return TOOLS_CACHE_PREFIX + entity.getUserId() + ":" + entity.getMcpServerCode();
    }
}
