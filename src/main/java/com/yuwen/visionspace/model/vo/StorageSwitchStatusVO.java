package com.yuwen.visionspace.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 存储切换状态视图。
 *
 * <p>区分持久激活配置、全局生效版本与本实例生效版本；切换进行中时
 * 呈现待生效目标与各实例确认进度。不包含任何凭据字段。
 */
@Data
public class StorageSwitchStatusVO implements Serializable {

    /**
     * completed=切换完成（或无切换进行中）；pending=有切换待确认
     */
    private String phase;

    /**
     * 最近完整生效的快照版本（持久）
     */
    private Long activeVersion;

    /**
     * 待确认的候选版本；无切换进行中时为 null
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
     * 已确认实例数
     */
    private Integer confirmCount;

    /**
     * 期望确认实例数
     */
    private Integer expectedInstances;

    /**
     * 本实例当前生效的快照版本
     */
    private Long localEffectiveVersion;

    /**
     * 本实例是否已与持久生效版本对齐
     */
    private Boolean localSynced;

    /**
     * 本实例当前默认上传平台
     */
    private String localDefaultPlatform;

    /**
     * 本实例已注册的平台集合（旧平台资源核对用，不含凭据）
     */
    private List<String> registeredPlatforms;

    /**
     * 持久激活的平台（storage_config.isActive = 1）
     */
    private String activePlatform;

    /**
     * 持久激活配置 id
     */
    private Long activeConfigId;

    private static final long serialVersionUID = 1L;
}
