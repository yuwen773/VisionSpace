package com.yuwen.visionspace.utils.strategy;

import de.androidpit.colorthief.ColorThief;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;

/**
 * 基于 color-thief-java 的颜色提取策略（Median Cut 算法）
 */
@Slf4j
@Component
public class ColorThiefStrategy implements ColorExtractStrategy {

    private static final int QUALITY = 10;

    private static final boolean IGNORE_WHITE = true;

    @Override
    public String extractDominantColor(BufferedImage image) {
        if (image == null) {
            return null;
        }
        try {
            int[] rgb = ColorThief.getColor(image, QUALITY, IGNORE_WHITE);
            if (rgb == null) {
                return null;
            }
            return String.format("#%02X%02X%02X", rgb[0], rgb[1], rgb[2]);
        } catch (Exception e) {
            log.warn("ColorThief 提取主色调失败", e);
            return null;
        }
    }
}
