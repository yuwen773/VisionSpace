package com.yuwen.visionspace.utils.strategy;

import com.yuwen.visionspace.config.ColorExtractProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.awt.image.BufferedImage;

/**
 * 颜色提取策略工厂
 */
@Slf4j
@Component
public class ColorExtractStrategyFactory {

    private static final String METHOD_MEDIAN_CUT = "median-cut";

    private static final String METHOD_KMEANS = "kmeans";

    @Resource
    private ColorExtractProperties properties;

    @Resource
    private ColorThiefStrategy colorThiefStrategy;

    @Resource
    private KMeansStrategy kMeansStrategy;

    /**
     * 创建主要的颜色提取策略
     */
    public ColorExtractStrategy createStrategy() {
        String method = properties.getMethod();
        if (METHOD_KMEANS.equalsIgnoreCase(method)) {
            log.info("使用 K-Means 颜色提取策略");
            return kMeansStrategy;
        }
        log.info("使用 Median Cut 颜色提取策略（color-thief）");
        return colorThiefStrategy;
    }

    /**
     * 提取主色调，支持降级
     *
     * @param image BufferedImage 对象
     * @return 主色调 HEX 字符串，提取失败返回 null
     */
    public String extractDominantColor(BufferedImage image) {
        if (image == null) {
            return null;
        }

        ColorExtractStrategy primary = createStrategy();

        // 尝试主策略
        String color = primary.extractDominantColor(image);
        if (color != null) {
            return color;
        }

        // 降级处理
        if (properties.isFallbackEnabled()) {
            log.info("主策略提取失败，降级到 K-Means");
            return kMeansStrategy.extractDominantColor(image);
        }

        return null;
    }
}
