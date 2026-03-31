package com.yuwen.visionspace.agent;

import com.yuwen.visionspace.agent.tools.QualityEvaluatorTool;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import jakarta.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Agent 迭代优化机制测试
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.ai.dashscope.api-key=test-key"
})
class AgentIterationTest {

    @Resource
    private QualityEvaluatorTool qualityEvaluatorTool;

    @Test
    void testQualityEvaluatorToolExists() {
        assertNotNull(qualityEvaluatorTool);
    }
}
