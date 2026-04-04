package com.yuwen.visionspace.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户图片统计响应
 */
@Data
public class UserPictureStatsResponse implements Serializable {

    /**
     * 上传数量
     */
    private Long uploadCount;

    /**
     * 收藏数量（暂无实现，预留）
     */
    private Long likeCount;

    /**
     * 审核通过数量
     */
    private Long reviewPassCount;

    /**
     * 审核通过率
     */
    private Double reviewPassRate;

    private static final long serialVersionUID = 1L;
}
