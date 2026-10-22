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

    @Test
    void testColorDistance() {
        // 测试相同颜色距离为0
        Color c1 = new Color(255, 0, 0);
        Color c2 = new Color(255, 0, 0);
        // 通过提取相同颜色图片验证
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(c1);
        g.fillRect(0, 0, 10, 10);
        g.dispose();
        String result = ColorExtractUtils.extractDominantColor(image);
        assertEquals("#FF0000", result);
    }
}
