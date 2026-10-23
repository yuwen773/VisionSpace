package com.yuwen.visionspace.acceptance;

import com.yuwen.visionspace.manager.storage.PictureStorageService;
import org.dromara.x.file.storage.core.FileInfo;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 验收存储替身：记录每次读写删的对象键，不连接真实云存储。
 *
 * <p>上传返回可预测的平台与对象键，读取返回固定的字节内容。测试据此断言
 * 删除/读取只使用资源记录里的平台与键，而不解析客户端 URL。
 */
@TestConfiguration(proxyBeanMethods = false)
public class RecordingStorageConfiguration {

    static final String PLATFORM = "acceptance";

    private static final byte[] CONTENT = "acceptance-image-bytes".getBytes(StandardCharsets.UTF_8);

    /**
     * 让对象删除失败，用于验证「删除未确认保留可重试状态」。
     *
     * <p>默认不失败；只有明确打开的用例才会走到失败分支，用完必须复位。
     */
    private static final java.util.concurrent.atomic.AtomicBoolean FAIL_DELETES =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    static void failDeletes(boolean fail) {
        FAIL_DELETES.set(fail);
    }

    @Bean
    @Primary
    PictureStorageService pictureStorageService(ExternalCallRecorder recorder) {
        return new PictureStorageService() {
            @Override
            public boolean deleteObject(String key) {
                try (var client = HttpClient.newHttpClient()) {
                    var request = HttpRequest.newBuilder(URI.create(recorder.baseUrl() + "/storage/delete"))
                            .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString(key)).build();
                    return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 204;
                } catch (Exception e) {
                    throw new IllegalStateException("Acceptance storage recording failed", e);
                }
            }

            @Override
            public boolean deleteObject(String platform, String objectKey) {
                recorder.record(new ExternalCallRecorder.Call("DELETE", platform, objectKey));
                // 删除失败时返回 false：调用方必须保留可重试状态
                return !FAIL_DELETES.get();
            }

            @Override
            public FileInfo putPictureObject(File file, String path, String name) {
                return synthetic(path, name);
            }

            @Override
            public FileInfo putObject(File file, String path, String name) {
                return synthetic(path, name);
            }

            @Override
            public byte[] readObject(String platform, String objectKey) {
                recorder.record(new ExternalCallRecorder.Call("READ", platform, objectKey));
                return CONTENT;
            }

            @Override
            public byte[] readLegacyObject(String url) {
                recorder.record(new ExternalCallRecorder.Call("READ-LEGACY", "", url));
                return CONTENT;
            }

            @Override
            public InputStream getObjectByUrl(String url) {
                throw new UnsupportedOperationException("Acceptance download not configured");
            }

            private FileInfo synthetic(String path, String name) {
                FileInfo info = new FileInfo();
                info.setPlatform(PLATFORM);
                info.setBasePath("");
                info.setPath(path);
                info.setFilename(name);
                info.setUrl("https://acceptance.invalid" + path + name);
                info.setSize((long) CONTENT.length);
                // 与原平台一致：缩略图键为文件名 + 后缀
                info.setThFilename(name + ".min.jpg");
                info.setThUrl("https://acceptance.invalid" + path + name + ".min.jpg");
                return info;
            }
        };
    }
}
