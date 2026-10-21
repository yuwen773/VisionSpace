package com.yuwen.visionspace.agent.tools;

import com.yuwen.visionspace.agent.model.ActionType;
import com.yuwen.visionspace.agent.model.ImageCategoryEnum;
import com.yuwen.visionspace.agent.model.ImageResource;
import com.yuwen.visionspace.agent.model.MatchVerdict;
import com.yuwen.visionspace.agent.model.QualityResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 图片质量评估工具
 * 评估搜索结果与用户意图的匹配度
 */
@Slf4j
@Component
public class QualityEvaluatorTool {

    /**
     * 数量充足、但命中意图的图片不足半数时的中性分数。
     */
    private static final double NEUTRAL_SCORE = 0.3;

    /**
     * 判定为「无法判断」时给出的分数。
     * <p>
     * 与 {@link #NEUTRAL_SCORE} 刻意取同一个值：分数本身表达的是「没有正面证据」，
     * 到底是搜错了还是描述不可读，由 {@link MatchVerdict} 承载。调用方不应再靠
     * 分数阈值去区分这两种情况。
     */
    private static final double UNKNOWN_SCORE = NEUTRAL_SCORE;

    /**
     * 评估图片列表与用户意图的匹配度
     *
     * @param images 图片列表
     * @param userIntent 用户原始意图描述
     * @return 评估结果
     */
    @Tool(name = "evaluateImageQuality", description = "评估一组图片与用户原始意图的匹配程度。在图片搜索完成后调用，用于判断搜索结果是否满足需求、是否需要重新搜索或改用 AIGC 生成。返回匹配度分数（0-1）、原因分析和下一步建议。")
    public QualityResult evaluateImageQuality(
            @ToolParam(description = "待评估的图片资源列表，来自搜索或生成的结果") List<ImageResource> images,
            @ToolParam(description = "用户的原始需求描述，例如：'需要一个科技感的网站首页 banner'、'为公司 logo 找参考'") String userIntent) {

        // 数量检查
        if (images == null || images.isEmpty()) {
            return QualityResult.builder()
                    .matchScore(0.0)
                    .reason("没有搜索结果")
                    .suggestions(List.of("建议使用 AIGC 生成图片"))
                    .action(ActionType.REGENERATE)
                    .verdict(MatchVerdict.MISMATCHED)
                    .build();
        }

        int count = images.size();

        // 简单规则判断：数量 < 3 时建议生成
        if (count < 3) {
            return QualityResult.builder()
                    .matchScore(NEUTRAL_SCORE)
                    .reason("搜索结果数量较少")
                    .suggestions(List.of("搜索结果不足，建议使用 AIGC 补充生成"))
                    .action(ActionType.REGENERATE)
                    .verdict(MatchVerdict.MISMATCHED)
                    .build();
        }

        // 数量 >= 3 时，数量本身已足以展示；语义匹配只作为信号，不作为否决条件。
        // 但「搜错了」和「判断不了」必须分开：搜索结果（Pexels/unDraw/以图搜图）的
        // category 固定为 CONTENT，描述多为英文，而用户意图常是中文——这种情况下
        // 关键词对不上是结构性的，不能据此认定图片不匹配。
        String normalizedIntent = userIntent == null ? "" : userIntent.toLowerCase(Locale.ROOT);
        String[] intentWords = normalizedIntent.split("[\\s,，.。;；]+");
        int matchCount = (int) images.stream()
                .filter(image -> isIntentMatchImage(normalizedIntent, intentWords, image))
                .count();

        MatchVerdict verdict = judge(normalizedIntent, matchCount, images);

        return QualityResult.builder()
                .matchScore(scoreFromMatchRatio(matchCount, images.size(), verdict))
                .verdict(verdict)
                .reason(reasonFor(verdict))
                .suggestions(suggestionsFor(verdict))
                // 只有确证不匹配时才值得再花一次迭代；判断不了就先给用户看。
                .action(verdict == MatchVerdict.MISMATCHED
                        ? ActionType.REGENERATE
                        : ActionType.RETURN)
                .build();
    }

    /**
     * 判定图片与意图的匹配情况。
     * <p>
     * 关键在区分 {@link MatchVerdict#MISMATCHED} 与 {@link MatchVerdict#UNKNOWN}：
     * 零命中既可能是搜错了，也可能只是描述用了另一种语言、关键词天然对不上。
     * 后者若按「不匹配」处理，会让 Agent 在探索阶段空转到次数耗尽。
     */
    private MatchVerdict judge(String normalizedIntent, int matchCount, List<ImageResource> images) {
        if (matchCount > 0) {
            return MatchVerdict.MATCHED;
        }
        // 零命中时，只有「意图与描述使用同一种书写体系」才说明关键词比较是有意义的。
        // 否则无法排除语言差异导致的假阴性。
        return hasComparableText(normalizedIntent, images)
                ? MatchVerdict.MISMATCHED
                : MatchVerdict.UNKNOWN;
    }

