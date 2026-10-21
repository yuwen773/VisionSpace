package com.yuwen.visionspace.agent;

import com.yuwen.visionspace.agent.model.ActionType;
import com.yuwen.visionspace.agent.model.ImageCategoryEnum;
import com.yuwen.visionspace.agent.model.ImageResource;
import com.yuwen.visionspace.agent.model.QualityResult;
import com.yuwen.visionspace.agent.tools.QualityEvaluatorTool;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QualityEvaluatorTool 单元测试
 */
class QualityEvaluatorToolTest {

    private final QualityEvaluatorTool tool = new QualityEvaluatorTool();

    @Test
    void testEvaluateEmptyImages() {
        QualityResult result = tool.evaluateImageQuality(new ArrayList<>(), "赛博朋克风格城市");
        assertEquals(0.0, result.getMatchScore());
        assertEquals(ActionType.REGENERATE, result.getAction());
    }

    @Test
    void testEvaluateNullImages() {
        QualityResult result = tool.evaluateImageQuality(null, "赛博朋克风格城市");
        assertEquals(0.0, result.getMatchScore());
        assertEquals(ActionType.REGENERATE, result.getAction());
    }

    @Test
    void testEvaluateFewImages() {
        List<ImageResource> images = new ArrayList<>();
        images.add(ImageResource.builder().url("http://test.jpg").build());

        QualityResult result = tool.evaluateImageQuality(images, "赛博朋克风格城市");
        assertEquals(0.3, result.getMatchScore());
        assertEquals(ActionType.REGENERATE, result.getAction());
    }

    /**
     * 搜索结果（如 Pexels）的 category 恒为 CONTENT、描述多为英文，
     * 无法据此判断中文意图。数量充足时必须仍然建议展示，而不是误判为不匹配。
     */
    @Test
    void testSearchResultsWithoutIntentSignalAreStillReturned() {
        List<ImageResource> images = contentImages(5, "cyberpunk city neon");

        QualityResult result = tool.evaluateImageQuality(images, "赛博朋克风格城市");

        assertEquals(ActionType.RETURN, result.getAction());
        assertTrue(result.getMatchScore() >= 0.3 && result.getMatchScore() <= 1.0,
                "matchScore 应在合理区间, 实际: " + result.getMatchScore());
        assertTrue(result.getReason().contains("未能从描述中确认"),
                "无意图信号时应如实说明, 实际: " + result.getReason());
    }

    @Test
    void testDescriptionOverlapRaisesMatchScore() {
        List<ImageResource> images = contentImages(3, "technology office team");
        QualityResult unmatched = tool.evaluateImageQuality(images, "猫咪图片");
        QualityResult matched = tool.evaluateImageQuality(images, "technology office banner");

        assertEquals(ActionType.RETURN, matched.getAction());
        assertTrue(matched.getMatchScore() > unmatched.getMatchScore(),
                "描述命中意图时分数应更高: " + matched.getMatchScore() + " vs " + unmatched.getMatchScore());
        assertTrue(matched.getReason().contains("相符"));
    }

    @Test
    void testCategoryMatchCountsAsIntentSignal() {
        List<ImageResource> images = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            images.add(ImageResource.builder()
                    .category(ImageCategoryEnum.LOGO)
                    .description("brand mark")
                    .url("http://test" + i + ".jpg")
                    .build());
        }

        QualityResult result = tool.evaluateImageQuality(images, "需要一个公司 logo");

        assertEquals(ActionType.RETURN, result.getAction());
        assertTrue(result.getMatchScore() > 0.3, "类别命中应视为意图信号, 实际: " + result.getMatchScore());
    }

    private List<ImageResource> contentImages(int count, String description) {
        List<ImageResource> images = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            images.add(ImageResource.builder()
                    .category(ImageCategoryEnum.CONTENT)
                    .description(description)
                    .url("http://test" + i + ".jpg")
                    .build());
        }
        return images;
    }
}
