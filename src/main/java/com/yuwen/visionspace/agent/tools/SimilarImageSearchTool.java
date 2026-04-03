package com.yuwen.visionspace.agent.tools;

import com.yuwen.visionspace.agent.model.ImageCategoryEnum;
import com.yuwen.visionspace.agent.model.ImageResource;
import com.yuwen.visionspace.api.imagesearch.ImageSearchApiFacade;
import com.yuwen.visionspace.api.imagesearch.model.ImageSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 以图搜图工具 — 封装百度以图搜图 API
 * <p>
 * 调用链路：上传图片URL → 解析搜索页 → 获取相似图片列表
 */
@Slf4j
@Component
public class SimilarImageSearchTool {

    @Tool(name = "searchSimilarImages", description = "以图搜图：根据一张图片的 URL 在百度图库中搜索视觉上相似的图片。" +
            "适用于用户已有一张参考图片、想找到更多类似风格或内容图片的场景。" +
            "如果此工具调用失败，请使用 analyzeImageFeatures 工具分析图片特征后，再用 searchContentImages 关键词搜索作为降级方案。" +
            "返回相似图片的来源页面 URL 和缩略图地址。")
    public List<ImageResource> searchSimilarImages(
            @ToolParam(description = "要搜索的图片 URL，必须是可公开访问的图片地址，例如：'https://example.com/photo.jpg'") String imageUrl) {
        List<ImageResource> imageList = new ArrayList<>();
        try {
            List<ImageSearchResult> resultList = ImageSearchApiFacade.searchImage(imageUrl);
            for (ImageSearchResult result : resultList) {
                imageList.add(ImageResource.builder()
                        .category(ImageCategoryEnum.CONTENT)
                        .description(result.getFromUrl())
                        .url(result.getThumbUrl())
                        .build());
            }
        } catch (Exception e) {
            log.error("以图搜图失败: {}", e.getMessage(), e);
        }
        return imageList;
    }
}
