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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 站内图片向量搜索工具
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "vision-space.rag", name = "enabled", havingValue = "true")
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
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(query)
                    .topK(k)
                    .similarityThreshold(0.0);

            Filter.Expression filter = visibilityFilter(currentSpaceId, currentUserId, isAdmin);
            if (filter != null) {
                builder.filterExpression(filter);
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

    /**
     * 可见性过滤。索引里只有「公开图库」（spaceId=0）与被判定为 public 的图片，
     * 所以默认只返回公开图库；用户在自己的空间里可以额外看到自己的图片。
     * 管理员不加限制。
     */
    private Filter.Expression visibilityFilter(Long currentSpaceId, Long currentUserId, Boolean isAdmin) {
        if (isAdmin != null && isAdmin) {
            return null;
        }
        Filter.Expression gallery = and(
                eq("spaceId", 0L),
                eq("visibility", "public"));
        if (currentSpaceId != null && currentUserId != null) {
            return new Filter.Expression(Filter.ExpressionType.OR, gallery,
                    and(eq("spaceId", currentSpaceId), eq("userId", currentUserId)));
        }
        return gallery;
    }

    private static Filter.Expression eq(String key, Object value) {
        return new Filter.Expression(Filter.ExpressionType.EQ,
                new Filter.Key(key), new Filter.Value(value));
    }

    private static Filter.Expression and(Filter.Expression left, Filter.Expression right) {
        return new Filter.Expression(Filter.ExpressionType.AND, left, right);
    }
}
