package com.yuwen.visionspace.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
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
     * 总数。
     *
     * <p>序列化后是十进制字符串：平台的 JSON 规则把 Long 序列化成字符串，
     * 避免 JavaScript 数字精度丢失。契约里也如实标成字符串，客户端按字符串解析。
     */
    @Schema(type = "string", description = "总数：十进制字符串，避免 JavaScript 数字精度丢失")
    private Long total;

    /**
     * 分页上下文：同一个上下文里的分页结果不重复、不遗漏。
     * 客户端翻页时带上这个值，就能拿到同一份有序结果的后续页。
     */
    private String context;

    /**
     * 客户端带来的分页上下文是否仍然有效。
     *
     * <p>为 false 时表示这份上下文已经结束（候选被刷新或到期，原来的页码不再
     * 对应任何候选），响应里的记录是当前候选的第一页，客户端应从新的一份结果
     * 重新开始，而不是继续按旧页码往后翻。
     */
    private Boolean contextValid;

    public RecommendPageVO(List<RecommendPictureVO> records, Long total, String context) {
        this(records, total, context, Boolean.TRUE);
    }
}
