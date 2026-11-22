package com.yuwen.visionspace.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 反馈待办统计。
 *
 * <p>管理端页头的「待处理 / 处理中 / 已解决」必须是全量口径，而不是当前页的记录数；
 * 因此由服务端聚合返回，前端不再从可见页自行累加。
 */
@Data
public class FeedbackStatsVO implements Serializable {

    /**
     * 待处理数量
     */
    private Long pending;

    /**
     * 处理中数量
     */
    private Long processing;

    /**
     * 已解决数量
     */
    private Long resolved;

    /**
     * 合计数量
     */
    private Long total;

    private static final long serialVersionUID = 1L;
}
