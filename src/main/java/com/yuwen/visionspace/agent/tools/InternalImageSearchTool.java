package com.yuwen.visionspace.agent.tools;

import com.yuwen.visionspace.agent.model.ImageResource;
import com.yuwen.visionspace.rag.PictureIndex;
import com.yuwen.visionspace.rag.PublicGalleryIndexReader;
import com.yuwen.visionspace.rag.RagVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 站内图片向量搜索工具。
 *
 * <p>文档标识、元数据键与可见范围都来自 {@link PictureIndex}：写入方
 * （{@code PictureEmbeddingServiceImpl}）与这里共用同一份契约。
 *
 * <p>向量库只提供候选。命中之后由 {@link PublicGalleryIndexReader} 回到数据库复核
 * 图片的当前状态：审核被撤销、已删除或已隔离的图片不会因为索引里还留着旧向量
 * 就被交出去。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = RagVectorStore.PROPERTY, havingValue = RagVectorStore.CHROMA)
public class InternalImageSearchTool {

    @Resource
    private VectorStore vectorStore;

    @Resource
    private PublicGalleryIndexReader reader;

    @Tool(name = "searchInternalImages", description = "从站内图片库中通过语义搜索查找图片。适用于用户需要特定风格、主题、颜色或类型的站内已有图片。优先使用此工具搜索站内资源，结果不足时再使用外部搜索。")
    public List<ImageResource> searchInternalImages(
            @ToolParam(description = "搜索描述，使用自然语言描述想要的图片特征，例如：'科技感蓝色背景图'、'温暖的自然风光'、'简洁的商务插图'") String query,
            @ToolParam(description = "返回结果数量，默认 5，最大 10", required = false) Integer topK
    ) {
        int k = (topK != null && topK > 0 && topK <= 10) ? topK : 8;

        try {
            // 可见范围由索引契约决定，不来自模型参数：模型没有「管理员」这个身份，
            // 也不该通过工具参数扩大检索范围
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(k)
                    .similarityThreshold(0.0)
                    .filterExpression(PictureIndex.publicGalleryFilter())
                    .build();

            List<Document> docs = vectorStore.similaritySearch(searchRequest);

            // 向量库只给候选，图片当前能不能对外展示由数据库说了算：
            // 审核被撤销、已删除或已隔离的旧命中在这里被丢弃
            List<ImageResource> results = reader.present(docs);

            log.info("站内图片搜索完成, query='{}', 命中={}, 复核后={}", query, docs.size(), results.size());
            return results;
        } catch (Exception e) {
            log.error("站内图片搜索失败: {}", e.getMessage(), e);
        }

        return List.of();
    }
}
