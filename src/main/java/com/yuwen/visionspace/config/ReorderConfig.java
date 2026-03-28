package com.yuwen.visionspace.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 推荐重打散配置
 */
@Component
@ConfigurationProperties(prefix = "vision-space.recommend.reorder")
@Data
public class ReorderConfig {

    /**
     * 作者打散窗口大小
     */
    private int authorWindowSize = 20;

    /**
     * 窗口内同作者最大数量
     */
    private int authorMaxCount = 2;

    /**
     * 最大连续同分类数
     */
    private int categoryMaxConsecutive = 3;
}
