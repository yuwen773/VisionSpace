package com.yuwen.visionspace.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理端仪表盘聚合统计数据
 */
@Data
public class AdminDashboardStatsVO implements Serializable {

    /**
     * 用户总数
     */
    private Long userCount;

    /**
     * 图片总数
     */
    private Long pictureCount;

    /**
     * 空间总数
     */
    private Long spaceCount;

    /**
     * 已使用存储大小（字节）
     */
    private Long usedSize;

    // ========== 推荐统计 ==========

    /**
     * 今日曝光量
     */
    private Long todayImpressionCount;

    /**
     * 今日点击量
     */
    private Long todayClickCount;

    /**
     * 今日点击率 CTR
     */
    private Double todayCtr;

    /**
     * 今日收藏量
     */
    private Long todayCollectCount;

    private static final long serialVersionUID = 1L;
}
