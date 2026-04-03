package com.yuwen.visionspace.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 图片分析结果 — AI 多模态模型对图片的结构化理解
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageAnalysisResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 图片内容的一句话概括
     */
    private String description;

    /**
     * 图片中的主色调列表
     */
    private List<String> colors;

    /**
     * 视觉风格描述
     */
    private String style;

    /**
     * 图片中的主体对象 / 元素
     */
    private List<String> subjects;

    /**
     * 场景氛围 / 情绪
     */
    private String mood;

    /**
     * 衍生的英文搜索关键词（可直接用于 searchContentImages）
     */
    private String searchKeywords;
}
