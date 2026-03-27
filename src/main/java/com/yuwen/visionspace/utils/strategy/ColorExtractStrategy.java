package com.yuwen.visionspace.utils.strategy;

import java.awt.image.BufferedImage;

/**
 * 颜色提取策略接口
 */
public interface ColorExtractStrategy {

    /**
     * 从 BufferedImage 提取主色调
     *
     * @param image BufferedImage 对象
     * @return 主色调 HEX 字符串（如 "#FF5733"），提取失败返回 null
     */
    String extractDominantColor(BufferedImage image);
}
