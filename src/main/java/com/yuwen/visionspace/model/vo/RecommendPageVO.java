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
}
