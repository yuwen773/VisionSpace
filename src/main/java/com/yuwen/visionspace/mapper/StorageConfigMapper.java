package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.StorageConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StorageConfigMapper extends BaseMapper<StorageConfig> {

    /**
     * 当前持久激活的平台配置（isActive = 1，至多一条）。
     */
    StorageConfig selectActiveConfig();

    /**
     * 全部启用（status = 1）的平台配置，按 orderNum 排序。
     */
    List<StorageConfig> selectEnabledConfigs();

    /**
     * 切换晋升事务内使用：把其他平台取消激活（幂等）。
     */
    int deactivateOtherConfigs(@Param("targetConfigId") Long targetConfigId);

    /**
     * 切换晋升事务内使用：激活目标平台（幂等，仅启用中的配置可激活）。
     */
    int activateConfig(@Param("targetConfigId") Long targetConfigId);
}
