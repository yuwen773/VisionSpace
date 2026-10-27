package com.yuwen.visionspace.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuwen.visionspace.manager.storage.PictureStorageService;
import com.yuwen.visionspace.mapper.PictureFileMapper;
import com.yuwen.visionspace.mapper.StorageCleanupTaskMapper;
import com.yuwen.visionspace.model.entity.PictureFile;
import com.yuwen.visionspace.model.entity.StorageCleanupTask;
import com.yuwen.visionspace.service.StorageCleanupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.List;

/**
 * 对象存储清理任务。
 *
 * <p>删除动作本身不可回滚，因此每次重试之前都重新核对引用，而不是沿用登记时的
 * 判断：资源记录可能在上次失败之后被重新绑定或重新变为有效，同一个对象键也可能
 * 被多条记录引用。核对不通过就阻断，交给人工处理——宁可留下一个可观察的
 * 待处理任务，也不按猜测删除别人的对象。
 */
@Slf4j
@Service
public class StorageCleanupServiceImpl extends ServiceImpl<StorageCleanupTaskMapper, StorageCleanupTask>
        implements StorageCleanupService {

    /** 首次重试延迟 */
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofMinutes(5);

    /** 退避上限：超过一天仍在失败的任务应当人工介入，而不是无限重试 */
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(24);

    @Resource
    private PictureStorageService pictureStorageService;

    @Resource
    private PictureFileMapper pictureFileMapper;

    @Override
    public void enqueue(PictureFile file, String key, String reason) {
        if (key == null || key.isBlank()) {
            return;
        }
        String platform = file == null ? null : file.getPlatform();
        String kind = file == null ? null : file.getKind();
        try {
            baseMapper.insertPending(file == null ? null : file.getId(),
                    file == null ? null : file.getPictureId(),
                    platform, key, kind, reason);
        } catch (Exception e) {
            // 登记失败不能让业务回滚：对象已经存在，日志里保留定位信息供人工处理
            log.error("清理任务登记失败: platform={}, key={}, reason={}", platform, key, reason, e);
        }
    }

    /**
     * 记录一条只用于人工核对的任务：存量对象只有地址、没有平台与对象键时，
     * 自动重试会把对象永久阻断在待处理状态，这里直接登记为等待人工处理。
     */
    @Override
    public void enqueueForReview(String url, String reason) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            baseMapper.insertPending(null, null, null, url, null, reason);
            StorageCleanupTask task = this.lambdaQuery()
                    .eq(StorageCleanupTask::getObjectKey, url)
                    .eq(StorageCleanupTask::getStatus, StorageCleanupTask.STATUS_PENDING)
                    .orderByDesc(StorageCleanupTask::getId)
                    .last("LIMIT 1")
                    .one();
            if (task != null) {
                baseMapper.markBlocked(task.getId(), "存量对象缺少平台标识，需人工核对后处理");
            }
        } catch (Exception e) {
            log.error("清理任务登记失败（需人工核对）: key={}, reason={}", url, reason, e);
        }
    }

    @Override
    public List<StorageCleanupTask> listDue(int limit) {
        return baseMapper.selectDue(limit);
    }

    @Override
    public boolean retryOne(Long taskId) {
        StorageCleanupTask task = this.getById(taskId);
        if (task == null || task.getStatus() == null || task.getStatus() != StorageCleanupTask.STATUS_PENDING) {
            return false;
        }
        String blockedReason = blockingReference(task);
        if (blockedReason != null) {
            baseMapper.markBlocked(task.getId(), blockedReason);
            log.warn("清理任务引用复核未通过，暂停重试: id={}, key={}, reason={}",
                    task.getId(), task.getObjectKey(), blockedReason);
            return false;
        }
        boolean removed = pictureStorageService.deleteObject(task.getPlatform(), task.getObjectKey());
        if (!removed) {
            int attempts = task.getAttempts() == null ? 1 : task.getAttempts() + 1;
            baseMapper.markRetry(task.getId(), "对象删除未确认", retryDelay(attempts).toSeconds());
            log.warn("清理任务删除未确认，稍后重试: id={}, key={}, attempts={}",
                    task.getId(), task.getObjectKey(), attempts);
            return false;
        }
        baseMapper.markDone(task.getId());
        return true;
    }

    /**
     * 删除前复核：对象是否仍被有效资源记录引用。
     *
     * @return 阻断原因；可以删除时返回 null
     */
    private String blockingReference(StorageCleanupTask task) {
        if (task.getPlatform() == null || task.getObjectKey() == null) {
            // 存量对象只有地址、没有平台与对象键：不能按地址猜测删除，
            // 转为等待人工处理，避免误删别人的对象
            return "缺少平台标识，需人工核对后处理";
        }
        for (PictureFile file : pictureFileMapper.selectLiveByPlatformAndKey(
                task.getPlatform(), task.getObjectKey())) {
            if (task.getPictureFileId() != null && task.getPictureFileId().equals(file.getId())) {
                // 自己的记录又回到临时/有效状态：资源被重新启用，不能删
                return "资源记录仍占用存储: fileId=" + file.getId();
            }
            return "对象仍被其他资源记录引用: fileId=" + file.getId();
        }
        if (task.getPictureFileId() != null
                && pictureFileMapper.countLivePictureBinding(task.getPictureFileId()) > 0) {
            // 记录被标为已替换/已删除，但图片行又被重新启用并指向它
            return "资源记录仍绑定在未删除的图片上: fileId=" + task.getPictureFileId();
        }
        return null;
    }

    private Duration retryDelay(int attempts) {
        Duration delay = INITIAL_RETRY_DELAY;
        for (int i = 1; i < attempts && delay.compareTo(MAX_RETRY_DELAY) < 0; i++) {
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
    }
}
