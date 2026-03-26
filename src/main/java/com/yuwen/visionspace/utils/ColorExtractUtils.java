package com.yuwen.visionspace.utils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * K-Means 颜色提取工具类
 */
public class ColorExtractUtils {

    private ColorExtractUtils() {
    }

    /**
     * 从 BufferedImage 提取主色调
     *
     * @param image BufferedImage 对象
     * @return 主色调 HEX 字符串（如 "#FF5733"），提取失败返回 null
     */
    public static String extractDominantColor(BufferedImage image) {
        if (image == null) {
            return null;
        }

        // 缩放图片到 100x100
        BufferedImage scaled = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.drawImage(image.getScaledInstance(100, 100, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();

        // 采样像素
        List<Color> pixels = new ArrayList<>();
        for (int y = 0; y < scaled.getHeight(); y++) {
            for (int x = 0; x < scaled.getWidth(); x++) {
                int rgb = scaled.getRGB(x, y);
                Color color = new Color(rgb);
                // 忽略透明像素和背景色
                if (color.getAlpha() > 0 && !isBackgroundColor(color)) {
                    pixels.add(color);
                }
            }
        }

        if (pixels.isEmpty()) {
            return null;
        }

        // K-Means 聚类
        List<Color> clusters = kMeansClustering(pixels, 5, 20);

        // 找像素最多的簇
        int maxCount = 0;
        Color dominantColor = clusters.get(0);
        List<Integer> counts = new ArrayList<>(Collections.nCopies(clusters.size(), 0));

        for (Color pixel : pixels) {
            double minDist = Double.MAX_VALUE;
            int nearest = 0;
            for (int j = 0; j < clusters.size(); j++) {
                double dist = colorDistance(pixel, clusters.get(j));
                if (dist < minDist) {
                    minDist = dist;
                    nearest = j;
                }
            }
            counts.set(nearest, counts.get(nearest) + 1);
        }

        for (int i = 0; i < clusters.size(); i++) {
            if (counts.get(i) > maxCount) {
                maxCount = counts.get(i);
                dominantColor = clusters.get(i);
            }
        }

        // 转换为 HEX 格式
        return String.format("#%02X%02X%02X",
                dominantColor.getRed(),
                dominantColor.getGreen(),
                dominantColor.getBlue());
    }

    /**
     * 从图片路径提取主色调
     *
     * @param imagePath 图片路径
     * @return 主色调 HEX 字符串，提取失败返回 null
     */
    public static String extractDominantColor(String imagePath) {
        try {
            BufferedImage image = ImageIO.read(new File(imagePath));
            return extractDominantColor(image);
        } catch (Exception e) {
            // 静默返回 null，这是工具类的设计意图（便捷调用，失败时返回 null）
            return null;
        }
    }

    private static List<Color> kMeansClustering(List<Color> pixels, int k, int maxIterations) {
        if (pixels == null || pixels.isEmpty()) {
            return new ArrayList<>();
        }

        // 随机选择 K 个初始质心
        List<Color> centroids = new ArrayList<>();
        List<Color> shuffled = new ArrayList<>(pixels);
        Collections.shuffle(shuffled);
        for (int i = 0; i < Math.min(k, shuffled.size()); i++) {
            centroids.add(shuffled.get(i));
        }

        List<Integer> assignments = new ArrayList<>(Collections.nCopies(pixels.size(), 0));

        for (int iter = 0; iter < maxIterations; iter++) {
            // 分配每个像素到最近的质心
            for (int i = 0; i < pixels.size(); i++) {
                double minDist = Double.MAX_VALUE;
                int nearest = 0;
                for (int j = 0; j < centroids.size(); j++) {
                    double dist = colorDistance(pixels.get(i), centroids.get(j));
                    if (dist < minDist) {
                        minDist = dist;
                        nearest = j;
                    }
                }
                assignments.set(i, nearest);
            }

            // 重新计算质心
            List<Color> newCentroids = new ArrayList<>();
            for (int j = 0; j < k; j++) {
                long r = 0, g = 0, b = 0;
                int count = 0;
                for (int i = 0; i < pixels.size(); i++) {
                    if (assignments.get(i) == j) {
                        Color c = pixels.get(i);
                        r += c.getRed();
                        g += c.getGreen();
                        b += c.getBlue();
                        count++;
                    }
                }
                if (count > 0) {
                    newCentroids.add(new Color((int)(r / count), (int)(g / count), (int)(b / count)));
                } else {
                    newCentroids.add(centroids.get(j));
                }
            }

            // 检查收敛
            boolean converged = true;
            for (int j = 0; j < k; j++) {
                if (colorDistance(centroids.get(j), newCentroids.get(j)) > 1) {
                    converged = false;
                    break;
                }
            }
            centroids = newCentroids;
            if (converged) {
                break;
            }
        }

        return centroids;
    }

    /**
     * 判断是否为背景色（白色或黑色）
     * 白色：RGB > 240
     * 黑色：RGB < 15
     */
    private static boolean isBackgroundColor(Color color) {
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        return (r > 240 && g > 240 && b > 240) || (r < 15 && g < 15 && b < 15);
    }

    /**
     * 计算两个颜色的欧氏距离
     */
    private static double colorDistance(Color c1, Color c2) {
        int dr = c1.getRed() - c2.getRed();
        int dg = c1.getGreen() - c2.getGreen();
        int db = c1.getBlue() - c2.getBlue();
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }
}
