package com.yuwen.visionspace.init;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuwen.visionspace.config.FileStoragePropertiesHolder;
import com.yuwen.visionspace.mapper.StorageConfigMapper;
import com.yuwen.visionspace.model.entity.StorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileStorageProperties;
import org.dromara.x.file.storage.core.FileStorageProperties.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;

/**
 * 从数据库加载存储平台配置，构建 FileStorageService
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileStorageInit {

    private final StorageConfigMapper storageConfigMapper;
    private final FileStoragePropertiesHolder propertiesHolder;

    @PostConstruct
    public void init() {
        refresh();
    }

    public void refresh() {
        // 清空所有平台配置
        propertiesHolder.getProperties().setMinio(new ArrayList<>());
        propertiesHolder.getProperties().setAliyunOss(new ArrayList<>());
        propertiesHolder.getProperties().setTencentCos(new ArrayList<>());
        propertiesHolder.getProperties().setHuaweiObs(new ArrayList<>());
        propertiesHolder.getProperties().setDefaultPlatform(null);

        String defaultPlatform = null;

        for (StorageConfig config : storageConfigMapper.selectList(
                new LambdaQueryWrapper<StorageConfig>().eq(StorageConfig::getStatus, 1))) {
            String platform = config.getPlatform();
            try {
                switch (platform) {
                    case "minio": {
                        MinioConfig c = new MinioConfig();
                        c.setPlatform(config.getPlatform());
                        c.setAccessKey(config.getAccessKey());
                        c.setSecretKey(config.getSecretKey());
                        c.setEndPoint(config.getEndpoint());
                        c.setBucketName(config.getBucket());
                        c.setDomain(config.getDomain());
                        c.setBasePath(config.getBasePath());
                        ArrayList<MinioConfig> list = new ArrayList<>();
                        list.add(c);
                        propertiesHolder.getProperties().setMinio(list);
                        break;
                    }
                    case "cos": {
                        TencentCosConfig c = new TencentCosConfig();
                        c.setPlatform(config.getPlatform());
                        c.setSecretId(config.getAccessKey());
                        c.setSecretKey(config.getSecretKey());
                        c.setRegion(config.getRegion());
                        c.setBucketName(config.getBucket());
                        c.setDomain(config.getDomain());
                        c.setBasePath(config.getBasePath());
                        ArrayList<TencentCosConfig> list = new ArrayList<>();
                        list.add(c);
                        propertiesHolder.getProperties().setTencentCos(list);
                        break;
                    }
                    case "oss": {
                        AliyunOssConfig c = new AliyunOssConfig();
                        c.setPlatform(config.getPlatform());
                        c.setAccessKey(config.getAccessKey());
                        c.setSecretKey(config.getSecretKey());
                        c.setEndPoint(config.getEndpoint());
                        c.setBucketName(config.getBucket());
                        c.setDomain(config.getDomain());
                        c.setBasePath(config.getBasePath());
                        ArrayList<AliyunOssConfig> list = new ArrayList<>();
                        list.add(c);
                        propertiesHolder.getProperties().setAliyunOss(list);
                        break;
                    }
                    case "obs": {
                        HuaweiObsConfig c = new HuaweiObsConfig();
                        c.setPlatform(config.getPlatform());
                        c.setAccessKey(config.getAccessKey());
                        c.setSecretKey(config.getSecretKey());
                        c.setEndPoint(config.getEndpoint());
                        c.setBucketName(config.getBucket());
                        c.setDomain(config.getDomain());
                        c.setBasePath(config.getBasePath());
                        ArrayList<HuaweiObsConfig> list = new ArrayList<>();
                        list.add(c);
                        propertiesHolder.getProperties().setHuaweiObs(list);
                        break;
                    }
                    default:
                        log.warn("不支持的平台类型: {}", platform);
                        continue;
                }

                if (config.getIsActive() != null && config.getIsActive() == 1) {
                    defaultPlatform = platform;
                }
                log.info("注册存储平台成功: {} ({})", config.getPlatformName(), platform);
            } catch (Exception e) {
                log.error("注册存储平台失败: {} ({})", config.getPlatformName(), platform, e);
            }
        }

        if (defaultPlatform != null) {
            propertiesHolder.getProperties().setDefaultPlatform(defaultPlatform);
            log.info("激活存储平台: {}", defaultPlatform);
        }

        int total = propertiesHolder.getProperties().getMinio().size()
                + propertiesHolder.getProperties().getAliyunOss().size()
                + propertiesHolder.getProperties().getTencentCos().size()
                + propertiesHolder.getProperties().getHuaweiObs().size();
        log.info("存储平台初始化完成，共加载 {} 个平台配置", total);
    }
}
