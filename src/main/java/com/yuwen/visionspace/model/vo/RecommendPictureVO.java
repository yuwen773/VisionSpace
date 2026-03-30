package com.yuwen.visionspace.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 首页瀑布流推荐图片视图
 */
@Data
public class RecommendPictureVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 图片ID
     */
    private Long id;

    /**
     * 缩略图 url
     */
    private String thumbnailUrl;

    /**
     * 图片名称
     */
    private String name;

    /**
     * 图片宽度
     */
    private Integer picWidth;

    /**
     * 图片高度
     */
    private Integer picHeight;

    /**
     * 宽高比
     */
    private Double picScale;

    /**
     * 主色调
     */
    private String picColor;

    /**
     * 标签
     */
    private List<String> tags;
}
