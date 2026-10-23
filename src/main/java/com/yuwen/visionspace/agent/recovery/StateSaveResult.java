package com.yuwen.visionspace.agent.recovery;

/**
 * 一次完整状态写入的结果。
 *
 * <p>必须区分「被更新的版本取代」与「真的写不进去」：前者说明本实例的版本号
 * 落后于共享持久状态，应当对齐后重试，不能把有效进度静默丢掉；后者才是备份故障。
 */
public enum StateSaveResult {
    /** 已写入 */
    SAVED,
    /** 版本不够新：库里有更新的状态，本次写入没有生效 */
    SUPERSEDED,
    /** 写入失败（异常或归属不符），原因已记录在 backupError */
    FAILED
}
