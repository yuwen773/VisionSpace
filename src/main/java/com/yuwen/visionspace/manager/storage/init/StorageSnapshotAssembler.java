package com.yuwen.visionspace.manager.storage.init;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.manager.storage.StorageCandidateVerifier;
import com.yuwen.visionspace.mapper.StorageConfigMapper;
import com.yuwen.visionspace.model.entity.StorageConfig;
import lombok.RequiredArgsConstructor;
import org.dromara.x.file.storage.core.platform.FileStorage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 候选快照组装：把持久配置完整构建为可验证的平台客户端集合。
 *
 * <p>任一配置构建或验证失败都让整体失败（抛出异常），调用方因此保留
 * 当前运行时快照——禁止「跳过失败项继续生效其余部分」的半快照切换。
 */
@Component
@RequiredArgsConstructor
public class StorageSnapshotAssembler {

    /**
     * 候选快照：平台客户端集合 + 默认上传平台。
     */
    public record Candidate(List<FileStorage> storages, String defaultPlatform) {
    }

    private final StorageConfigMapper storageConfigMapper;
    private final StorageCandidateVerifier verifier;

    /**
     * 完整构建候选快照。
     *
     * @param targetPlatformOverride 切换目标平台；null 表示沿用当前持久激活平台
     * @return 候选快照（不可变）
     */
    public Candidate assemble(String targetPlatformOverride) {
        List<StorageConfig> enabled = storageConfigMapper.selectEnabledConfigs();
        if (enabled == null) {
            enabled = List.of();
        }
        List<FileStorage> storages = enabled.stream()
                .map(verifier::buildVerified)
                .toList();
        String defaultPlatform = enabled.stream()
                .filter(config -> config.getIsActive() != null && config.getIsActive() == 1)
                .map(StorageConfig::getPlatform)
                .findFirst()
                .orElse(null);
        if (targetPlatformOverride != null) {
            // 目标平台必须真实出现在候选里，防止验证与占位之间配置被禁用造成空默认
            boolean targetPresent = storages.stream()
                    .anyMatch(storage -> Objects.equals(storage.getPlatform(), targetPlatformOverride));
            if (!targetPresent) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "切换目标平台不在启用配置中: " + targetPlatformOverride);
            }
            defaultPlatform = targetPlatformOverride;
        }
        return new Candidate(List.copyOf(storages), defaultPlatform);
    }
}
