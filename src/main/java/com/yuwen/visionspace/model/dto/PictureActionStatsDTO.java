package com.yuwen.visionspace.model.dto;

import lombok.Data;

/**
 * 图片行为聚合统计结果 DTO
 * 用于接收分组统计结果
 */
@Data
public class PictureActionStatsDTO {
    /**
     * 图片ID
     */
    private Long pictureId;

    /**
     * 曝光次数
     */
    private Long impressionCount;

    /**
     * 点击次数
     */
    private Long clickCount;

    /**
     * 浏览次数
     */
    private Long viewCount;

    /**
     * 点赞次数
     */
    private Long likeCount;

    /**
     * 收藏次数
     */
    private Long collectCount;

    /**
     * 下载次数
     */
    private Long downloadCount;

    /**
     * 分享次数
     */
    private Long shareCount;
}
