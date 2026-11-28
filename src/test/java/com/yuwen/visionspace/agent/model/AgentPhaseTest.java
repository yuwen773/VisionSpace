package com.yuwen.visionspace.agent.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentPhaseTest {

    @Test
    void testPhaseText() {
        assertNotNull(AgentPhase.EXPLORATION.getText());
        assertNotNull(AgentPhase.GENERATION.getText());
        assertNotNull(AgentPhase.DONE.getText());
    }
}
