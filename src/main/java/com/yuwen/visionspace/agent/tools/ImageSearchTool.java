package com.yuwen.visionspace.agent.tools;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yuwen.visionspace.agent.model.ImageCategoryEnum;
import com.yuwen.visionspace.agent.model.ImageResource;
import lombok.extern.slf4j.Slf4j;
import com.yuwen.visionspace.api.imagesearch.ImageSearchApiFacade;
import com.yuwen.visionspace.api.imagesearch.model.ImageSearchResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 图片搜索工具
 */
@Slf4j
@Component
public class ImageSearchTool {

    private static final String PEXELS_API_URL = "https://api.pexels.com/v1/search";

    @Value("${vision-space.pexels.api-key}")
    private String pexelsApiKey;

    @Tool(description = "搜索内容相关的图片，用于网站内容展示")
    public List<ImageResource> searchContentImages(@ToolParam(description = "搜索关键词") String query) {
        List<ImageResource> imageList = new ArrayList<>();
        int searchCount = 12;
        // 调用 API，注意释放资源
        try (HttpResponse response = HttpRequest.get(PEXELS_API_URL)
                .header("Authorization", pexelsApiKey)
                .form("query", query)
                .form("per_page", searchCount)
                .form("page", 1)
                .execute()) {
            if (response.isOk()) {
                JSONObject result = JSONUtil.parseObj(response.body());
                JSONArray photos = result.getJSONArray("photos");
                for (int i = 0; i < photos.size(); i++) {
                    JSONObject photo = photos.getJSONObject(i);
                    JSONObject src = photo.getJSONObject("src");
                    imageList.add(ImageResource.builder()
                            .category(ImageCategoryEnum.CONTENT)
                            .description(photo.getStr("alt", query))
                            .url(src.getStr("medium"))
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("Pexels API 调用失败: {}", e.getMessage(), e);
        }
        return imageList;
    }

    /**
     * 以图搜图 - 根据图片URL搜索相似图片
     *
     * @param imageUrl 图片URL
     * @return 相似图片列表
     */
    @Tool(description = "以图搜图，根据图片URL搜索相似图片，返回图片来源和缩略图地址")
    public List<ImageResource> searchSimilarImages(@ToolParam(description = "要搜索的图片URL") String imageUrl) {
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
