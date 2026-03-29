package com.yuwen.visionspace.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yuwen.visionspace.model.dto.storage.StorageConfigActiveRequest;
import com.yuwen.visionspace.model.dto.storage.StorageConfigQueryRequest;
import com.yuwen.visionspace.model.entity.StorageConfig;
import com.yuwen.visionspace.model.vo.StorageConfigVO;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

public interface StorageConfigService extends IService<StorageConfig> {

    /**
     * 添加存储平台配置
     */
    long addStorageConfig(StorageConfig storageConfig);

    /**
     * 更新存储平台配置
     */
    boolean updateStorageConfig(StorageConfig storageConfig);

    /**
     * 删除存储平台配置
     */
    boolean deleteStorageConfig(long id);

    /**
     * 设置激活平台
     */
    boolean setActivePlatform(StorageConfigActiveRequest request);

    /**
     * 设置默认平台
     */
    boolean setDefaultPlatform(Long id);

    /**
     * 获取所有配置列表（凭证脱敏）
     */
    List<StorageConfigVO> listStorageConfigVO(StorageConfigQueryRequest queryRequest, HttpServletRequest request);

    /**
     * 获取当前激活的平台配置
     */
    StorageConfig getActiveStorageConfig();
}
