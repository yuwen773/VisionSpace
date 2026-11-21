package com.yuwen.visionspace.agent.recovery;

import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外部动作政策：分类与措辞只有这一份。
 */
class ExternalActionPolicyTest {

    @Test
    void readOnlyToolsCanSafelyRetryIncludingInternalSearch() {
        assertTrue(ExternalActionPolicy.canSafelyRetry("searchContentImages"));
        assertTrue(ExternalActionPolicy.canSafelyRetry("searchSimilarImages"));
        assertTrue(ExternalActionPolicy.canSafelyRetry("searchIllustrations"));
        assertTrue(ExternalActionPolicy.canSafelyRetry("searchInternalImages"));
        assertTrue(ExternalActionPolicy.canSafelyRetry("analyzeImageFeatures"));
        assertTrue(ExternalActionPolicy.canSafelyRetry("evaluateImageQuality"));
    }

    @Test
    void toolsWithSideEffectsNeedReview() {
        assertFalse(ExternalActionPolicy.canSafelyRetry("generateImages"));
        assertFalse(ExternalActionPolicy.canSafelyRetry(null));
        assertEquals(ExternalActionPolicy.Outcome.NEEDS_REVIEW,
                ExternalActionPolicy.unknownOutcome("generateImages"));
        assertEquals(ExternalActionPolicy.Outcome.SAFE_TO_RETRY,
                ExternalActionPolicy.unknownOutcome("searchInternalImages"));
    }

    @Test
    void pausingIsTheSameDecisionForInterceptorsAndEntryPoints() {
        PendingReviewException error = ExternalActionPolicy.pauseForReview("generateImages");

        assertEquals("generateImages", error.getToolName());
        assertEquals("该步骤的外部执行结果未知，已暂停，请核对后再继续",
                ExternalActionPolicy.pausedMessage());
        assertTrue(ExternalActionPolicy.pausedMessage("generateImages")
                .contains("generateImages"));
    }

    @Test
    void confirmationTextIsSharedByBothEntryPoints() {
        List<InterruptionMetadata.ToolFeedback> feedbacks = List.of(
                InterruptionMetadata.ToolFeedback.builder()
                        .id("call-1").name("generateImages").arguments("{}")
                        .description("需要确认").build(),
                InterruptionMetadata.ToolFeedback.builder()
                        .id("call-2").name("generateImages").arguments("{}")
                        .description("风格符合预期").build());

        assertEquals("工具: generateImages\n评估: 需要确认\n"
                        + "工具: generateImages\n评估: 风格符合预期\n",
                ExternalActionPolicy.confirmationText(feedbacks));
        assertEquals("", ExternalActionPolicy.confirmationText(null));
        assertEquals("", ExternalActionPolicy.confirmationText(List.of()));
    }

    @Test
    void needsReviewExceptionCarriesTheToolName() {
        assertThrows(PendingReviewException.class,
                () -> { throw ExternalActionPolicy.pauseForReview("generateImages"); });
    }
}