    /**
     * 判断意图与图片描述是否处于同一书写体系，从而关键词比较才有意义。
     * <p>
     * 任一张图的描述与意图同体系即认为可比：只要存在可比样本，零命中就不再是
     * 语言差异造成的，而是真的对不上。
     */
    private boolean hasComparableText(String normalizedIntent, List<ImageResource> images) {
        if (normalizedIntent.isBlank()) {
            return false;
        }
        boolean intentHasCjk = containsCjk(normalizedIntent);
        for (ImageResource image : images) {
            String description = image.getDescription();
            if (description == null || description.isBlank()) {
                continue;
            }
            if (containsCjk(description.toLowerCase(Locale.ROOT)) == intentHasCjk) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否包含 CJK 字符。用于粗略判断文本属于哪种书写体系，不做语言识别。
     */
    private boolean containsCjk(String text) {
        return text.codePoints().anyMatch(cp ->
                Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }

    private String reasonFor(MatchVerdict verdict) {
        return switch (verdict) {
            case MATCHED -> "搜索结果数量充足，且存在与意图相符的图片描述";
            case MISMATCHED -> "搜索结果数量充足，但图片描述与意图明显不符";
            case UNKNOWN -> "搜索结果数量充足，但描述语言与意图不一致，无法从描述判断匹配度";
        };
    }

    private List<String> suggestionsFor(MatchVerdict verdict) {
        return switch (verdict) {
            case MATCHED -> List.of("可以先展示搜索结果，询问用户满意度");
            case MISMATCHED -> List.of("图片类型不符合需求，建议换关键词重搜或使用 AIGC 生成符合意图的图片");
            case UNKNOWN -> List.of("先展示搜索结果，由用户判断是否符合意图；用户明确不符时再换策略");
        };
    }

    /**
     * 由匹配比例换算匹配度分数。
     * <p>
     * 无法判断时给中性分，而不是按零命中折算——分数会进模型上下文，
     * 折算成低分会诱导模型自行得出「不匹配」的结论，绕开 verdict。
     */
    private double scoreFromMatchRatio(int matchCount, int total, MatchVerdict verdict) {
        if (verdict == MatchVerdict.UNKNOWN) {
            return UNKNOWN_SCORE;
        }
        double matchRatio = (double) matchCount / total;
        return matchRatio >= 0.5
                ? Math.min(0.9, 0.6 + matchRatio * 0.3)
                : NEUTRAL_SCORE;
    }

    /**
     * 判断用户意图是否与单张图片匹配
     *
     * @param normalizedIntent 规范化的用户意图（小写）
     * @param intentWords      预切分好的意图关键词
     * @param image 图片资源
     * @return 是否匹配
     */
    private boolean isIntentMatchImage(String normalizedIntent, String[] intentWords, ImageResource image) {
        // 检查类别关键词
        ImageCategoryEnum category = image.getCategory();
        if (category != null) {
            String categoryText = category.getText().toLowerCase(Locale.ROOT);
            String categoryValue = category.getValue().toLowerCase(Locale.ROOT);

            if (normalizedIntent.contains(categoryText) || normalizedIntent.contains(categoryValue)) {
                return true;
            }

            // 额外的中文关键词映射
            if (category == ImageCategoryEnum.LOGO && normalizedIntent.contains("标志")) {
                return true;
            }
            if (category == ImageCategoryEnum.ILLUSTRATION && (normalizedIntent.contains("插画") || normalizedIntent.contains("插图"))) {
                return true;
            }
            if (category == ImageCategoryEnum.ARCHITECTURE && (normalizedIntent.contains("架构") || normalizedIntent.contains("结构图"))) {
                return true;
            }
        }

        // 检查描述关键词
        String description = image.getDescription();
        if (description != null && !description.isEmpty()) {
            String normalizedDesc = description.toLowerCase(Locale.ROOT);
            // 简单的关键词重叠检查（intentWords 在调用方预先切分，避免每张图重复 split）
            for (String word : intentWords) {
                if (word.length() >= 2 && normalizedDesc.contains(word)) {
                    return true;
                }
            }
            // 中文没有词边界，整句 split 出来只有一个词，上面的整词包含几乎必然失败。
            // 改用 bigram 重叠做同语言下的粗略匹配，避免同语言场景被误判为不匹配。
            if (sharesCjkBigram(normalizedIntent, normalizedDesc)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 中文字符 bigram 是否有交集。
     * <p>
     * 只处理 CJK 部分，英文部分交给上面的整词匹配。bigram 是刻意粗糙的做法：
     * 这个评估器的职责是「判断关键词比较是否有意义」，不是做语义相似度，
     * 宁可放过（判 MATCHED）也不要误杀（判 MISMATCHED 会让 Agent 重搜空转）。
     */
    private boolean sharesCjkBigram(String intent, String description) {
        Set<String> descBigrams = cjkBigrams(description);
        if (descBigrams.isEmpty()) {
            return false;
        }
        for (String bigram : cjkBigrams(intent)) {
            if (descBigrams.contains(bigram)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 抽取文本中相邻两个 CJK 字符组成的 bigram。
     */
    private Set<String> cjkBigrams(String text) {
        Set<String> bigrams = new HashSet<>();
        int prev = -1;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            boolean isHan = Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN;
            if (isHan && prev >= 0) {
                bigrams.add(new String(Character.toChars(prev)) + new String(Character.toChars(cp)));
            }
            prev = isHan ? cp : -1;
            i += Character.charCount(cp);
        }
        return bigrams;
    }
}
