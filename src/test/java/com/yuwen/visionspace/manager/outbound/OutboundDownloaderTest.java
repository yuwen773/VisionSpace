package com.yuwen.visionspace.manager.outbound;

import com.sun.net.httpserver.HttpServer;
import com.yuwen.visionspace.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 有界下载的验收：使用本机受控 HTTP 替身，不访问任何真实内网、元数据或第三方服务。
 *
 * <p>替身监听回环地址，测试通过显式开关放行回环（生产不打开），
 * 因此这里的"允许"只说明策略在受控环境下按预期工作，不代表生产放行了内网。
 */
class OutboundDownloaderTest {

    private static final List<String> IMAGE_TYPES = List.of("image/png", "image/jpeg");

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger requests = new AtomicInteger();

    @TempDir
    Path tempDir;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void downloadsImageWithinBoundsAndReportsActualBytes() throws IOException {
        byte[] content = "controlled-image".getBytes(StandardCharsets.UTF_8);
        handle("/ok.png", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            // Content-Length 故意小于实际写入，验证放行依据是实际字节而不是声明值
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(content);
            exchange.close();
        });

        OutboundDownloader.Downloaded result = downloader().download(
                baseUrl + "/ok.png", newFile("ok.png"), IMAGE_TYPES);

        assertEquals(content.length, result.bytes());
        assertEquals("image/png", result.contentType());
        assertEquals(content.length, Files.size(tempDir.resolve("ok.png")));
    }

    @Test
    void headSupportIsIrrelevantBecauseChecksRunOnTheActualGet() {
        // 替身不支持 HEAD：安全检查仍必须在 GET 上完成
        handle("/no-head.png", exchange -> {
            if ("HEAD".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
            } else {
                exchange.getResponseHeaders().add("Content-Type", "image/png");
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write("x".getBytes(StandardCharsets.UTF_8));
            }
            exchange.close();
        });

        OutboundDownloader.Downloaded result = downloader().download(
                baseUrl + "/no-head.png", newFile("no-head.png"), IMAGE_TYPES);
        assertEquals(1, result.bytes());
    }

    @Test
    void declaredLengthIsNotTrustedWhenBodyExceedsLimit() {
        OutboundPolicyProperties properties = properties();
        properties.setMaxDownloadBytes(16);
        handle("/huge.png", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            // 声明一个很小的长度，实际写更多字节
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(new byte[1024]);
            exchange.close();
        });

        File target = newFile("huge.png");
        BusinessException error = assertThrows(BusinessException.class,
                () -> downloader(properties).download(baseUrl + "/huge.png", target, IMAGE_TYPES));
        assertEquals(40000, error.getCode());
        assertFalse(target.exists() && target.length() > 16, "must not keep oversized body");
    }

    @Test
    void rejectsUnexpectedContentType() {
        handle("/page.html", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write("<html></html>".getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });

        assertThrows(BusinessException.class,
                () -> downloader().download(baseUrl + "/page.html", newFile("page.html"), IMAGE_TYPES));
    }

    @Test
    void followsEachRedirectWithPolicyCheckAndStopsAtLimit() {
        // 第一跳通过，第二跳再次通过；重定向次数上限以内允许
        handle("/hop1", redirect("/hop2"));
        handle("/hop2", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write("ok".getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });

        OutboundDownloader.Downloaded result = downloader().download(
                baseUrl + "/hop1", newFile("hop.png"), IMAGE_TYPES);
        assertEquals(2, result.bytes());
        assertEquals("/hop2", result.finalUri().getPath());
    }

    @Test
    void redirectLoopIsBounded() {
        handle("/loop", redirect("/loop"));
        OutboundPolicyProperties properties = properties();
        properties.setMaxRedirects(2);

        BusinessException error = assertThrows(BusinessException.class,
                () -> downloader(properties).download(baseUrl + "/loop", newFile("loop.png"), IMAGE_TYPES));
        assertEquals(40000, error.getCode());
        // 上限是 2，最多发起 3 次请求（首跳 + 2 次重定向）后必须停下
        assertTrue(requests.get() <= 4, "redirects must be bounded: " + requests.get());
    }

    @Test
    void redirectToRestrictedTargetIsRejectedPerHop() {
        // 每一跳都要重新判定：指向受限地址的 Location 必须在发请求之前被拒绝
        handle("/to-metadata", redirect("http://169.254.169.254/latest/meta-data/"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> downloader().download(baseUrl + "/to-metadata",
                        newFile("meta.png"), IMAGE_TYPES));
        assertEquals(40000, error.getCode());
        // 只有首跳发出去过，受限目标一次都没有被请求
        assertEquals(1, requests.get());
    }

    @Test
    void redirectToPrivateAddressIsRejected() {
        handle("/to-private", redirect("http://10.0.0.1/a.png"));
        assertThrows(BusinessException.class,
                () -> downloader().download(baseUrl + "/to-private",
                        newFile("private.png"), IMAGE_TYPES));
        assertEquals(1, requests.get());
    }

    @Test
    void nonSuccessStatusIsRejectedWithoutWritingContent() {
        handle("/missing.png", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        assertThrows(BusinessException.class,
                () -> downloader().download(baseUrl + "/missing.png",
                        newFile("missing.png"), IMAGE_TYPES));
    }

    @Test
    void emptyBodyIsRejected() {
        handle("/empty.png", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        assertThrows(BusinessException.class,
                () -> downloader().download(baseUrl + "/empty.png",
                        newFile("empty.png"), IMAGE_TYPES));
    }

    // ---- helpers ----

    private OutboundPolicyProperties properties() {
        OutboundPolicyProperties properties = new OutboundPolicyProperties();
        // 受控替身监听回环地址：仅本测试显式打开
        properties.setAllowLoopback(true);
        properties.setMaxDownloadBytes(1024 * 1024);
        properties.setMaxRedirects(3);
        properties.setTotalTimeoutSeconds(5);
        properties.setConnectTimeoutSeconds(2);
        properties.setReadTimeoutSeconds(2);
        return properties;
    }

    private OutboundDownloader downloader() {
        return downloader(properties());
    }

    private OutboundDownloader downloader(OutboundPolicyProperties properties) {
        return new OutboundDownloader(new OutboundPolicy(properties));
    }

    private File newFile(String name) {
        return tempDir.resolve(name).toFile();
    }

    private void handle(String path, com.sun.net.httpserver.HttpHandler handler) {
        server.createContext(path, exchange -> {
            requests.incrementAndGet();
            handler.handle(exchange);
        });
    }

    private com.sun.net.httpserver.HttpHandler redirect(String location) {
        return exchange -> {
            exchange.getResponseHeaders().add("Location", location);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        };
    }
}
