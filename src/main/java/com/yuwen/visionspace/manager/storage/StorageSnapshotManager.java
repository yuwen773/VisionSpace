package com.yuwen.visionspace.manager.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.platform.FileStorage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 运行时存储快照：持有当前生效的平台集合、默认平台与版本号。
 *
 * <p>切换/刷新不直接清空平台列表（先清后建会把在途任务暴露在半空快照上），
 * 而是把完整候选快照一次性应用：同名平台原位原子替换，新增平台先追加，
 * 失效平台最后移除——按平台名查找在任何时刻都不会落空。
 * 资源的读取/删除始终按资源记录里的原平台定位，与默认平台无关。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class StorageSnapshotManager {

    /**
     * 当前生效快照：版本号 + 默认平台 + 已注册平台名集合。
     */
    public record Snapshot(long version, String defaultPlatform, List<String> platforms) {
    }

    private final FileStorageService fileStorageService;

    private final AtomicReference<Snapshot> current =
            new AtomicReference<>(new Snapshot(0, null, List.of()));

    /**
     * 原子应用候选快照。调用前候选必须已经完整构建并验证通过。
     *
     * @param storages        候选平台客户端集合
     * @param defaultPlatform 新的默认上传平台（可为 null，表示显式的无默认状态）
     * @param version         快照版本号
     */
    public synchronized void apply(List<FileStorage> storages, String defaultPlatform, long version) {
        CopyOnWriteArrayList<FileStorage> list = fileStorageService.getFileStorageList();
        Set<String> candidateNames = storages.stream()
                .map(FileStorage::getPlatform)
                .collect(Collectors.toUnmodifiableSet());
        // 同名平台原位替换：切换前后都保持可按名定位，在途任务不会被落空
        for (FileStorage storage : storages) {
            int index = indexOfPlatform(list, storage.getPlatform());
            if (index >= 0) {
                list.set(index, storage);
            } else {
                list.add(storage);
            }
        }
        // 只移除候选快照中已不存在的平台（管理端显式禁用或删除的配置）
        list.removeIf(storage -> !candidateNames.contains(storage.getPlatform()));
        fileStorageService.getProperties().setDefaultPlatform(defaultPlatform);
        current.set(new Snapshot(version, defaultPlatform, List.copyOf(candidateNames)));
        log.info("存储快照已生效: version={}, defaultPlatform={}, platforms={}",
                version, defaultPlatform, candidateNames);
    }

    public Snapshot currentSnapshot() {
        return current.get();
    }

    public long currentVersion() {
        return current.get().version();
    }

    private int indexOfPlatform(List<FileStorage> list, String platform) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getPlatform().equals(platform)) {
                return i;
            }
        }
        return -1;
    }
}
