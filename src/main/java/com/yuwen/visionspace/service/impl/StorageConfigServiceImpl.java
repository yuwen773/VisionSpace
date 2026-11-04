package com.yuwen.visionspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.mapper.StorageConfigMapper;
import com.yuwen.visionspace.mapper.StorageSwitchStateMapper;
import com.yuwen.visionspace.manager.storage.StorageCandidateVerifier;
import com.yuwen.visionspace.manager.storage.StorageCredentialMasker;
import com.yuwen.visionspace.model.dto.storage.StorageConfigQueryRequest;
import com.yuwen.visionspace.model.entity.StorageConfig;
import com.yuwen.visionspace.model.entity.StorageSwitchState;
import com.yuwen.visionspace.model.vo.StorageConfigVO;
import com.yuwen.visionspace.service.StorageConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StorageConfigServiceImpl extends ServiceImpl<StorageConfigMapper, StorageConfig>
        implements StorageConfigService {

    private final StorageCandidateVerifier candidateVerifier;
    private final StorageSwitchStateMapper switchStateMapper;

    public StorageConfigServiceImpl(StorageCandidateVerifier candidateVerifier,
                                    StorageSwitchStateMapper switchStateMapper) {
        this.candidateVerifier = candidateVerifier;
        this.switchStateMapper = switchStateMapper;
    }

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
        // 生效中的配置不允许改平台标识或被停用：生效平台只能由切换流程变更
        boolean activeConfig = Objects.equals(oldConfig.getIsActive(), 1);
        if (activeConfig) {
            rejectActiveTargetChange(oldConfig, storageConfig);
        }
        // 生效中的配置改动了存储目标字段：合并后的候选先完整验证再放行持久化，
        // 坏端点/坏凭据在保存前显式失败；运行时应用仍走显式刷新（D20 运维交接）
        if (activeConfig && storageTargetChanged(oldConfig, storageConfig)) {
            StorageConfig merged = mergeSubmitted(oldConfig, storageConfig);
            try {
                candidateVerifier.buildVerified(merged);
            } catch (BusinessException e) {
                log.error("生效配置更新候选验证失败: platform={}, configId={}",
                        merged.getPlatform(), merged.getId(), e);
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "更新后的生效配置候选验证失败，本次修改未保存：" + e.getMessage());
            }
        }
        boolean result = this.updateById(storageConfig);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新存储配置失败");
        }
        return true;
    }

    /**
     * 生效中的配置不允许改平台标识或停用：
     * 平台变更等于换存储目标，必须新建配置并走切换流程；
     * 停用会让持久层失去激活配置而运行时仍在使用旧客户端。
     */
    private void rejectActiveTargetChange(StorageConfig oldConfig, StorageConfig submitted) {
        if (submitted.getPlatform() != null && !submitted.getPlatform().equals(oldConfig.getPlatform())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "生效中的配置不可更改平台，请新建配置并通过切换流程启用");
        }
        if (submitted.getStatus() != null && submitted.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "生效中的配置不可停用，请先切换到其他平台");
        }
    }

    /**
     * 判断是否改动了存储目标字段（平台定位/凭据）。改动了就必须先通过候选验证。
     */
    private boolean storageTargetChanged(StorageConfig oldConfig, StorageConfig submitted) {
        return fieldChanged(oldConfig.getEndpoint(), submitted.getEndpoint())
                || fieldChanged(oldConfig.getRegion(), submitted.getRegion())
                || fieldChanged(oldConfig.getBucket(), submitted.getBucket())
                || fieldChanged(oldConfig.getBasePath(), submitted.getBasePath())
                || fieldChanged(oldConfig.getDomain(), submitted.getDomain())
                || fieldChanged(oldConfig.getAccessKey(), submitted.getAccessKey())
                || fieldChanged(oldConfig.getSecretKey(), submitted.getSecretKey());
    }

    private boolean fieldChanged(String oldValue, String newValue) {
        return newValue != null && !newValue.equals(oldValue);
    }

    /**
     * 合并提交与库中原值，得到更新后的完整配置用于候选验证。
     */
    private StorageConfig mergeSubmitted(StorageConfig oldConfig, StorageConfig submitted) {
        StorageConfig merged = new StorageConfig();
        merged.setId(oldConfig.getId());
        merged.setPlatform(oldConfig.getPlatform());
        merged.setPlatformName(submitted.getPlatformName() != null
                ? submitted.getPlatformName() : oldConfig.getPlatformName());
        merged.setEndpoint(submitted.getEndpoint() != null ? submitted.getEndpoint() : oldConfig.getEndpoint());
        merged.setRegion(submitted.getRegion() != null ? submitted.getRegion() : oldConfig.getRegion());
        merged.setBucket(submitted.getBucket() != null ? submitted.getBucket() : oldConfig.getBucket());
        merged.setBasePath(submitted.getBasePath() != null ? submitted.getBasePath() : oldConfig.getBasePath());
        merged.setDomain(submitted.getDomain() != null ? submitted.getDomain() : oldConfig.getDomain());
        merged.setAccessKey(submitted.getAccessKey() != null ? submitted.getAccessKey() : oldConfig.getAccessKey());
        merged.setSecretKey(submitted.getSecretKey() != null ? submitted.getSecretKey() : oldConfig.getSecretKey());
        merged.setStatus(oldConfig.getStatus());
        return merged;
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
        // 生效中的配置不可删除：删除会造成持久层无激活而运行时仍用旧客户端，
        // 必须先切换到其他平台
        if (Objects.equals(config.getIsActive(), 1)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "生效中的存储配置不可删除，请先切换到其他平台");
        }
        // 进行中切换的目标也不可删除：目标消失会让确认路径失败，需先回滚
        StorageSwitchState state = switchStateMapper.selectById(1L);
        if (state != null && state.getPendingVersion() != null
                && Objects.equals(state.getPendingConfigId(), id)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "该配置是进行中切换的目标，请先回滚本次切换");
        }
        return this.removeById(id);
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
