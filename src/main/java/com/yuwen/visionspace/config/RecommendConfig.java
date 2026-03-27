package com.yuwen.visionspace.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "vision-space.recommend")
public class RecommendConfig {

    private double engagementWeight = 0.40;
    private double freshnessWeight = 0.20;
    private double qualityWeight = 0.20;
    private double conversionWeight = 0.10;
    private double manualWeight = 0.10;
    private double timeDecayRate = 0.05;
    private int cacheTtlMinutes = 5;
}
