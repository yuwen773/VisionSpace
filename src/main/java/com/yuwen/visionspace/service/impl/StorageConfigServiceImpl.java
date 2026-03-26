package com.yuwen.visionspace.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuwen.visionspace.constant.UserConstant;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.mapper.StorageConfigMapper;
import com.yuwen.visionspace.model.dto.storage.StorageConfigActiveRequest;
import com.yuwen.visionspace.model.dto.storage.StorageConfigQueryRequest;
import com.yuwen.visionspace.model.entity.StorageConfig;
import com.yuwen.visionspace.model.vo.StorageConfigVO;
import com.yuwen.visionspace.service.StorageConfigService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StorageConfigServiceImpl extends ServiceImpl<StorageConfigMapper, StorageConfig>
        implements StorageConfigService {

    @Override
    public long addStorageConfig(StorageConfig storageConfig) {
        // 同一时刻仅一个激活平台
        if (storageConfig.getIsActive() != null && storageConfig.getIsActive() == 1) {
            deactivateAll();
        }
        boolean result = this.save(storageConfig);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "添加存储配置失败");
        }
        return storageConfig.getId();
    }

    @Override
    public boolean updateStorageConfig(StorageConfig storageConfig) {
        if (storageConfig.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        StorageConfig oldConfig = this.getById(storageConfig.getId());
        if (oldConfig == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 切换激活平台
        if (storageConfig.getIsActive() != null && storageConfig.getIsActive() == 1) {
            deactivateAll();
        }
        boolean result = this.updateById(storageConfig);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新存储配置失败");
        }
        return true;
    }

    @Override
    public boolean deleteStorageConfig(long id) {
        StorageConfig config = this.getById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return this.removeById(id);
    }

    @Override
    public boolean setActivePlatform(StorageConfigActiveRequest request) {
        StorageConfig config = this.getById(request.getId());
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        if (config.getStatus() == 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该平台已禁用，请先启用");
        }
        deactivateAll();
        config.setIsActive(1);
        return this.updateById(config);
    }

    @Override
    public boolean setDefaultPlatform(Long id) {
        if (id == null) {
            return false;
        }
        StorageConfig config = this.getById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 清除所有默认标记
        List<StorageConfig> allConfigs = this.list();
        for (StorageConfig c : allConfigs) {
            if (c.getIsDefault() != null && c.getIsDefault() == 1 && !c.getId().equals(id)) {
                c.setIsDefault(0);
                this.updateById(c);
            }
        }
        config.setIsDefault(1);
        return this.updateById(config);
    }

    @Override
    public List<StorageConfigVO> listStorageConfigVO(StorageConfigQueryRequest queryRequest, HttpServletRequest request) {
        LambdaQueryWrapper<StorageConfig> wrapper = new LambdaQueryWrapper<>();
        if (queryRequest.getId() != null) {
            wrapper.eq(StorageConfig::getId, queryRequest.getId());
        }
        if (!StringUtils.isEmpty(queryRequest.getPlatform())) {
            wrapper.eq(StorageConfig::getPlatform, queryRequest.getPlatform());
        }
        if (queryRequest.getStatus() != null) {
            wrapper.eq(StorageConfig::getStatus, queryRequest.getStatus());
        }
        wrapper.orderByAsc(StorageConfig::getOrderNum);
        List<StorageConfig> list = this.list(wrapper);

        boolean isAdmin = StpUtil.hasRole(UserConstant.ADMIN_ROLE);
        return list.stream().map(config -> {
            StorageConfigVO vo = new StorageConfigVO();
            BeanUtils.copyProperties(config, vo);
            // 非管理员脱敏
            if (!isAdmin) {
                vo.setAccessKey(null);
                vo.setSecretKey(null);
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public StorageConfig getActiveStorageConfig() {
        return this.getOne(new LambdaQueryWrapper<StorageConfig>()
                .eq(StorageConfig::getIsActive, 1));
    }

    private void deactivateAll() {
        List<StorageConfig> activeList = this.list(
                new LambdaQueryWrapper<StorageConfig>().eq(StorageConfig::getIsActive, 1));
        for (StorageConfig c : activeList) {
            c.setIsActive(0);
            this.updateById(c);
        }
    }
}