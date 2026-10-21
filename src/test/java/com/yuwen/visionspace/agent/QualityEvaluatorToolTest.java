package com.yuwen.visionspace.agent;

import com.yuwen.visionspace.agent.model.ActionType;
import com.yuwen.visionspace.agent.model.ImageCategoryEnum;
import com.yuwen.visionspace.agent.model.ImageResource;
import com.yuwen.visionspace.agent.model.MatchVerdict;
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
     * 中文意图 + 英文图库描述是结构性错配：关键词永远对不上，但这不代表图片不对。
     * 必须判为 UNKNOWN 并建议展示，而不是 MISMATCHED 让 Agent 空转到次数耗尽。
     */
    @Test
    void testChineseIntentWithEnglishDescriptions_isUnknownNotMismatched() {
        List<ImageResource> images = contentImages(5, "cyberpunk city neon night");

        QualityResult result = tool.evaluateImageQuality(images, "赛博朋克风格城市");

        assertEquals(MatchVerdict.UNKNOWN, result.getVerdict(),
                "语言不一致导致的关键词零命中不能当作不匹配");
        assertEquals(ActionType.RETURN, result.getAction(),
                "无法判断时应先展示给用户，不应触发重搜");
        assertTrue(result.getReason().contains("描述语言与意图不一致"),
                "应如实说明是语言问题, 实际: " + result.getReason());
    }

    /**
     * 意图与描述同为中文却零命中，说明关键词比较是有意义的，此时才是真的不匹配。
     */
    @Test
    void testSameLanguageZeroOverlap_isMismatched() {
        List<ImageResource> images = contentImages(4, "城市夜景 霓虹灯 街道");

        QualityResult result = tool.evaluateImageQuality(images, "猫咪 宠物");

        assertEquals(MatchVerdict.MISMATCHED, result.getVerdict(),
                "同语言零命中应判为不匹配");
        assertEquals(ActionType.REGENERATE, result.getAction());
    }

    /**
     * 英文意图 + 英文描述零命中同样可比，应判为不匹配。
     */
    @Test
    void testEnglishIntentWithEnglishDescriptions_isMismatched() {
        List<ImageResource> images = contentImages(4, "office desk laptop");

        QualityResult result = tool.evaluateImageQuality(images, "mountain lake sunset");

        assertEquals(MatchVerdict.MISMATCHED, result.getVerdict());
        assertEquals(ActionType.REGENERATE, result.getAction());
    }

    /**
     * 中文没有词边界，整句 split 后只有一个词，整词包含几乎必然失败。
     * 同语言场景必须靠 bigram 重叠识别出命中，否则会被误判为 MISMATCHED。
     */
    @Test
    void testChineseIntentWithChineseDescriptions_matchesByBigram() {
        List<ImageResource> images = contentImages(3, "城市夜景 霓虹灯 街道");

        QualityResult result = tool.evaluateImageQuality(images, "赛博朋克城市夜景");

        assertEquals(MatchVerdict.MATCHED, result.getVerdict(),
                "「城市」「夜景」两个 bigram 都命中，不应判为不匹配");
        assertEquals(ActionType.RETURN, result.getAction());
    }

    /**
     * 空意图无法比较，归为 UNKNOWN。
     */
    @Test
    void testEmptyIntent_isUnknown() {
        List<ImageResource> images = contentImages(5, "cyberpunk city");

        assertEquals(MatchVerdict.UNKNOWN,
                tool.evaluateImageQuality(images, null).getVerdict());
        assertEquals(MatchVerdict.UNKNOWN,
                tool.evaluateImageQuality(images, "").getVerdict());
    }

    /**
     * 分数不得在 UNKNOWN 时跌破中性分：低分会诱导模型绕开 verdict 自行判定不匹配。
     */
    @Test
    void testUnknownVerdict_keepsNeutralScore() {
        List<ImageResource> images = contentImages(12, "english description only");

        QualityResult result = tool.evaluateImageQuality(images, "赛博朋克风格城市");

        assertEquals(MatchVerdict.UNKNOWN, result.getVerdict());
        assertEquals(0.3, result.getMatchScore(), 1e-9,
                "UNKNOWN 的分数应保持中性，不随零命中折算");
    }

    /**
     * 同语言下命中与不命中的分数必须有区分度。
     * （跨语言对照见 {@link #testChineseIntentWithEnglishDescriptions_isUnknownNotMismatched}，
     * 那种情况判 UNKNOWN，两者分数本就应当相同。）
     */
    @Test
    void testDescriptionOverlapRaisesMatchScore() {
        List<ImageResource> images = contentImages(3, "technology office team");
        QualityResult unmatched = tool.evaluateImageQuality(images, "mountain lake");
        QualityResult matched = tool.evaluateImageQuality(images, "technology office banner");

        assertEquals(ActionType.RETURN, matched.getAction());
        assertEquals(MatchVerdict.MATCHED, matched.getVerdict());
        assertEquals(MatchVerdict.MISMATCHED, unmatched.getVerdict());
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
        assertEquals(MatchVerdict.MATCHED, result.getVerdict());
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
