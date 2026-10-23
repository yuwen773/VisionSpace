package com.yuwen.visionspace.model.vo;

import lombok.Data;

/**
 * 一轮行为聚合的结果（脱敏：只含数量）。
 */
@Data
public class PictureStatsAggregateResultVO {

    /** 本轮领取到的事件数 */
    private int claimedCount;

    /** 本轮计入统计的事件数 */
    private int accumulatedCount;

    /** 本轮标记完成的事件数 */
    private int completedCount;

    /** 收敛的「已计入未完成」事件数：来自上一次运行在累计之后中断 */
    private int recoveredCount;

    /** 清理掉的已处理历史事件数 */
    private int deletedCount;
}
