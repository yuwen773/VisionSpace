package com.yuwen.visionspace.agent;

import com.yuwen.visionspace.acceptance.IsolatedSpringBootTest;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注册表与工具模块必须一致。
 *
 * <p>曾经有两个工具（插画搜索、Mermaid 生成）实现完整却从未注册进
 * {@code methodTools()}，而系统提示还在让模型调用其中一个——模型调用不到的工具，
 * 只会变成一次失败的尝试。这个测试把「声明了 @Tool 却没注册」变成红色。
 */
@IsolatedSpringBootTest
@TestPropertySource(properties = {
    "spring.ai.dashscope.api-key=test-key"
})
class ImageAgentToolRegistryTest {

    private static final String TOOLS_PACKAGE = "com.yuwen.visionspace.agent.tools";

    @Resource
    private ImageAgent imageAgent;

    @Resource
    private ApplicationContext context;

    @Test
    void everyToolComponentIsRegisteredWithTheAgent() throws NoSuchMethodException {
        Set<String> declared = declaredToolNames();
        Set<String> registered = registeredToolNames();

        assertTrue(declared.contains("searchContentImages"), "工具扫描没有找到已注册的工具");
        assertEquals(declared, registered,
                "存在声明了 @Tool 却没有注册给 Agent 的工具，或注册了未声明的工具");
    }

    /** 工具包里每个 @Tool 方法的名字。 */
    private Set<String> declaredToolNames() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        Set<String> names = new TreeSet<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(TOOLS_PACKAGE)) {
            Class<?> type;
            try {
                type = Class.forName(definition.getBeanClassName());
            } catch (ClassNotFoundException e) {
                throw new AssertionError("无法加载工具类: " + definition.getBeanClassName(), e);
            }
            // 条件装配关闭的工具（例如未启用站内检索时的向量工具）不参与比对
            if (context.getBeansOfType(type).isEmpty()) {
                continue;
            }
            for (Method method : type.getDeclaredMethods()) {
                Tool tool = method.getAnnotation(Tool.class);
                if (tool != null) {
                    names.add(tool.name());
                }
            }
        }
        return names;
    }

    /** {@code ImageAgent.methodTools()} 实际传给 ReactAgent 的工具名。 */
    private Set<String> registeredToolNames() throws NoSuchMethodException {
        Object[] tools = (Object[]) ReflectionTestUtils.invokeMethod(imageAgent, "methodTools");
        assertTrue(tools != null && tools.length > 0, "Agent 没有注册任何工具");
        return Arrays.stream(tools)
                .flatMap(tool -> Arrays.stream(tool.getClass().getDeclaredMethods()))
                .map(method -> method.getAnnotation(Tool.class))
                .filter(Objects::nonNull)
                .map(Tool::name)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
