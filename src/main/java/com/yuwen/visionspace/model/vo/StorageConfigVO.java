package com.yuwen.visionspace.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 存储配置的公开返回模型。
 *
 * <p>本类刻意不声明 accessKey / secretKey 字段：即便将来有人误用 BeanUtils 全量拷贝，
 * 也无法把真实密钥序列化到浏览器。只暴露"是否已配置"与脱敏提示。
 */
@Data
public class StorageConfigVO implements Serializable {

    private Long id;

    private String platform;

    private String platformName;

    private String endpoint;

    private String region;

    private String bucket;

    /**
     * 已配置 AccessKey 时返回遮蔽值，未配置时为 null。
     */
    private String accessKeyMasked;

    /**
     * 已配置 SecretKey 时返回遮蔽值，未配置时为 null。响应中永不含真实值。
     */
    private String secretKeyMasked;

    private boolean hasAccessKey;

    private boolean hasSecretKey;

    private String domain;

    private String basePath;

    private Integer isActive;

    private Integer isDefault;

    private Integer orderNum;

    private Integer status;

    private Date createTime;

    private Date updateTime;

    private static final long serialVersionUID = 1L;
}
