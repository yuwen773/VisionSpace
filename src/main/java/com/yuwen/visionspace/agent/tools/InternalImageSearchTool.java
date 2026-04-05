package com.yuwen.visionspace.agent.tools;

import com.yuwen.visionspace.agent.model.ImageCategoryEnum;
import com.yuwen.visionspace.agent.model.ImageResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 站内图片向量搜索工具
 */
@Slf4j
@Component
public class InternalImageSearchTool {

    @Resource
    private VectorStore vectorStore;

    @Tool(name = "searchInternalImages", description = "从站内图片库中通过语义搜索查找图片。适用于用户需要特定风格、主题、颜色或类型的站内已有图片。优先使用此工具搜索站内资源，结果不足时再使用外部搜索。")
    public List<ImageResource> searchInternalImages(
            @ToolParam(description = "搜索描述，使用自然语言描述想要的图片特征，例如：'科技感蓝色背景图'、'温暖的自然风光'、'简洁的商务插图'") String query,
            @ToolParam(description = "当前空间 ID", required = false) Long currentSpaceId,
            @ToolParam(description = "当前用户 ID", required = false) Long currentUserId,
            @ToolParam(description = "是否为管理员", required = false) Boolean isAdmin,
            @ToolParam(description = "返回结果数量，默认 5，最大 10", required = false) Integer topK
    ) {
        int k = (topK != null && topK > 0 && topK <= 10) ? topK : 8;
        List<ImageResource> results = new ArrayList<>();

        try {
            SearchRequest.SearchRequestBuilder builder = SearchRequest.builder()
                    .query(query)
                    .topK(k)
                    .similarityThreshold(0.0);

            // 非管理员用户需要权限过滤
            if (currentSpaceId != null && (isAdmin == null || !isAdmin)) {
                Filter.Expression filter;
                if (currentUserId != null) {
                    filter = Filter.and(
                            Filter.eq("spaceId", currentSpaceId),
                            Filter.or(
                                    Filter.eq("visibility", "public"),
                                    Filter.eq("userId", currentUserId)
                            )
                    );
                } else {
                    filter = Filter.and(
                            Filter.eq("spaceId", currentSpaceId),
                            Filter.eq("visibility", "public")
                    );
                }
                builder.filter(filter);
            }

            List<Document> docs = vectorStore.similaritySearch(builder.build());

            for (Document doc : docs) {
                Map<String, Object> meta = doc.getMetadata();
                String imageUrl = (String) meta.getOrDefault("imageUrl", "");
                String description = doc.getText();

                if (!imageUrl.isEmpty()) {
                    results.add(ImageResource.builder()
                            .category(ImageCategoryEnum.CONTENT)
                            .url(imageUrl)
                            .description(description)
                            .source("internal")
                            .build());
                }
            }

            log.info("站内图片搜索完成, query='{}', 结果数={}", query, results.size());
        } catch (Exception e) {
            log.error("站内图片搜索失败: {}", e.getMessage(), e);
        }

        return results;
    }
}
