package com.yuwen.visionspace.job;

import com.yuwen.visionspace.agent.service.AgentAttachmentService;
import com.yuwen.visionspace.model.entity.PictureFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * Agent 附件回收与删除重试任务。
 *
 * <p>两件事：
 * <ul>
 *   <li>回收未绑定的临时图片：创建超过保留期（二十四小时）且没有有效绑定的资源
 *       被清理。绑定与回收并发时以资源行锁为准，不会误删正在绑定的图片。</li>
 *   <li>重试未确认的删除：对象存储删除失败时保留的可重试状态在这里按退避时间
 *       重试，删除未确认不会被静默丢弃。</li>
 * </ul>
 */
@Slf4j
@Component
public class AgentAttachmentCleanupJob {

    @Resource
    private AgentAttachmentService agentAttachmentService;

    /**
     * 每小时回收一次未绑定的临时图片，并重试未确认的删除。
     */
    @Scheduled(fixedRate = 3600000, initialDelay = 600000)
    public void cleanup() {
        reclaimUnboundTempImages();
        retryPendingDeletes();
    }

    private void reclaimUnboundTempImages() {
        List<PictureFile> candidates =
                agentAttachmentService.listReclaimCandidates(AgentAttachmentService.DEFAULT_BATCH_SIZE);
        if (candidates.isEmpty()) {
            return;
        }
        int reclaimed = 0;
        for (PictureFile candidate : candidates) {
            try {
                if (agentAttachmentService.reclaimOne(candidate.getId())) {
                    reclaimed++;
                }
            } catch (Exception e) {
                // 单条失败不影响其余候选：状态保留在库里，下一轮继续
                log.warn("回收未绑定临时图片失败: id={}", candidate.getId(), e);
            }
        }
        log.info("回收未绑定临时图片: 候选={}, 已回收={}", candidates.size(), reclaimed);
    }

    private void retryPendingDeletes() {
        List<PictureFile> pending =
                agentAttachmentService.listPendingDeletes(AgentAttachmentService.DEFAULT_BATCH_SIZE);
        if (pending.isEmpty()) {
            return;
        }
        int confirmed = 0;
        for (PictureFile file : pending) {
            try {
                if (agentAttachmentService.retryDeleteOne(file.getId())) {
                    confirmed++;
                }
            } catch (Exception e) {
                log.warn("重试删除附件对象失败: id={}", file.getId(), e);
            }
        }
        log.info("重试未确认的删除: 待处理={}, 已确认={}", pending.size(), confirmed);
    }
}
