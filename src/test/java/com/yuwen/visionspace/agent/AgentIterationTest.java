package com.yuwen.visionspace.agent;

import com.yuwen.visionspace.agent.model.ActionType;
import com.yuwen.visionspace.agent.model.ImageCategoryEnum;
import com.yuwen.visionspace.agent.model.ImageResource;
import com.yuwen.visionspace.agent.model.MatchVerdict;
import com.yuwen.visionspace.agent.model.QualityResult;
import com.yuwen.visionspace.agent.tools.QualityEvaluatorTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import com.yuwen.visionspace.acceptance.IsolatedSpringBootTest;
import org.springframework.test.context.TestPropertySource;

import jakarta.annotation.Resource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Agent 迭代优化机制的装配测试。
 * <p>
 * 与 {@code QualityEvaluatorToolTest} 互补而非重复：后者用 {@code new} 构造被测对象，
 * 覆盖的是纯逻辑；这里走 Spring 容器，覆盖的是「工具确实被注册成容器 bean 且能被
 * 反射识别为 @Tool」——{@code ImageAgent} 通过 {@code methodTools(...)} 拿到的正是
 * 这个 bean，装配一旦断掉，Agent 会在运行期静默失去质量评估能力。
 */
@IsolatedSpringBootTest
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

    /**
     * 工具方法必须带 @Tool 注解且 name 非空，否则 Spring AI 无法把它暴露给模型。
     */
    @Test
    void evaluateImageQuality_isAnnotatedAsTool() throws NoSuchMethodException {
        Tool tool = QualityEvaluatorTool.class
                .getMethod("evaluateImageQuality", List.class, String.class)
                .getAnnotation(Tool.class);

        assertNotNull(tool, "evaluateImageQuality 缺少 @Tool 注解，模型将看不到该工具");
        assertFalse(tool.name().isBlank(), "工具名不能为空");
    }

    /**
     * 容器里的实例必须能正常完成一次评估，而不是只有对象存在。
     */
    @Test
    void injectedTool_producesUsableVerdict() {
        List<ImageResource> images = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            images.add(ImageResource.builder()
                    .category(ImageCategoryEnum.CONTENT)
                    .description("technology office team")
                    .url("http://test" + i + ".jpg")
                    .build());
        }

        QualityResult result = qualityEvaluatorTool.evaluateImageQuality(images, "technology office banner");

        assertEquals(MatchVerdict.MATCHED, result.getVerdict());
        assertEquals(ActionType.RETURN, result.getAction());
        assertNotNull(result.getReason());
        assertFalse(result.getSuggestions().isEmpty());
    }
}
