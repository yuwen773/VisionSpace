package com.yuwen.visionspace.agent.recovery;

/**
 * 一次完整状态写入的结果。
 *
 * <p>必须区分「被更新的版本取代」「执行所有权已更替」与「真的写不进去」：
 * 前两者说明共享状态已经走到前面，本次载荷不应再写（后者才需要留 backupError）；
 * 区别在于版本取代可以对齐重试，世代更替的重试毫无意义——重试的仍是旧执行的
 * 载荷，重新编号只会把「迟到」伪装成「最新」。
 */
public enum StateSaveResult {
    /** 已写入 */
    SAVED,
    /** 版本不够新：库里有更新的状态，本次写入没有生效（同世代下可对齐重试） */
    SUPERSEDED,
    /** 执行世代已更替：本载荷属于被取代的旧执行，重试也无法也绝不应生效 */
    LOST_OWNERSHIP,
    /** 写入失败（异常或归属不符），原因已记录在 backupError */
    FAILED
}
