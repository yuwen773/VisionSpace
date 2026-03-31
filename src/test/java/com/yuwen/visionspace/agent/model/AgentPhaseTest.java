package com.yuwen.visionspace.agent.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentPhaseTest {

    @Test
    void testPhaseValues() {
        assertEquals(3, AgentPhase.values().length);
        assertNotNull(AgentPhase.EXPLORATION);
        assertNotNull(AgentPhase.GENERATION);
        assertNotNull(AgentPhase.DONE);
    }

    @Test
    void testPhaseText() {
        assertNotNull(AgentPhase.EXPLORATION.getText());
        assertNotNull(AgentPhase.GENERATION.getText());
        assertNotNull(AgentPhase.DONE.getText());
    }
}
