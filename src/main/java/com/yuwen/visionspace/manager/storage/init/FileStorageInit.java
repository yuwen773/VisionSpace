package com.yuwen.visionspace.manager.storage.init;

import com.yuwen.visionspace.manager.storage.StorageSwitchService;
import com.yuwen.visionspace.mapper.StorageSwitchStateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 启动时初始化存储运行时（#40）。
 *
 * <p>启动流程：保证切换状态单行存在 → 从持久配置完整构建候选快照并原子应用。
 * 构建或验证失败时保持空快照并显式报错：上传会被明确拒绝（无默认平台），
 * 不回退到任何默认凭据或跳过失败项的半快照。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileStorageInit {

    private final StorageSwitchStateMapper storageSwitchStateMapper;
    private final StorageSwitchService storageSwitchService;

    @PostConstruct
    public void init() {
        storageSwitchStateMapper.ensureRow();
        try {
            storageSwitchService.reloadPersistedSnapshot();
            log.info("存储平台初始化完成，version={}, defaultPlatform={}",
                    storageSwitchService.getStorageSwitchStatus().getActiveVersion(),
                    storageSwitchService.getStorageSwitchStatus().getLocalDefaultPlatform());
        } catch (Exception e) {
            // 失败保留空快照：显式拒绝上传，等待管理员修复配置后执行刷新
            log.error("存储平台初始化失败，运行时保持空快照，上传将被拒绝直至配置修复", e);
        }
    }
}
