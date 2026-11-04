package com.yuwen.visionspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.mapper.StorageConfigMapper;
import com.yuwen.visionspace.manager.storage.StorageCredentialMasker;
import com.yuwen.visionspace.model.dto.storage.StorageConfigQueryRequest;
import com.yuwen.visionspace.model.entity.StorageConfig;
import com.yuwen.visionspace.model.vo.StorageConfigVO;
import com.yuwen.visionspace.service.StorageConfigService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StorageConfigServiceImpl extends ServiceImpl<StorageConfigMapper, StorageConfig>
        implements StorageConfigService {

    @Override
    public long addStorageConfig(StorageConfig storageConfig) {
        // 新建时不允许把遮蔽值或占位文本当作真实密钥保存。
        rejectMaskedCredential(storageConfig.getAccessKey(), "AccessKey");
        rejectMaskedCredential(storageConfig.getSecretKey(), "SecretKey");
        // 激活平台必须走切换接口（候选验证 + 多实例确认），不允许绕过验证直接生效
        rejectDirectActivation(storageConfig.getIsActive());
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
        // 只替换被显式改动过的凭据；遮蔽值、空白与 null 保留库中原值。
        storageConfig.setAccessKey(resolveCredential(storageConfig.getAccessKey(), oldConfig.getAccessKey()));
        storageConfig.setSecretKey(resolveCredential(storageConfig.getSecretKey(), oldConfig.getSecretKey()));
        // 激活平台必须走切换接口（候选验证 + 多实例确认），不允许绕过验证直接生效
        rejectDirectActivation(storageConfig.getIsActive());
        boolean result = this.updateById(storageConfig);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新存储配置失败");
        }
        return true;
    }

    /**
     * 激活标记是切换的持久结果，只能由切换流程在晋升事务内变更；
     * 直接写入会造成「未经验证的平台成为默认」的旁路。
     */
    private void rejectDirectActivation(Integer isActive) {
        if (isActive != null && isActive == 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "激活平台请使用切换接口（候选验证后生效）");
        }
    }

    /**
     * 新建配置时，遮掩值与占位文本都不是可用的真实凭据，必须拒绝。
     */
    private void rejectMaskedCredential(String submitted, String field) {
        if (submitted != null && StorageCredentialMasker.MASK.equals(submitted.trim())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, field + " 不能使用遮蔽值");
        }
    }

    /**
     * 请求未要求替换时返回原值，避免将遮蔽文本或空占位写回真实配置。
     */
    private String resolveCredential(String submitted, String current) {
        return StorageCredentialMasker.isReplacement(submitted) ? submitted.trim() : current;
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

        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 逐字段映射，不拷贝凭据原值；AccessKey / SecretKey 只以遮蔽形式出现。
     */
    private StorageConfigVO toVO(StorageConfig config) {
        StorageConfigVO vo = new StorageConfigVO();
        vo.setId(config.getId());
        vo.setPlatform(config.getPlatform());
        vo.setPlatformName(config.getPlatformName());
        vo.setEndpoint(config.getEndpoint());
        vo.setRegion(config.getRegion());
        vo.setBucket(config.getBucket());
        vo.setDomain(config.getDomain());
        vo.setBasePath(config.getBasePath());
        vo.setIsActive(config.getIsActive());
        vo.setIsDefault(config.getIsDefault());
        vo.setOrderNum(config.getOrderNum());
        vo.setStatus(config.getStatus());
        vo.setCreateTime(config.getCreateTime());
        vo.setUpdateTime(config.getUpdateTime());
        vo.setAccessKeyMasked(StorageCredentialMasker.mask(config.getAccessKey()));
        vo.setSecretKeyMasked(StorageCredentialMasker.mask(config.getSecretKey()));
        vo.setHasAccessKey(config.getAccessKey() != null && !config.getAccessKey().isBlank());
        vo.setHasSecretKey(config.getSecretKey() != null && !config.getSecretKey().isBlank());
        return vo;
    }

    @Override
    public StorageConfig getActiveStorageConfig() {
        return this.getOne(new LambdaQueryWrapper<StorageConfig>()
                .eq(StorageConfig::getIsActive, 1));
    }
}
