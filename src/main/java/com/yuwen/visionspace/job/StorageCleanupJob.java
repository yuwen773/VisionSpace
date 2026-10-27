package com.yuwen.visionspace.job;

import com.yuwen.visionspace.model.entity.StorageCleanupTask;
import com.yuwen.visionspace.service.StorageCleanupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 对象存储清理任务的重试。
 *
 * <p>上传失败回收、替换旧对象、删除图片后的对象清理，只要删除没有被确认，
 * 就会留下一条待处理任务。这里按退避时间重试，并把结果收敛到
 * 已确认删除或等待人工处理两种终态——失败不会静默消失。
 */
@Slf4j
@Component
public class StorageCleanupJob {

    /** 单批上限，避免一次任务持有过多行锁 */
    private static final int BATCH_SIZE = 100;

    @Resource
    private StorageCleanupService storageCleanupService;

    @Scheduled(fixedRate = 900000, initialDelay = 600000)
    public void retryPendingDeletes() {
        List<StorageCleanupTask> due = storageCleanupService.listDue(BATCH_SIZE);
        if (due.isEmpty()) {
            return;
        }
        int confirmed = 0;
        for (StorageCleanupTask task : due) {
            try {
                if (storageCleanupService.retryOne(task.getId())) {
                    confirmed++;
                }
            } catch (Exception e) {
                // 单条失败不影响其余任务：任务仍在库里，下一轮继续
                log.warn("重试存储清理任务失败: id={}, key={}", task.getId(), task.getObjectKey(), e);
            }
        }
        log.info("重试存储清理任务: 到期={}, 已确认={}", due.size(), confirmed);
    }
}
