package com.yuwen.visionspace.config;

import lombok.Data;
import org.dromara.x.file.storage.core.FileStorageProperties;

/**
 * 持有 FileStorageProperties 实例，供 FileStorageInit 和 FileStorageConfig 共用
 */
@Data
public class FileStoragePropertiesHolder {
    private final FileStorageProperties properties = new FileStorageProperties();
}
