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
import java.time.Duration;

@TestConfiguration(proxyBeanMethods = false)
public class RecordingStorageConfiguration {
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
            public FileInfo putPictureObject(File file, String path, String name) {
                throw new UnsupportedOperationException("Acceptance picture upload not configured");
            }

            @Override
            public FileInfo putObject(File file, String path, String name) {
                throw new UnsupportedOperationException("Acceptance upload not configured");
            }

            @Override
            public InputStream getObjectByUrl(String url) {
                throw new UnsupportedOperationException("Acceptance download not configured");
            }
        };
    }
}
