package com.yuwen.visionspace.model.dto.storage;

import lombok.Data;
import java.io.Serializable;

@Data
public class StorageConfigAddRequest implements Serializable {

    private String platform;

    private String platformName;

    private String endpoint;

    private String region;

    private String bucket;

    private String accessKey;

    private String secretKey;

    private String domain;

    private String basePath;

    private Integer isActive;

    private Integer isDefault;

    private Integer orderNum;

    private Integer status;

    private static final long serialVersionUID = 1L;
}