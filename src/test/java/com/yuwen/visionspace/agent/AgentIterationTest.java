package com.yuwen.visionspace.agent;

import com.yuwen.visionspace.agent.model.ImageResource;
import com.yuwen.visionspace.agent.model.QualityResult;
import com.yuwen.visionspace.agent.tools.QualityEvaluatorTool;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

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
    void testEvaluateEmptyImages() {
        QualityResult result = qualityEvaluatorTool.evaluateImageQuality(
                new ArrayList<>(), "赛博朋克风格城市");
        assertEquals(0.0, result.getMatchScore());
        assertEquals("regenerate", result.getAction());
    }

    @Test
    void testEvaluateNullImages() {
        QualityResult result = qualityEvaluatorTool.evaluateImageQuality(
                null, "赛博朋克风格城市");
        assertEquals(0.0, result.getMatchScore());
        assertEquals("regenerate", result.getAction());
    }

    @Test
    void testEvaluateFewImages() {
        List<ImageResource> images = new ArrayList<>();
        images.add(ImageResource.builder().url("http://test.jpg").build());

        QualityResult result = qualityEvaluatorTool.evaluateImageQuality(
                images, "赛博朋克风格城市");
        assertEquals(0.3, result.getMatchScore());
        assertEquals("regenerate", result.getAction());
    }

    @Test
    void testEvaluateEnoughImages() {
        List<ImageResource> images = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            images.add(ImageResource.builder().url("http://test" + i + ".jpg").build());
        }

        QualityResult result = qualityEvaluatorTool.evaluateImageQuality(
                images, "赛博朋克风格城市");
        assertEquals(0.6, result.getMatchScore());
        assertEquals("return", result.getAction());
    }

    @Test
    void testEvaluateThreeImages() {
        List<ImageResource> images = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            images.add(ImageResource.builder().url("http://test" + i + ".jpg").build());
        }

        QualityResult result = qualityEvaluatorTool.evaluateImageQuality(
                images, "猫咪图片");
        assertEquals(0.6, result.getMatchScore());
        assertEquals("return", result.getAction());
    }
}
