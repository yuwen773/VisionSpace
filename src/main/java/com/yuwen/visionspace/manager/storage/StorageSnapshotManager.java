package com.yuwen.visionspace.manager.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.platform.FileStorage;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 运行时存储快照：权威记录当前生效的平台集合、默认平台与版本号。
 *
 * <p>本组件持有的不可变 {@link Snapshot} 经 {@link AtomicReference} 原子换引用，
 * 是版本对账与状态上报的唯一事实来源。x-file-storage 自身按平台名在
 * {@code FileStorageService} 内部的 final CopyOnWriteArrayList 上做查找，
 * 该字段无法整体换引用，因此列表迁移采用「分阶段、每步对读者原子」的顺序：
 *
 * <ol>
 *   <li>先让候选快照里的每个平台在列表中在位（同名平台原位原子替换，新平台追加）
 *       ——此后任何按名查找都不会落空，旧客户端与新客户端都是验证过的可用实例；</li>
 *   <li>默认平台此时才切换（新默认必然已在位）；</li>
 *   <li>最后移除候选之外的平台——只可能是管理端显式禁用/删除的配置，
 *       其查找失败正是期望的最终状态。</li>
 * </ol>
 *
 * <p>任何中间态下：候选内平台按名查找必中，候选外平台至多延迟一步消失，
 * 不存在「被移除平台在窗口内查找落空而候选平台也不可用」的半快照。
 * 切换期间上传在任务开始时已钉住平台（见 PictureStorageService），
 * 在途任务按原平台完成。
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
     * 应用候选快照。调用前候选必须已经完整构建并验证通过，
     * 且平台名互不重复（持久层 uk_platform 约束）。
     *
     * @param storages        候选平台客户端集合
     * @param defaultPlatform 新的默认上传平台（可为 null，表示显式的无默认状态）
     * @param version         快照版本号
     */
    public synchronized void apply(List<FileStorage> storages, String defaultPlatform, long version) {
        if (storages == null) {
            storages = List.of();
        }
        CopyOnWriteArrayList<FileStorage> list = fileStorageService.getFileStorageList();
        Set<String> candidateNames = new LinkedHashSet<>();
        // 阶段 1：候选平台全部在位（同名原位替换 / 新平台追加），查找永不落空
        for (FileStorage storage : storages) {
            if (!candidateNames.add(storage.getPlatform())) {
                throw new IllegalStateException("候选快照存在重复平台: " + storage.getPlatform());
            }
            int index = indexOfPlatform(list, storage.getPlatform());
            if (index >= 0) {
                list.set(index, storage);
            } else {
                list.add(storage);
            }
        }
        // 阶段 2：新默认平台已在位，切换默认
        fileStorageService.getProperties().setDefaultPlatform(defaultPlatform);
        // 阶段 3：最后移除候选之外的平台（显式禁用/删除的配置）
        list.removeIf(storage -> !candidateNames.contains(storage.getPlatform()));
        // 权威快照最后原子换引用
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
