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
 * 已占位但未被全部期望实例确认，期间可安全回滚。
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
     * 已确认实例数
     */
    private Integer confirmCount;

    /**
     * 期望确认实例数
     */
    private Integer expectedInstances;

    private Date createTime;

    private Date updateTime;

    private static final long serialVersionUID = 1L;
}
