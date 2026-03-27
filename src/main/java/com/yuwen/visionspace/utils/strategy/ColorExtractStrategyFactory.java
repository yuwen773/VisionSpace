package com.yuwen.visionspace.utils.strategy;

import com.yuwen.visionspace.config.ColorExtractProperties;
import com.yuwen.visionspace.model.enums.ColorExtractMethodEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.awt.image.BufferedImage;

/**
 * 颜色提取策略工厂
 */
@Slf4j
@Component
public class ColorExtractStrategyFactory {

    @Resource
    private ColorExtractProperties properties;

    @Resource
    private ColorThiefStrategy colorThiefStrategy;

    @Resource
    private KMeansStrategy kMeansStrategy;

    private volatile ColorExtractStrategy cachedStrategy;

    private volatile ColorExtractMethodEnum cachedMethod;

    @PostConstruct
    public void init() {
        cachedMethod = ColorExtractMethodEnum.fromValue(properties.getMethod());
        cachedStrategy = createStrategy(cachedMethod);
        log.info("颜色提取策略初始化: {}", cachedMethod == ColorExtractMethodEnum.KMEANS ? "K-Means" : "Median Cut");
    }

    /**
     * 获取颜色提取策略
     */
    public ColorExtractStrategy createStrategy() {
        return cachedStrategy;
    }

    private ColorExtractStrategy createStrategy(ColorExtractMethodEnum method) {
        if (method == ColorExtractMethodEnum.KMEANS) {
            return kMeansStrategy;
        }
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

        // 降级处理：只有当主策略不是 K-Means 时才降级
        if (properties.isFallbackEnabled() && cachedMethod != ColorExtractMethodEnum.KMEANS) {
            log.info("主策略提取失败，降级到 K-Means");
            return kMeansStrategy.extractDominantColor(image);
        }

        return null;
    }
}
