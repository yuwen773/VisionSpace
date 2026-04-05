package com.yuwen.visionspace.service.impl;

import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.service.PictureEmbeddingService;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.VlCaptionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class PictureEmbeddingServiceImpl implements PictureEmbeddingService {

    private static final String DOC_ID_PREFIX = "picture_";

    @Resource
    private VectorStore vectorStore;

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private PictureService pictureService;

    @Resource
    private VlCaptionService vlCaptionService;

    @Override
    @Async("embeddingExecutor")
    public void generateAndStoreEmbedding(Picture picture) {
        try {
            // 1. 生成富描述
            String richCaption = vlCaptionService.generateRichCaption(picture.getImageUrl());

            // 2. 构建多模态输入 (text + image)
            List<Map<String, Object>> contents = new ArrayList<>();
            contents.add(Map.of("text",
                    (picture.getName() != null ? picture.getName() : "") + ". " +
                            (picture.getIntroduction() != null ? picture.getIntroduction() : "")));
            contents.add(Map.of("image", picture.getImageUrl()));

            // 3. 调用 embedding 模型生成向量（使用 YAML 配置的 qwen3-vl-embedding + enable_fusion）
            EmbeddingResponse embeddingResponse = embeddingModel.embed(
                    new EmbeddingRequest(contents, null));

            float[] vector = embeddingResponse.getResults().get(0).getOutput();

            // 4. 构建 Document（content = richCaption 用于 Chroma 存储可读文本）
            Map<String, Object> metadata = buildMetadata(picture);
            Document doc = new Document(DOC_ID_PREFIX + picture.getId(), richCaption, metadata);

            // 5. 注入向量到 Document（跳过 VectorStore 重新 embedding）
            doc.setEmbedding(vector);

            // 6. 存入 Chroma
            vectorStore.add(List.of(doc));

            log.info("图片嵌入生成成功(多模态融合), pictureId={}, 向量维度={}",
                    picture.getId(), vector.length);
        } catch (Exception e) {
            log.error("图片嵌入生成失败, pictureId={}", picture.getId(), e);
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

            // 1. 生成富描述
            String richCaption = vlCaptionService.generateRichCaption(picture.getImageUrl());

            // 2. 构建多模态输入 (text + image)
            List<Map<String, Object>> contents = new ArrayList<>();
            contents.add(Map.of("text",
                    (picture.getName() != null ? picture.getName() : "") + ". " +
                            (picture.getIntroduction() != null ? picture.getIntroduction() : "")));
            contents.add(Map.of("image", picture.getImageUrl()));

            // 3. 调用 embedding 模型生成向量
            EmbeddingResponse embeddingResponse = embeddingModel.embed(
                    new EmbeddingRequest(contents, null));

            float[] vector = embeddingResponse.getResults().get(0).getOutput();

            // 4. 构建 Document（content = richCaption 用于 Chroma 存储可读文本）
            Map<String, Object> metadata = buildMetadata(picture);
            Document doc = new Document(DOC_ID_PREFIX + picture.getId(), richCaption, metadata);

            // 5. 注入向量到 Document
            doc.setEmbedding(vector);

            // 6. 存入 Chroma
            vectorStore.add(List.of(doc));

            log.info("图片嵌入更新成功(多模态融合), pictureId={}, 向量维度={}",
                    picture.getId(), vector.length);
        } catch (Exception e) {
            log.error("图片嵌入更新失败, pictureId={}", picture.getId(), e);
        }
    }

    @Override
    @Async("embeddingExecutor")
    public void deleteEmbedding(Long pictureId) {
        try {
            String docId = DOC_ID_PREFIX + pictureId;
            vectorStore.delete(List.of(docId));
            log.info("图片嵌入删除成功, pictureId={}", pictureId);
        } catch (Exception e) {
            log.error("图片嵌入删除失败, pictureId={}", pictureId, e);
        }
    }

    @Override
    public int fullSync() {
        // Use query method to find all approved public pictures
        List<Picture> pictures = pictureService.listApprovedPublicPictures();

        if (pictures.isEmpty()) {
            log.info("没有需要索引的公开图片");
            return 0;
        }

        log.info("开始全量同步, 共 {} 张图片", pictures.size());
        int successCount = 0;
        int batchSize = 50;

        for (int i = 0; i < pictures.size(); i += batchSize) {
            List<Picture> batch = pictures.subList(i, Math.min(i + batchSize, pictures.size()));
            List<Document> docs = new ArrayList<>();
            for (Picture picture : batch) {
                try {
                    Document doc = buildDocumentWithMultimodalEmbedding(picture);
                    if (doc != null) {
                        docs.add(doc);
                    }
                } catch (Exception e) {
                    log.error("构建文档嵌入失败, pictureId={}", picture.getId(), e);
                }
            }
            if (!docs.isEmpty()) {
                try {
                    vectorStore.add(docs);
                    successCount += docs.size();
                    log.info("全量同步进度: {}/{}", Math.min(i + batchSize, pictures.size()), pictures.size());
                } catch (Exception e) {
                    log.error("批量嵌入失败, offset={}", i, e);
                }
            }
        }

        log.info("全量同步完成, 成功 {}/{}", successCount, pictures.size());
        return successCount;
    }

    /**
     * 构建带有多模态融合向量的 Document
     */
    private Document buildDocumentWithMultimodalEmbedding(Picture picture) {
        // 1. 生成富描述
        String richCaption = vlCaptionService.generateRichCaption(picture.getImageUrl());

        // 2. 构建多模态输入 (text + image)
        List<Map<String, Object>> contents = new ArrayList<>();
        contents.add(Map.of("text",
                (picture.getName() != null ? picture.getName() : "") + ". " +
                        (picture.getIntroduction() != null ? picture.getIntroduction() : "")));
        contents.add(Map.of("image", picture.getImageUrl()));

        // 3. 调用 embedding 模型生成向量
        EmbeddingResponse embeddingResponse = embeddingModel.embed(
                new EmbeddingRequest(contents, null));

        float[] vector = embeddingResponse.getResults().get(0).getOutput();

        // 4. 构建 metadata
        Map<String, Object> metadata = buildMetadata(picture);

        // 5. 构建 Document 并注入向量
        Document doc = new Document(DOC_ID_PREFIX + picture.getId(), richCaption, metadata);
        doc.setEmbedding(vector);

        return doc;
    }

    /**
     * 构建 metadata
     */
    private Map<String, Object> buildMetadata(Picture picture) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("pictureId", picture.getId());
        metadata.put("imageUrl", picture.getUrl() != null ? picture.getUrl() : "");
        metadata.put("thumbnailUrl", picture.getThumbnailUrl() != null ? picture.getThumbnailUrl() : "");
        metadata.put("title", picture.getName() != null ? picture.getName() : "");
        metadata.put("tags", picture.getTags() != null ? picture.getTags() : "[]");
        metadata.put("category", picture.getCategory() != null ? picture.getCategory() : "");
        metadata.put("picColor", picture.getPicColor() != null ? picture.getPicColor() : "");
        metadata.put("spaceId", picture.getSpaceId() != null ? picture.getSpaceId() : 0L);
        metadata.put("userId", picture.getUserId() != null ? picture.getUserId() : 0L);
        metadata.put("visibility", picture.getVisibility() != null ? picture.getVisibility() : "public");
        metadata.put("indexedAt", System.currentTimeMillis());
        return metadata;
    }
}
