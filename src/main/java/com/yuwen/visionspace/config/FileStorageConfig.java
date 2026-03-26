package com.yuwen.visionspace.config;

import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.FileStorageServiceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * FileStorageService 配置类
 * 从数据库加载配置后，通过 FileStoragePropertiesHolder 构建 FileStorageService
 */
@Configuration
public class FileStorageConfig {

    @Bean
    public FileStoragePropertiesHolder fileStoragePropertiesHolder() {
        return new FileStoragePropertiesHolder();
    }

    @Bean
    @Primary
    public FileStorageService fileStorageService(FileStoragePropertiesHolder propertiesHolder) {
        return FileStorageServiceBuilder
                .create(propertiesHolder.getProperties())
                .setDefaultFileRecorder()
                .setDefaultTikaFactory()
                .build();
    }
}
