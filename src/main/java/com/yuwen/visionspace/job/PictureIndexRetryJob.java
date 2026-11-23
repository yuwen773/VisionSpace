package com.yuwen.visionspace.job;

import com.yuwen.visionspace.mapper.PictureIndexTaskMapper;
import com.yuwen.visionspace.model.entity.PictureIndexTask;
import com.yuwen.visionspace.rag.RagVectorStore;
import com.yuwen.visionspace.service.PictureEmbeddingService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 公开图库索引的重试任务。
 *
 * <p>索引写入失败时留下待办，这里按退避时间重做。重做时以数据库的当前状态为准，
 * 因此失败之后发生的编辑、撤审与删除都会被一并带上，不需要为每次改动单独登记。
 *
 * <p>只在开启站内检索时装配：没有向量库的部署没有索引，也就没有待办。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = RagVectorStore.PROPERTY, havingValue = RagVectorStore.CHROMA)
public class PictureIndexRetryJob {

    /** 单轮最多重试的图片数，避免一次拉起过多向量库写入 */
    private static final int BATCH_LIMIT = 50;

    @Resource
    private PictureIndexTaskMapper taskMapper;

    @Resource
    private PictureEmbeddingService pictureEmbeddingService;

    /**
     * 每分钟重试一轮到期失败的索引同步。
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void retryFailed() {
        List<PictureIndexTask> tasks;
        try {
            tasks = taskMapper.selectRetryable(PictureIndexTask.STATUS_FAILED, BATCH_LIMIT);
        } catch (RuntimeException e) {
            log.warn("索引重试任务读取失败: {}", e.getMessage());
            return;
        }
        for (PictureIndexTask task : tasks) {
            try {
                pictureEmbeddingService.syncPictureIndexNow(task.getPictureId());
            } catch (RuntimeException e) {
                // 单个图片失败不影响其余：待办仍在，下一轮继续
                log.warn("索引重试失败, pictureId={}: {}", task.getPictureId(), e.getMessage());
            }
        }
        if (!tasks.isEmpty()) {
            log.info("索引重试一轮完成, 待重试 {} 条", tasks.size());
        }
    }
}
