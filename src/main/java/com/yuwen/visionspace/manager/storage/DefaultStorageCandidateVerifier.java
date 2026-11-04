package com.yuwen.visionspace.manager.storage;

import cn.hutool.core.util.StrUtil;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.model.entity.StorageConfig;
import com.yuwen.visionspace.model.enums.StoragePlatformEnum;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileStorageServiceBuilder;
import org.dromara.x.file.storage.core.FileStorageProperties;
import org.dromara.x.file.storage.core.platform.FileStorage;
import org.dromara.x.file.storage.core.platform.MinioFileStorage;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 默认候选验证：完整构建平台客户端并强制完成惰性初始化，MinIO 默认探活。
 *
 * <p>x-file-storage 的平台客户端是惰性构造的（首次上传时才创建 SDK 客户端），
 * 只调用构建器不会发现坏配置。这里在候选阶段就强制构造 SDK 客户端，
 * 让坏端点、坏凭据格式在切换前显式失败，而不是切换后上传时才暴露。
 * MinIO 的 bucketExists 探活默认开启（fail-closed）：坏 bucket 名与被撤销
 * 凭据在切换前显式失败；隔离验收环境经 acceptance.yml 显式关闭。
 */
@Slf4j
@Component
public class DefaultStorageCandidateVerifier implements StorageCandidateVerifier {

    private final boolean probeEnabled;

    public DefaultStorageCandidateVerifier(
            @Value("${vision-space.storage.candidate-probe-enabled:false}") boolean probeEnabled) {
        this.probeEnabled = probeEnabled;
    }

    @Override
    public FileStorage buildVerified(StorageConfig config) {
        StoragePlatformEnum platformEnum = StoragePlatformEnum.getEnumByCode(config.getPlatform());
        if (platformEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "不支持的平台类型: " + config.getPlatform());
        }
        validateRequiredFields(platformEnum, config);
        FileStorage storage = buildClient(platformEnum, config);
        initClient(storage, config);
        if (probeEnabled) {
            probe(platformEnum, storage, config);
        }
        log.info("候选存储平台验证通过: {} ({})", config.getPlatformName(), config.getPlatform());
        return storage;
    }

    /**
     * 必填字段在候选阶段显式校验，错误信息只含平台标识，不回显凭据。
     */
    private void validateRequiredFields(StoragePlatformEnum platformEnum, StorageConfig config) {
        requireNonBlank(config.getPlatform(), "platform");
        requireNonBlank(config.getBucket(), "bucket");
        requireNonBlank(config.getAccessKey(), "accessKey");
        requireNonBlank(config.getSecretKey(), "secretKey");
        switch (platformEnum) {
            case MINIO, OSS -> requireNonBlank(config.getEndpoint(), "endpoint");
            case COS -> requireNonBlank(config.getRegion(), "region");
            case OBS -> {
                requireNonBlank(config.getEndpoint(), "endpoint");
                requireNonBlank(config.getRegion(), "region");
            }
        }
    }

    private void requireNonBlank(String value, String field) {
        if (StrUtil.isBlank(value)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "存储配置缺少必填字段: " + field);
        }
    }

    private FileStorage buildClient(StoragePlatformEnum platformEnum, StorageConfig config) {
        return switch (platformEnum) {
            case MINIO -> {
                FileStorageProperties.MinioConfig c = new FileStorageProperties.MinioConfig();
                c.setPlatform(config.getPlatform());
                c.setAccessKey(config.getAccessKey());
                c.setSecretKey(config.getSecretKey());
                c.setEndPoint(config.getEndpoint());
                c.setBucketName(config.getBucket());
                c.setDomain(config.getDomain());
                c.setBasePath(config.getBasePath());
                yield first(FileStorageServiceBuilder.buildMinioFileStorage(Collections.singletonList(c), null));
            }
            case COS -> {
                FileStorageProperties.TencentCosConfig c = new FileStorageProperties.TencentCosConfig();
                c.setPlatform(config.getPlatform());
                c.setSecretId(config.getAccessKey());
                c.setSecretKey(config.getSecretKey());
                c.setRegion(config.getRegion());
                c.setBucketName(config.getBucket());
                c.setDomain(config.getDomain());
                c.setBasePath(config.getBasePath());
                yield first(FileStorageServiceBuilder.buildTencentCosFileStorage(Collections.singletonList(c), null));
            }
            case OSS -> {
                FileStorageProperties.AliyunOssConfig c = new FileStorageProperties.AliyunOssConfig();
                c.setPlatform(config.getPlatform());
                c.setAccessKey(config.getAccessKey());
                c.setSecretKey(config.getSecretKey());
                c.setEndPoint(config.getEndpoint());
                c.setBucketName(config.getBucket());
                c.setDomain(config.getDomain());
                c.setBasePath(config.getBasePath());
                yield first(FileStorageServiceBuilder.buildAliyunOssFileStorage(Collections.singletonList(c), null));
            }
            case OBS -> {
                FileStorageProperties.HuaweiObsConfig c = new FileStorageProperties.HuaweiObsConfig();
                c.setPlatform(config.getPlatform());
                c.setAccessKey(config.getAccessKey());
                c.setSecretKey(config.getSecretKey());
                c.setEndPoint(config.getEndpoint());
                c.setBucketName(config.getBucket());
                c.setDomain(config.getDomain());
                c.setBasePath(config.getBasePath());
                yield first(FileStorageServiceBuilder.buildHuaweiObsFileStorage(Collections.singletonList(c), null));
            }
        };
    }

    private FileStorage first(java.util.List<? extends FileStorage> built) {
        if (built == null || built.isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "候选存储平台客户端构建失败");
        }
        return built.get(0);
    }

    /**
     * 强制完成 SDK 客户端构造。COS/OBS 的 SDK 不在本服务依赖里，
     * 用反射触发 getClient，构造异常同样视为候选失败。
     */
    private void initClient(FileStorage storage, StorageConfig config) {
        try {
            storage.getClass().getMethod("getClient").invoke(storage);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("候选存储平台客户端初始化失败: platform={}", config.getPlatform(), cause);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "候选存储平台客户端初始化失败: " + config.getPlatform());
        } catch (ReflectiveOperationException e) {
            log.error("候选存储平台客户端初始化入口缺失: platform={}", config.getPlatform(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "候选存储平台客户端初始化失败: " + config.getPlatform());
        }
    }

    /**
     * 可选探活（当前仅 MinIO）：验证 bucket 真实可达。
     * 网络失败、凭据无效或 bucket 缺失都会让候选验证失败。
     */
    private void probe(StoragePlatformEnum platformEnum, FileStorage storage, StorageConfig config) {
        if (platformEnum != StoragePlatformEnum.MINIO) {
            return;
        }
        try {
            MinioClient client = ((MinioFileStorage) storage).getClient();
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(config.getBucket()).build());
            if (!exists) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "候选存储平台探活失败: bucket 不存在 (" + config.getPlatform() + ")");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("候选存储平台探活失败: platform={}", config.getPlatform(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "候选存储平台探活失败: " + config.getPlatform());
        }
    }
}
