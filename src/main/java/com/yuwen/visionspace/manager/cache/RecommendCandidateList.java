package com.yuwen.visionspace.manager.cache;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 推荐候选列表（缓存条目）。
 *
 * <p>候选列表按「类型 + 分页上下文」缓存一份，翻页时只做内存切片：
 * 不会再因为每页缓存未命中而重新加载并保存全量结果。
 * 候选池有界；对外报告的总数不放在这里，始终按公开图库的真实数量现算。
 */
@Data
public class RecommendCandidateList implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分页上下文：同一上下文里的分页结果不重复、不遗漏 */
    private String context;

    /** 有界候选的图片 id（已按类型排好序） */
    private List<Long> pictureIds;

    public RecommendCandidateList() {
    }

    public RecommendCandidateList(String context, List<Long> pictureIds) {
        this.context = context;
        this.pictureIds = pictureIds;
    }
}
