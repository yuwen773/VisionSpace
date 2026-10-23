package com.yuwen.visionspace.model.vo;

import lombok.Data;

/**
 * 行为上报的可观测计数（脱敏：只含数量，不含用户、IP 或事件内容）。
 */
@Data
public class PictureActionReportMetricsVO {

    /** 成功受理的新事件数 */
    private long acceptedCount;

    /** 因幂等键命中而被丢弃的重复事件数 */
    private long duplicateCount;

    /** 参数、可见性或越权字段导致的拒绝数 */
    private long rejectedCount;

    /** 触发限流被拒绝的次数 */
    private long rateLimitedCount;
}
