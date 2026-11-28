package com.yuwen.visionspace.utils;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class ColorExtractUtilsTest {

    @Test
    void testExtractDominantColorWithNull() {
        assertNull(ColorExtractUtils.extractDominantColor((BufferedImage) null));
        assertNull(ColorExtractUtils.extractDominantColor((String) null));
    }

    @Test
    void testExtractDominantColorWithSolidColor() {
        // 创建一个纯红色图片
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 100, 100);
        g.dispose();

        String result = ColorExtractUtils.extractDominantColor(image);
        assertNotNull(result);
        assertEquals("#FF0000", result);
    }

    @Test
    void testExtractDominantColorWithMixedColors() {
        // 创建一个红蓝各占一半的图片
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 50, 100);
        g.setColor(Color.BLUE);
        g.fillRect(50, 0, 50, 100);
        g.dispose();

        String result = ColorExtractUtils.extractDominantColor(image);
        assertNotNull(result);
        // 应该是红色或蓝色
        assertTrue(result.equals("#FF0000") || result.equals("#0000FF"),
                "Expected red or blue but got: " + result);
    }
}
