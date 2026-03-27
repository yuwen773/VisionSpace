package com.yuwen.visionspace.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 颜色提取配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "vision-space.color-extract")
public class ColorExtractProperties {

    /**
     * 颜色提取方法: median-cut（默认）或 kmeans
     */
    private String method = "median-cut";

    /**
     * 是否启用降级（primary 失败时使用 K-Means）
     */
    private boolean fallbackEnabled = true;
}
