package com.yuwen.visionspace.utils.strategy;

import com.yuwen.visionspace.utils.ColorExtractUtils;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;

/**
 * 基于 K-Means 算法的颜色提取策略（保留原实现作为降级备选）
 */
@Slf4j
public class KMeansStrategy implements ColorExtractStrategy {

    @Override
    public String extractDominantColor(BufferedImage image) {
        try {
            return ColorExtractUtils.extractDominantColor(image);
        } catch (Exception e) {
            log.warn("KMeans 提取主色调失败", e);
            return null;
        }
    }
}
