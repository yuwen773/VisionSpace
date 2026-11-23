package com.yuwen.visionspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuwen.visionspace.mapper.PictureIndexTaskMapper;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.PictureIndexTask;
import com.yuwen.visionspace.model.enums.PictureReviewStatusEnum;
import com.yuwen.visionspace.model.enums.ResourceIsolationStatusEnum;
import com.yuwen.visionspace.model.enums.ResourceTypeEnum;
import com.yuwen.visionspace.rag.PictureIndex;
import com.yuwen.visionspace.rag.PictureIndexState;
import com.yuwen.visionspace.rag.RagVectorStore;
import com.yuwen.visionspace.service.PictureEmbeddingService;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.ResourceIsolationService;
import com.yuwen.visionspace.service.VlCaptionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 站内图片向量索引。
 *
 * <p>索引文本由「名称 + 简介 + 标签 + 视觉模型生成的富描述」拼成，向量由
 * {@link VectorStore} 用同一个 EmbeddingModel 生成。查询侧
 * （{@code InternalImageSearchTool}）用同一模型嵌入查询串，因此索引与检索处在
 * 同一向量空间，不需要在业务层手工注入向量。</p>
 *
 * <p>索引内容由图片当前在数据库里的状态决定（见 {@link PictureIndexState}）：
 * 调用方只给图片 id，这里重新读一次当前状态再决定覆盖写入还是移出索引。
 * 用改动前的对象快照重建索引，会让已转入待审核的公开图片带着旧内容留在索引里。</p>
 *
 * <p>写入走覆盖而不是先删后写：向量库的写入按文档标识覆盖，先删后写在写失败时
 * 会留下空洞——旧内容已经没了，新内容也没进去，这条图片就从检索里彻底消失。
 * 写失败时留下可重试的记录，重试时同样以数据库当前状态为准。</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = RagVectorStore.PROPERTY, havingValue = RagVectorStore.CHROMA)
public class PictureEmbeddingServiceImpl implements PictureEmbeddingService {

    private static final int BATCH_SIZE = 50;

    /** 失败原因入库的最大长度 */
    private static final int MAX_ERROR_LENGTH = 400;

    /** 重试退避的上限（分钟） */
    private static final long MAX_BACKOFF_MINUTES = 60;

    @Resource
    private VectorStore vectorStore;

    @Resource
    private PictureService pictureService;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private VlCaptionService vlCaptionService;

    @Resource
    private ResourceIsolationService resourceIsolationService;

    @Resource
    private PictureIndexTaskMapper taskMapper;

    @Override
    @Async("embeddingExecutor")
    public void syncPictureIndex(Long pictureId) {
        syncPictureIndexNow(pictureId);
    }

    @Override
    public void syncPictureIndexNow(Long pictureId) {
        if (pictureId == null || pictureId <= 0) {
            return;
        }
        // 以数据库当前状态为准：调用方给的是图片 id，不是某一次改动的快照
        Picture picture = pictureService.getById(pictureId);
        boolean quarantined = picture != null
                && resourceIsolationService.isQuarantined(ResourceTypeEnum.PICTURE.getValue(), pictureId);
        PictureIndexState.Operation operation = PictureIndexState.decide(picture, quarantined);
        try {
            if (operation == PictureIndexState.Operation.UPSERT) {
                Document document = buildDocument(picture);
                if (document == null) {
                    // 没有可索引的内容：把可能残留的旧文档移出索引，
                    // 否则检索会召回一个内容已经对不上的旧描述
                    vectorStore.delete(List.of(PictureIndex.docId(pictureId)));
                } else {
                    // 覆盖写：同一文档标识直接覆盖，不先删后写
                    vectorStore.add(List.of(document));
                }
            } else {
                vectorStore.delete(List.of(PictureIndex.docId(pictureId)));
            }
            clearTask(pictureId);
            log.info("图片索引同步完成, pictureId={}, operation={}", pictureId, operation);
        } catch (Exception e) {
            recordFailure(pictureId, operation, e);
            log.error("图片索引同步失败, pictureId={}, operation={}", pictureId, operation, e);
        }
    }

    @Override
    public int fullSync() {
        // 先把不该被检索的公开图片移出索引，再写入应当被检索的：
        // 只写不移会让撤审与隔离过的图片永远留在索引里
        int removed = removeStaleDocuments();
        List<Picture> pictures = pictureService.listApprovedPublicPictures();
        if (pictures.isEmpty()) {
            log.info("全量同步完成：没有需要索引的公开图片，已移出索引 {}", removed);
            return 0;
        }

        log.info("开始全量同步, 共 {} 张图片", pictures.size());
        int successCount = 0;
        for (int i = 0; i < pictures.size(); i += BATCH_SIZE) {
            List<Picture> batch = pictures.subList(i, Math.min(i + BATCH_SIZE, pictures.size()));
            List<Document> docs = new ArrayList<>();
            for (Picture picture : batch) {
                try {
                    Document document = buildDocument(picture);
                    if (document != null) {
                        docs.add(document);
                    }
                } catch (Exception e) {
                    log.error("构建文档失败, pictureId={}", picture.getId(), e);
                }
            }
            if (docs.isEmpty()) {
                continue;
            }
            try {
                vectorStore.add(docs);
                successCount += docs.size();
                log.info("全量同步进度: {}/{}", Math.min(i + BATCH_SIZE, pictures.size()), pictures.size());
            } catch (Exception e) {
                log.error("批量嵌入失败, offset={}", i, e);
            }
        }

        log.info("全量同步完成, 成功 {}/{}, 已移出索引 {}", successCount, pictures.size(), removed);
        return successCount;
    }

