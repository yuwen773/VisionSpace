package com.yuwen.visionspace.utils;

import com.yuwen.visionspace.config.RecommendConfig;
import com.yuwen.visionspace.model.entity.Picture;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 首页推荐评分计算器
 */
@Component
public class HomeRecommendScoreCalculator {

    // 权重配置
    @Resource
    private RecommendConfig recommendConfig;

    // 互动权重细分
    private static final double VIEW_WEIGHT = 0.15;
    private static final double LIKE_WEIGHT = 0.15;
    private static final double COLLECT_WEIGHT = 0.15;
    private static final double DOWNLOAD_WEIGHT = 0.15;
    private static final double SHARE_WEIGHT = 0.10;

    /**
     * 计算推荐评分
     */
    public double calculateScore(Picture picture) {
        double engagementScore = calculateEngagementScore(picture);
        double freshnessScore = calculateFreshnessScore(picture);
        double qualityScore = calculateQualityScore(picture);
        double conversionScore = calculateConversionScore(picture);
        double manualScore = calculateManualScore(picture);

        return recommendConfig.getEngagementWeight() * engagementScore
             + recommendConfig.getFreshnessWeight() * freshnessScore
             + recommendConfig.getQualityWeight() * qualityScore
             + recommendConfig.getConversionWeight() * conversionScore
             + recommendConfig.getManualWeight() * manualScore;
    }

    /**
     * 互动评分 (对数平滑后的加权求和)
     */
    private double calculateEngagementScore(Picture picture) {
        double viewScore = Math.log1p(picture.getViewCount() == null ? 0 : picture.getViewCount()) * VIEW_WEIGHT;
        double likeScore = Math.log1p(picture.getLikeCount() == null ? 0 : picture.getLikeCount()) * LIKE_WEIGHT;
        double collectScore = Math.log1p(picture.getCollectCount() == null ? 0 : picture.getCollectCount()) * COLLECT_WEIGHT;
        double downloadScore = Math.log1p(picture.getDownloadCount() == null ? 0 : picture.getDownloadCount()) * DOWNLOAD_WEIGHT;
        double shareScore = Math.log1p(picture.getShareCount() == null ? 0 : picture.getShareCount()) * SHARE_WEIGHT;

        // 归一化到 [0, 1] (假设最大 log1p 值约为 15)
        double rawScore = viewScore + likeScore + collectScore + downloadScore + shareScore;
        return rawScore / 15.0;
    }

    /**
     * 新鲜度评分 (指数衰减)
     * 新图片得分高，老图片得分低
     */
    private double calculateFreshnessScore(Picture picture) {
        if (picture.getCreateTime() == null) {
            return 0.0;
        }
        LocalDateTime now = LocalDateTime.now();
        Instant createInstant = picture.getCreateTime().toInstant();
        LocalDateTime createTime = createInstant.atZone(ZoneId.systemDefault()).toLocalDateTime();
        long hours = Duration.between(createTime, now).toHours();
        // e^(-rate * hours), hours=0 时为 1, hours=24 时约为 0.3
        return Math.exp(-recommendConfig.getTimeDecayRate() * hours);
    }

    /**
     * 质量评分 (基于图片尺寸和格式)
     */
    private double calculateQualityScore(Picture picture) {
        if (picture.getPicWidth() == null || picture.getPicHeight() == null) {
            return 0.5;
        }
        // 基于分辨率的质量评估 (1080p 为基准)
        long pixels = picture.getPicWidth() * picture.getPicHeight();
        double score = Math.min(1.0, pixels / (1920.0 * 1080.0));
        // 图片格式加权 (webp/png 优于 jpg/jpeg)
        if (picture.getPicFormat() != null) {
            String format = picture.getPicFormat().toLowerCase();
            if ("webp".equals(format) || "png".equals(format)) {
                score = Math.min(1.0, score + 0.1);
            }
        }
        return score;
    }

    /**
     * 转化评分 (基于 CTR)
     */
    private double calculateConversionScore(Picture picture) {
        if (picture.getImpressionCount() == null || picture.getImpressionCount() == 0) {
            return 0.5; // 默认中等转化率
        }
        long clicks = (picture.getLikeCount() == null ? 0 : picture.getLikeCount())
                    + (picture.getCollectCount() == null ? 0 : picture.getCollectCount())
                    + (picture.getShareCount() == null ? 0 : picture.getShareCount());
        double ctr = (double) clicks / picture.getImpressionCount();
        return Math.min(1.0, ctr * 10); // CTR 10% 为满分
    }

    /**
     * 人工评分 (预留字段, 管理员可以手动调整)
     */
    private double calculateManualScore(Picture picture) {
        // TODO: 后续实现管理员手动评分功能
        return 0.5;
    }
}