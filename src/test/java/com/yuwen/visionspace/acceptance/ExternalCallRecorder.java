package com.yuwen.visionspace.acceptance;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** No provider traffic: unexpected HTTP calls are recorded and rejected. */
public final class ExternalCallRecorder implements AutoCloseable {
    public record Call(String method, String path, String body) { }

    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final HttpServer server;

    public ExternalCallRecorder() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.add(new Call(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] response = "{\"error\":\"unexpected acceptance external call\"}".getBytes(StandardCharsets.UTF_8);
            // The storage adapter has an explicit, deterministic delete contract.
            boolean delete = exchange.getRequestMethod().equals("POST")
                    && exchange.getRequestURI().getPath().equals("/storage/delete");
            exchange.sendResponseHeaders(delete ? 204 : 503, delete ? -1 : response.length);
            if (!delete) {
                exchange.getResponseBody().write(response);
            }
            exchange.close();
        });
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    /**
     * 记录不经过 HTTP 的替身调用（例如存储读写的直接适配）。
     */
    public void record(Call call) {
        calls.add(call);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
