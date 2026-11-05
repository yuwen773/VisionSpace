package com.yuwen.visionspace.manager.storage;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.model.entity.StorageConfig;
import org.dromara.x.file.storage.core.platform.FileStorage;
import org.dromara.x.file.storage.core.platform.MinioFileStorage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 候选存储验证：坏配置在切换前显式失败，而不是切换后上传时才暴露。
 */
class DefaultStorageCandidateVerifierTest {

    private final DefaultStorageCandidateVerifier verifier = new DefaultStorageCandidateVerifier(false);

    @Test
    void unknownPlatformIsRejected() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> verifier.buildVerified(config("bogus", "http://127.0.0.1:4566", null)));
        assertEquals(40000, exception.getCode());
    }

    @Test
    void minioWithoutEndpointIsRejected() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> verifier.buildVerified(config("minio", null, null)));
        assertEquals(40000, exception.getCode());
    }

    @Test
    void minioWithMalformedEndpointFailsClientInit() {
        // x-file-storage 的客户端是惰性构造的；验证必须强制完成初始化
        BusinessException exception = assertThrows(BusinessException.class,
                () -> verifier.buildVerified(config("minio", "http://", null)));
        assertEquals(50000, exception.getCode());
    }

    @Test
    void validMinioCandidateBuildsClient() {
        FileStorage storage = verifier.buildVerified(config("minio", "http://127.0.0.1:4566", null));
        assertInstanceOf(MinioFileStorage.class, storage);
        assertEquals("minio", storage.getPlatform());
    }

    @Test
    void validOssCandidateBuildsClient() {
        StorageConfig config = config("oss", "http://127.0.0.1:4567", null);
        config.setAccessKey("ak");
        config.setSecretKey("sk");
        config.setBucket("bucket");
        FileStorage storage = verifier.buildVerified(config);
        assertEquals("oss", storage.getPlatform());
    }

    @Test
    void unreachableBucketFailsProbeWhenEnabled() {
        // 探活指向必然拒绝连接的本机端口：候选失败必须在切换前显式发生
        DefaultStorageCandidateVerifier probing = new DefaultStorageCandidateVerifier(true);
        assertThrows(BusinessException.class,
                () -> probing.buildVerified(config("minio", "http://127.0.0.1:1", null)));
    }

    private StorageConfig config(String platform, String endpoint, String region) {
        StorageConfig config = new StorageConfig();
        config.setId(1L);
        config.setPlatform(platform);
        config.setPlatformName("acceptance " + platform);
        config.setEndpoint(endpoint);
        config.setRegion(region);
        config.setBucket("acceptance-bucket");
        config.setAccessKey("acceptance-ak");
        config.setSecretKey("acceptance-sk");
        config.setStatus(1);
        return config;
    }
}