    /**
     * 把当前不该被检索的公开图片移出索引：审核未通过的，以及隔离中的。
     */
    private int removeStaleDocuments() {
        int pass = PictureReviewStatusEnum.PASS.getValue();
        List<Long> stale = new ArrayList<>(pictureMapper.selectNonApprovedPublicPictureIds(pass));
        stale.addAll(pictureMapper.selectQuarantinedApprovedPublicPictureIds(pass,
                ResourceTypeEnum.PICTURE.getValue(), ResourceIsolationStatusEnum.QUARANTINED.getValue()));
        int removed = 0;
        for (int i = 0; i < stale.size(); i += BATCH_SIZE) {
            List<String> ids = stale.subList(i, Math.min(i + BATCH_SIZE, stale.size())).stream()
                    .map(PictureIndex::docId)
                    .toList();
            try {
                vectorStore.delete(ids);
                removed += ids.size();
            } catch (Exception e) {
                log.error("移出过期索引失败, offset={}", i, e);
            }
        }
        return removed;
    }

    /**
     * 构造索引文档。没有可索引文本时返回 null，由调用方按「不可检索」处理。
     */
    private Document buildDocument(Picture picture) {
        String richCaption = vlCaptionService.generateRichCaption(picture.getUrl());
        String indexText = buildIndexText(picture, richCaption);
        if (indexText == null) {
            return null;
        }
        return PictureIndex.document(picture, indexText);
    }

    /**
     * 索引文本：结构化字段在前，视觉描述在后。名称/简介/标签缺失时逐项跳过，
     * 避免拼出 "null" 污染向量。没有任何可索引内容时返回 null。
     */
    private String buildIndexText(Picture picture, String richCaption) {
        StringBuilder text = new StringBuilder();
        appendIfPresent(text, picture.getName());
        appendIfPresent(text, picture.getIntroduction());
        appendIfPresent(text, picture.getCategory());
        appendIfPresent(text, picture.getTags());
        appendIfPresent(text, richCaption);
        // Chroma 不接受空文本；没有可索引内容时不落库
        return text.isEmpty() ? null : text.toString();
    }

    private void appendIfPresent(StringBuilder text, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!text.isEmpty()) {
            text.append('。');
        }
        text.append(value);
    }

    /**
     * 同步成功后清掉待办：这条图片当前没有未完成的索引动作。
     */
    private void clearTask(Long pictureId) {
        try {
            taskMapper.deleteByPictureId(pictureId);
        } catch (Exception e) {
            log.warn("索引待办清理失败, pictureId={}", pictureId, e);
        }
    }

    /**
     * 记录失败，留下可重试的待办。只写一行日志不算收敛：那样索引与数据库的分歧
     * 会一直留着，直到有人手工处理。
     */
    private void recordFailure(Long pictureId, PictureIndexState.Operation operation, Exception failure) {
        try {
            int attempts = currentAttempts(pictureId) + 1;
            Date nextRetry = new Date(System.currentTimeMillis() + backoffMinutes(attempts) * 60_000L);
            taskMapper.recordFailure(pictureId, operation.name(), PictureIndexTask.STATUS_FAILED,
                    attempts, summarize(failure), nextRetry);
        } catch (Exception e) {
            log.error("索引重试待办登记失败, pictureId={}", pictureId, e);
        }
    }

    private int currentAttempts(Long pictureId) {
        PictureIndexTask task = taskMapper.selectOne(
                new LambdaQueryWrapper<PictureIndexTask>().eq(PictureIndexTask::getPictureId, pictureId));
        return task == null || task.getAttempts() == null ? 0 : task.getAttempts();
    }

    /**
     * 退避时间：1、2、4、8……分钟，上限一小时。避免向量库长时间不可用时反复撞它。
     */
    private long backoffMinutes(int attempts) {
        int steps = Math.min(Math.max(attempts - 1, 0), 6);
        return Math.min(1L << steps, MAX_BACKOFF_MINUTES);
    }

    /**
     * 失败原因入库前的脱敏：只保留异常类型与截断后的说明，地址一律替换掉。
     */
    private String summarize(Exception failure) {
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        message = message.replaceAll("https?://\\S+", "<url>");
        if (message.length() > MAX_ERROR_LENGTH) {
            message = message.substring(0, MAX_ERROR_LENGTH);
        }
        return failure.getClass().getSimpleName() + (message.isBlank() ? "" : ": " + message);
    }
}
