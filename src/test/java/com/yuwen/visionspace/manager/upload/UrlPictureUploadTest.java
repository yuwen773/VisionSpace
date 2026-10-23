package com.yuwen.visionspace.manager.upload;

import com.sun.net.httpserver.HttpServer;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.manager.outbound.OutboundDownloader;
import com.yuwen.visionspace.manager.outbound.OutboundPolicy;
import com.yuwen.visionspace.manager.outbound.OutboundPolicyProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * URL 图片上传的地址策略与有界下载。
 *
 * <p>替换目标只监听回环；拒绝场景不发起任何请求，也不留下临时文件。
 */
class UrlPictureUploadTest {

    private HttpServer server;
    private String baseUrl;
    private int requests;
    private UrlPictureUpload upload;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/photo.png", exchange -> {
            requests++;
            byte[] content = "png-bytes".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        OutboundPolicyProperties properties = new OutboundPolicyProperties();
        properties.setAllowLoopback(true);
        upload = new UrlPictureUpload();
        setField(upload, "outboundDownloader", new OutboundDownloader(new OutboundPolicy(properties)));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void validUrlIsAcceptedAndDerivesFilename() {
        upload.validPicture(baseUrl + "/photo.png");
        // 与文件上传一致：保留原始文件名（含后缀），对象键由模板重新拼装
        assertEquals("photo.png", upload.getOriginFilename(baseUrl + "/photo.png"));
        assertEquals("photo.png", upload.getOriginFilename(baseUrl + "/photo.png?token=secret"));
    }

    @Test
    void filenameWithoutSuffixGetsADefault() {
        assertEquals("image.jpg", upload.getOriginFilename("https://example.com/image"));
    }

    @Test
    void restrictedTargetsFailBeforeAnyRequest() {
        for (String target : List.of("http://127.0.0.1.evil.invalid/a.png",
                "http://169.254.169.254/latest/meta-data/", "file:///etc/passwd",
                "http://10.0.0.1/a.png")) {
            assertThrows(BusinessException.class, () -> upload.validPicture(target), target);
        }
        assertEquals(0, requests);
    }

    @Test
    void blankUrlIsRejected() {
        assertThrows(BusinessException.class, () -> upload.validPicture(" "));
        assertThrows(BusinessException.class, () -> upload.validPicture(null));
    }

    @Test
    void downloadWritesActualBytesAndCleansUpOnFailure() throws Exception {
        File target = tempDir.resolve("photo.png").toFile();
        upload.processFile(baseUrl + "/photo.png", target);
        assertEquals("png-bytes".length(), Files.size(target.toPath()));
        assertEquals(1, requests);
    }

    @Test
    void unsafeTargetFailsWithoutCreatingContent() {
        File target = tempDir.resolve("blocked.png").toFile();
        assertThrows(BusinessException.class,
                () -> upload.processFile("http://169.254.169.254/a.png", target));
        assertTrue(!target.exists(), "rejected download must not leave content");
    }

    private void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
