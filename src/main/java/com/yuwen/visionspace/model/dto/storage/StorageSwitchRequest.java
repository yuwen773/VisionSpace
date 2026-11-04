package com.yuwen.visionspace.model.dto.storage;

import lombok.Data;

import java.io.Serializable;

/**
 * 存储平台切换请求。
 */
@Data
public class StorageSwitchRequest implements Serializable {

    /**
     * 切换目标配置 id
     */
    private Long targetConfigId;

    /**
     * 期望确认生效的实例数；单实例部署留空或 1，双实例部署填 2。
     * 确认数达到期望后切换才晋升为完整成功。
     */
    private Integer expectedInstances;

    private static final long serialVersionUID = 1L;
}
