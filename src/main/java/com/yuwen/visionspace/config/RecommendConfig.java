package com.yuwen.visionspace.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "vision-space.recommend")
public class RecommendConfig {

    /** 用户互动权重（如点赞、评论、分享等） */
    private double engagementWeight = 0.40;
    /** 内容新鲜度权重（基于发布时间的衰减） */
    private double freshnessWeight = 0.20;
    /** 内容质量权重（基于算法评分或专家打分） */
    private double qualityWeight = 0.20;
    /** 转化行为权重（如点击详情、购买、关注等） */
    private double conversionWeight = 0.10;
    /** 人工干预权重（运营手动调整推荐排序） */
    private double manualWeight = 0.10;
    /** 时间衰减速率（用于计算新鲜度随时间降低的系数） */
    private double timeDecayRate = 0.05;
    /** 推荐结果缓存存活时间（单位：分钟） */
    private int cacheTtlMinutes = 5;
}
