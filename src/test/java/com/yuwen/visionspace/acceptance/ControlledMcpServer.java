package com.yuwen.visionspace.acceptance;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 受控的远程 MCP 替身，只监听回环地址。
 *
 * <p>实现 MCP 0.14 客户端实际使用的 SSE 传输：{@code GET /sse} 建立事件流并下发
 * endpoint 事件；客户端随后向 {@code POST /message} 发送 JSON-RPC，响应经事件流
 * 以 {@code event: message} 送回。它记录每一次请求，测试据此断言
 * 「拒绝后零连接、零工具执行」，不接触任何真实内网、元数据或第三方 MCP 服务。
 */
public final class ControlledMcpServer implements AutoCloseable {

    private static final String TOOL_NAME = "echo";

    /** 一个最小的合法 PNG，供 URL 上传路径使用。 */
    private static final byte[] IMAGE_BYTES = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private final HttpServer server;
    private final ExecutorService executor;
    private final AtomicInteger toolCalls = new AtomicInteger();
    private final List<String> rawRequests = new CopyOnWriteArrayList<>();
    private final AtomicReference<OutputStream> eventStream = new AtomicReference<>();
    private final String toolText;

    public ControlledMcpServer() throws IOException {
        this("controlled-mcp-result");
    }

    public ControlledMcpServer(String toolText) throws IOException {
        this.toolText = toolText;
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/sse", this::handleSse);
        server.createContext("/message", this::handleMessage);
        // 受控图片目标：用于验证正常 URL 上传路径，不连接任何外部地址
        server.createContext("/photo.png", exchange -> {
            rawRequests.add("GET " + exchange.getRequestURI().getPath());
            byte[] content = IMAGE_BYTES;
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
            exchange.close();
        });
        // 默认执行器是单线程：事件流会长期占用，POST 必须能并行处理
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/sse";
    }

    public int port() {
        return server.getAddress().getPort();
    }

    /** 收到的全部请求（按到达顺序）。 */
    public List<String> requests() {
        return List.copyOf(rawRequests);
    }

    /** 实际执行的 tools/call 次数。 */
    public int toolCalls() {
        return toolCalls.get();
    }

    public int requestCount() {
        return rawRequests.size();
    }

    public void resetCounters() {
        rawRequests.clear();
        toolCalls.set(0);
    }

    private void handleSse(HttpExchange exchange) throws IOException {
        rawRequests.add("GET " + exchange.getRequestURI().getPath());
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, 0);
        OutputStream out = exchange.getResponseBody();
        eventStream.set(out);
        try {
            writeEvent(out, "endpoint", "/message");
            out.flush();
            // 事件流保持打开：JSON-RPC 响应都从这里送回
            while (eventStream.get() == out) {
                Thread.sleep(50);
                out.write(": ping\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // 客户端断开，正常结束
        } finally {
            eventStream.compareAndSet(out, null);
            exchange.close();
        }
    }

    private void handleMessage(HttpExchange exchange) throws IOException {
        rawRequests.add("POST " + exchange.getRequestURI().getPath());
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String method = extract(body, "\"method\"");
        String id = extractId(body);

        // POST 本身只回 202：真正的 JSON-RPC 响应走事件流
        exchange.sendResponseHeaders(202, -1);
        exchange.close();

        if (id == null) {
            return;
        }
        String result = switch (method == null ? "" : method) {
            case "initialize" -> """
                    {"protocolVersion":"2024-11-05","capabilities":{"tools":{}},"serverInfo":{"name":"controlled","version":"1"}}""";
            case "tools/list" -> """
                    {"tools":[{"name":"%s","description":"controlled echo","inputSchema":{"type":"object","properties":{}}}]}"""
                    .formatted(TOOL_NAME);
            case "tools/call" -> {
                toolCalls.incrementAndGet();
                yield """
                        {"content":[{"type":"text","text":"%s"}]}""".formatted(toolText);
            }
            default -> null;
        };
        String response = result == null
                ? """
                {"jsonrpc":"2.0","id":%s,"error":{"code":-32601,"message":"unsupported"}}""".formatted(id)
                : """
                {"jsonrpc":"2.0","id":%s,"result":%s}""".formatted(id, result);
        sendEvent(response);
    }

    private void sendEvent(String json) {
        OutputStream out = eventStream.get();
        if (out == null) {
            return;
        }
        try {
            writeEvent(out, "message", json);
            out.flush();
        } catch (IOException e) {
            // 事件流已关闭，丢弃这次响应
        }
    }

    private void writeEvent(OutputStream out, String event, String data) throws IOException {
        out.write(("event: " + event + "\ndata: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
    }

    private String extract(String body, String key) {
        int keyIndex = body.indexOf(key);
        if (keyIndex < 0) {
            return null;
        }
        int start = body.indexOf('"', body.indexOf(':', keyIndex) + 1);
        if (start < 0) {
            return null;
        }
        int end = body.indexOf('"', start + 1);
        return end < 0 ? null : body.substring(start + 1, end);
    }

    private String extractId(String body) {
        int index = body.indexOf("\"id\"");
        if (index < 0) {
            return null;
        }
        int colon = body.indexOf(':', index);
        if (colon < 0) {
            return null;
        }
        int end = colon + 1;
        while (end < body.length() && Character.isWhitespace(body.charAt(end))) {
            end++;
        }
        int start = end;
        if (start < body.length() && body.charAt(start) == '"') {
            int close = body.indexOf('"', start + 1);
            return close < 0 ? null : body.substring(start, close + 1);
        }
        while (end < body.length() && (Character.isDigit(body.charAt(end)) || body.charAt(end) == '-')) {
            end++;
        }
        return end == start ? null : body.substring(start, end);
    }

    @Override
    public void close() {
        eventStream.set(null);
        server.stop(0);
        executor.shutdownNow();
    }
}
