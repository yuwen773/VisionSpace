package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 存储切换版本状态（单行，id = 1）。
 *
 * <p>持久配置状态（storage_config.isActive）与各实例运行快照由本行衔接：
 * activeVersion 为最近完整生效的快照版本；pendingVersion 非空表示有切换
 * 已占位但未被全部期望实例确认，期间可安全回滚。确认进度不在本行计数，
 * 而由 storage_switch_confirm 的确认实例集合决定（幂等，杜绝虚增）。
 */
@Data
@TableName(value = "storage_switch_state")
public class StorageSwitchState implements Serializable {

    /**
     * 固定为 1 的单行
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 最近完整生效的快照版本
     */
    private Long activeVersion;

    /**
     * 待确认的候选版本（NULL 表示当前没有进行中的切换）
     */
    private Long pendingVersion;

    /**
     * 待生效配置 id
     */
    private Long pendingConfigId;

    /**
     * 待生效平台
     */
    private String pendingPlatform;

    /**
     * 切换占位时间
     */
    private Date pendingTime;

    /**
     * 本次占位的一次性令牌（占位时由 SQL 生成）：确认写入用它做原子条件校验，
     * 防止跨 abort/再占位存活的旧确认线程把确认行落进复用同版本号的新尝试
     */
    private String pendingToken;

    /**
     * 期望确认实例数
     */
    private Integer expectedInstances;

    private Date createTime;

    private Date updateTime;

    private static final long serialVersionUID = 1L;
}
