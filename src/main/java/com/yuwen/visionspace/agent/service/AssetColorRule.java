package com.yuwen.visionspace.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.utils.ColorSimilarUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/** 只有显式配置的版本化标定规则才能启用颜色筛选。 */
@Component
public class AssetColorRule {
    public record Rule(String version, String blueAnchor, String purpleAnchor, double threshold) {
        public double score(String color) {
            return Math.max(ColorSimilarUtils.calculateSimilarity(color, blueAnchor),
                    ColorSimilarUtils.calculateSimilarity(color, purpleAnchor));
        }
    }

    private final Rule rule;

    public AssetColorRule(ResourceLoader resources, ObjectMapper json,
                          @Value("${vision-space.asset-search.color-rule-location:}") String location) {
        Rule loaded = null;
        if (!location.isBlank()) {
            try (var input = resources.getResource(location).getInputStream()) {
                var node = json.readTree(input);
                String version = node.path("version").asText();
                String blue = normalize(node.path("blueAnchor").asText());
                String purple = normalize(node.path("purpleAnchor").asText());
                double threshold = node.path("threshold").asDouble(Double.NaN);
                if (node.isObject() && node.size() == 4 && version.matches("[a-zA-Z0-9.-]{1,80}")
                        && blue != null && purple != null && node.path("threshold").isNumber()
                        && Double.isFinite(threshold) && threshold > 0 && threshold <= 1) {
                    loaded = new Rule(version, blue, purple, threshold);
                }
            } catch (java.io.IOException ignored) {
                // 缺失/损坏配置只关闭颜色入口，不回退为无阈值 Top-K。
            }
        }
        rule = loaded;
    }

    public Rule requireReady() {
        ThrowUtils.throwIf(rule == null, ErrorCode.OPERATION_ERROR, "RULE_NOT_READY");
        return rule;
    }

    static String normalize(String color) {
        if (color == null || !color.matches("(?i)(#|0x)[0-9a-f]{6}")) return null;
        return "#" + color.substring(color.length() - 6).toUpperCase(java.util.Locale.ROOT);
    }
}
