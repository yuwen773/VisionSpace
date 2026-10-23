package com.yuwen.visionspace.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 推荐分页包装对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendPageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 图片列表
     */
    private List<RecommendPictureVO> records;

    /**
     * 总数
     */
    private Long total;

    /**
     * 分页上下文：同一个上下文里的分页结果不重复、不遗漏。
     * 客户端翻页时带上这个值，就能拿到同一份有序结果的后续页。
     */
    private String context;

    public RecommendPageVO(List<RecommendPictureVO> records, Long total) {
        this(records, total, null);
    }
}
