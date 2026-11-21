package com.yuwen.visionspace.service.impl;

import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.service.GalleryIndex;
import com.yuwen.visionspace.service.PictureEmbeddingService;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.VlCaptionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 站内图片向量索引。
 *
 * <p>索引文本由「名称 + 简介 + 标签 + 视觉模型生成的富描述」拼成，向量由
 * {@link VectorStore} 用同一个 EmbeddingModel 生成。查询侧
 * （{@code InternalImageSearchTool}）用同一模型嵌入查询串，因此索引与检索处在
 * 同一向量空间，不需要在业务层手工注入向量。</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.ai.vectorstore.type", havingValue = "chroma")
public class PictureEmbeddingServiceImpl implements PictureEmbeddingService {

    private static final String DOC_ID_PREFIX = "picture_";

    private static final int BATCH_SIZE = 50;

    @Resource
    private VectorStore vectorStore;

    @Resource
    private PictureService pictureService;

    @Resource
    private VlCaptionService vlCaptionService;

    @Override
    @Async("embeddingExecutor")
    public void generateAndStoreEmbedding(Picture picture) {
        try {
            vectorStore.add(List.of(buildDocument(picture)));
            log.info("图片嵌入写入成功, pictureId={}", picture.getId());
        } catch (Exception e) {
            log.error("图片嵌入写入失败, pictureId={}", picture.getId(), e);
        }
    }

    @Override
    @Async("embeddingExecutor")
    public void updateEmbedding(Picture picture) {
        try {
            String docId = DOC_ID_PREFIX + picture.getId();
            try {
                vectorStore.delete(List.of(docId));
            } catch (Exception deleteEx) {
                log.debug("删除旧嵌入失败（可能不存在）, pictureId={}", picture.getId());
            }
            vectorStore.add(List.of(buildDocument(picture)));
            log.info("图片嵌入更新成功, pictureId={}", picture.getId());
        } catch (Exception e) {
            log.error("图片嵌入更新失败, pictureId={}", picture.getId(), e);
        }
    }

    @Override
    @Async("embeddingExecutor")
    public void deleteEmbedding(Long pictureId) {
        try {
            vectorStore.delete(List.of(DOC_ID_PREFIX + pictureId));
            log.info("图片嵌入删除成功, pictureId={}", pictureId);
        } catch (Exception e) {
            log.error("图片嵌入删除失败, pictureId={}", pictureId, e);
        }
    }

    @Override
    public int fullSync() {
        List<Picture> pictures = pictureService.listApprovedPublicPictures();
        if (pictures.isEmpty()) {
            log.info("没有需要索引的公开图片");
            return 0;
        }

        log.info("开始全量同步, 共 {} 张图片", pictures.size());
        int successCount = 0;
        for (int i = 0; i < pictures.size(); i += BATCH_SIZE) {
            List<Picture> batch = pictures.subList(i, Math.min(i + BATCH_SIZE, pictures.size()));
            List<Document> docs = new ArrayList<>();
            for (Picture picture : batch) {
                try {
                    docs.add(buildDocument(picture));
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

        log.info("全量同步完成, 成功 {}/{}", successCount, pictures.size());
        return successCount;
    }

    private Document buildDocument(Picture picture) {
        String richCaption = vlCaptionService.generateRichCaption(picture.getUrl());
        return new Document(DOC_ID_PREFIX + picture.getId(), buildIndexText(picture, richCaption),
                buildMetadata(picture));
    }

    /**
     * 索引文本：结构化字段在前，视觉描述在后。名称/简介/标签缺失时逐项跳过，
     * 避免拼出 "null" 污染向量。
     */
    private String buildIndexText(Picture picture, String richCaption) {
        StringBuilder text = new StringBuilder();
        appendIfPresent(text, picture.getName());
        appendIfPresent(text, picture.getIntroduction());
        appendIfPresent(text, picture.getCategory());
        appendIfPresent(text, picture.getTags());
        appendIfPresent(text, richCaption);
        if (text.isEmpty()) {
            // Chroma 不接受空文本；没有可索引内容时不落库
            throw new IllegalStateException("图片没有可索引的文本内容, pictureId=" + picture.getId());
        }
        return text.toString();
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

    private Map<String, Object> buildMetadata(Picture picture) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("pictureId", picture.getId());
        metadata.put("imageUrl", picture.getUrl() != null ? picture.getUrl() : "");
        metadata.put("thumbnailUrl", picture.getThumbnailUrl() != null ? picture.getThumbnailUrl() : "");
        metadata.put("title", picture.getName() != null ? picture.getName() : "");
        metadata.put("tags", picture.getTags() != null ? picture.getTags() : "[]");
        metadata.put("category", picture.getCategory() != null ? picture.getCategory() : "");
        metadata.put("picColor", picture.getPicColor() != null ? picture.getPicColor() : "");
        metadata.put(GalleryIndex.META_SPACE_ID,
                picture.getSpaceId() != null ? picture.getSpaceId() : GalleryIndex.GALLERY_SPACE_ID);
        metadata.put(GalleryIndex.META_USER_ID,
                picture.getUserId() != null ? picture.getUserId() : 0L);
        metadata.put("indexedAt", System.currentTimeMillis());
        return metadata;
    }
}
