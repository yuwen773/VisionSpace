package com.yuwen.visionspace.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导出当前后端契约，供前端生成器对账。
 *
 * <p>前端 {@code src/api/} 由 {@code npm run openapi2ts} 从 {@code /api/v3/api-docs} 生成。
 * 该命令原本要求开发者手工启动后端；这里在隔离验收里把契约落盘，验收脚本即可在没有
 * 活后端的情况下重新生成并比对，接口变更未同步客户端时直接失败。
 */
@IsolatedSpringBootTest
class OpenApiContractTest {

    /** 与前端 openapi.config.js 的默认 schemaPath 指向同一份契约。 */
    private static final Path OUTPUT = Path.of("target", "openapi-contract.json");

    @LocalServerPort
    private int port;

    @Test
    void exportsTheContractTheFrontendGeneratorConsumes() throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v3/api-docs"))
                    .timeout(Duration.ofSeconds(30)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());

            Files.createDirectories(OUTPUT.getParent());
            Files.writeString(OUTPUT, response.body());

            assertTrue(Files.size(OUTPUT) > 1024, "OpenAPI contract looks truncated");
            assertTrue(response.body().contains("\"paths\""), "OpenAPI contract has no paths");
        }
    }
}
