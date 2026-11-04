package com.yuwen.visionspace.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 存储切换确认实例集合记录：每个实例对每个候选版本至多一条。
 *
 * <p>主键 (version, instanceId) + INSERT IGNORE 保证同一实例重复确认
 * （应用失败重试、进程重启后再次确认）不产生第二条记录，
 * 晋升判定以集合大小为准，不使用可虚增的次数计数器。
 * 仅经 XML 语句访问（复合主键不走 MyBatis Plus BaseMapper）。
 */
@Data
public class StorageSwitchConfirm implements Serializable {

    /**
     * 候选版本
     */
    private Long version;

    /**
     * 确认实例标识（进程启动生成）
     */
    private String instanceId;

    /**
     * 确认时的待生效配置 id（审计）
     */
    private Long configId;

    /**
     * 确认时间
     */
    private Date confirmTime;

    private static final long serialVersionUID = 1L;
}
