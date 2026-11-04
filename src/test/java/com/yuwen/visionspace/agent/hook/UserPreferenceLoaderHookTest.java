package com.yuwen.visionspace.agent.hook;

import com.yuwen.visionspace.agent.service.UserPreferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 偏好注入模型消息的渲染单元测试（不触真实模型）。
 *
 * <p>覆盖 #42 评审补充要求：偏好真正进入模型上下文——渲染片段包含
 * 显式设置与学习结论、无偏好时不注入、偏好读取失败时对话不受阻（fail-open
 * 但留 warn 日志）。
 */
class UserPreferenceLoaderHookTest {

    private static final long USER_ID = 7L;

    private UserPreferenceService service;
    private UserPreferenceLoaderHook hook;

    @BeforeEach
    void setUp() {
        service = mock(UserPreferenceService.class);
        hook = new UserPreferenceLoaderHook(service);
    }

    @Test
    void renderPreferenceBlock_noPreferences_rendersNothing() {
        when(service.loadForModel(USER_ID)).thenReturn(Map.of());

        assertEquals("", hook.renderPreferenceBlock(USER_ID), "无偏好不得注入空片段");
        assertEquals("", hook.renderPreferenceBlock(null));
    }

    @Test
    void renderPreferenceBlock_rendersExplicitAndLearnedEntries() {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("liked_styles", List.of("水彩", "极简"));
        merged.put("disliked_styles", List.of("过于写实"));
        merged.put("liked_colors", List.of("亮色系"));
        merged.put("notes", "整体偏亮");
        when(service.loadForModel(USER_ID)).thenReturn(merged);

        String block = hook.renderPreferenceBlock(USER_ID);

        assertTrue(block.contains("[用户长期偏好]"), block);
        assertTrue(block.contains("喜欢的风格: 水彩、极简"), block);
        assertTrue(block.contains("不喜欢的风格: 过于写实"), block);
        assertTrue(block.contains("喜欢的色调: 亮色系"), block);
        assertTrue(block.contains("补充说明: 整体偏亮"), block);
        // 显式优先的合并语义由服务层保证；片段如实带出合并结果
        assertTrue(block.startsWith("\n\n"), "片段以分隔开头，避免与用户消息粘连");
    }

    @Test
    void renderPreferenceBlock_serviceFailure_failsOpenWithoutBreakingChat() {
        when(service.loadForModel(USER_ID)).thenThrow(new RuntimeException("db down"));

        assertEquals("", hook.renderPreferenceBlock(USER_ID), "偏好读取失败不得中断对话");
    }
}
