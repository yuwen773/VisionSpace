package com.yuwen.visionspace.manager.storage.init;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuwen.visionspace.mapper.StorageConfigMapper;
import com.yuwen.visionspace.model.entity.StorageConfig;
import com.yuwen.visionspace.model.enums.StoragePlatformEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileStorageProperties;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.FileStorageServiceBuilder;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Collections;

/**
 * 从数据库加载存储平台配置，动态添加到 FileStorageService
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileStorageInit {

    private final StorageConfigMapper storageConfigMapper;
    private final FileStorageService fileStorageService;

    @PostConstruct
    public void init() {
        refresh();
    }

    public void refresh() {
        // 清空现有存储平台列表
        fileStorageService.getFileStorageList().clear();

        for (StorageConfig config : storageConfigMapper.selectList(
                new LambdaQueryWrapper<StorageConfig>().eq(StorageConfig::getStatus, 1))) {
            String platform = config.getPlatform();
            try {
                StoragePlatformEnum platformEnum = StoragePlatformEnum.getEnumByCode(platform);
                if (platformEnum == null) {
                    log.warn("不支持的平台类型: {}", platform);
                    continue;
                }
                switch (platformEnum) {
                    case MINIO: {
                        FileStorageProperties.MinioConfig c = new FileStorageProperties.MinioConfig();
                        c.setPlatform(config.getPlatform());
                        c.setAccessKey(config.getAccessKey());
                        c.setSecretKey(config.getSecretKey());
                        c.setEndPoint(config.getEndpoint());
                        c.setBucketName(config.getBucket());
                        c.setDomain(config.getDomain());
                        c.setBasePath(config.getBasePath());
                        fileStorageService.getFileStorageList().addAll(
                                FileStorageServiceBuilder.buildMinioFileStorage(Collections.singletonList(c), null));
                        break;
                    }
                    case COS: {
                        FileStorageProperties.TencentCosConfig c = new FileStorageProperties.TencentCosConfig();
                        c.setPlatform(config.getPlatform());
                        c.setSecretId(config.getAccessKey());
                        c.setSecretKey(config.getSecretKey());
                        c.setRegion(config.getRegion());
                        c.setBucketName(config.getBucket());
                        c.setDomain(config.getDomain());
                        c.setBasePath(config.getBasePath());
                        fileStorageService.getFileStorageList().addAll(
                                FileStorageServiceBuilder.buildTencentCosFileStorage(Collections.singletonList(c), null));
                        break;
                    }
                    case OSS: {
                        FileStorageProperties.AliyunOssConfig c = new FileStorageProperties.AliyunOssConfig();
                        c.setPlatform(config.getPlatform());
                        c.setAccessKey(config.getAccessKey());
                        c.setSecretKey(config.getSecretKey());
                        c.setEndPoint(config.getEndpoint());
                        c.setBucketName(config.getBucket());
                        c.setDomain(config.getDomain());
                        c.setBasePath(config.getBasePath());
                        fileStorageService.getFileStorageList().addAll(
                                FileStorageServiceBuilder.buildAliyunOssFileStorage(Collections.singletonList(c), null));
                        break;
                    }
                    case OBS: {
                        FileStorageProperties.HuaweiObsConfig c = new FileStorageProperties.HuaweiObsConfig();
                        c.setPlatform(config.getPlatform());
                        c.setAccessKey(config.getAccessKey());
                        c.setSecretKey(config.getSecretKey());
                        c.setEndPoint(config.getEndpoint());
                        c.setBucketName(config.getBucket());
                        c.setDomain(config.getDomain());
                        c.setBasePath(config.getBasePath());
                        fileStorageService.getFileStorageList().addAll(
                                FileStorageServiceBuilder.buildHuaweiObsFileStorage(Collections.singletonList(c), null));
                        break;
                    }
                }

                if (config.getIsActive() != null && config.getIsActive() == 1) {
                    fileStorageService.getProperties().setDefaultPlatform(platform);
                    log.info("激活存储平台: {}", platform);
                }
                log.info("注册存储平台成功: {} ({})", config.getPlatformName(), platform);
            } catch (Exception e) {
                log.error("注册存储平台失败: {} ({})", config.getPlatformName(), platform, e);
            }
        }

        log.info("存储平台初始化完成，共加载 {} 个平台配置", fileStorageService.getFileStorageList().size());
    }
}
