package com.yuwen.visionspace.agent.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QualityResultTest {

    @Test
    void testBuilderWithNewFields() {
        QualityResult result = QualityResult.builder()
                .matchScore(0.8)
                .reason("匹配良好")
                .suggestions(List.of("可展示"))
                .action(ActionType.RETURN)
                .phase(AgentPhase.EXPLORATION)
                .exploreCount(2)
                .build();

        assertEquals(0.8, result.getMatchScore());
        assertEquals(AgentPhase.EXPLORATION, result.getPhase());
        assertEquals(2, result.getExploreCount());
    }

    @Test
    void testBuilderWithNullNewFields() {
        QualityResult result = QualityResult.builder()
                .matchScore(0.5)
                .action(ActionType.RESEARCH)
                .build();

        assertNull(result.getPhase());
        assertNull(result.getExploreCount());
    }
}
