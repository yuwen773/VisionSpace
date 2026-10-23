package com.yuwen.visionspace.model.vo;

import lombok.Data;

/**
 * 行为事件的待处理积压（脱敏：只含数量，不含用户、图片或事件内容）。
 */
@Data
public class PictureStatsBacklogVO {

    /** 待处理事件数：既未计入统计也未完成 */
    private long pendingCount;

    /** 已计入统计但未完成的事件数：来自累计之后、完成之前的中断 */
    private long accumulatedPendingCount;

    /** 已被领取且租约仍有效的事件数 */
    private long claimedCount;

    /** 反复领取仍失败的事件数 */
    private long stuckCount;

    /** 目标图片已存在性缺失却仍未处理的事件数：冲突清单的一部分 */
    private long orphanCount;
}
