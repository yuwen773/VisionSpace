package com.yuwen.visionspace.agent.hook;

import com.yuwen.visionspace.agent.service.UserPreferenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 模型调用前加载用户长期偏好并渲染为提示词片段。
 *
 * <p>偏好已持久化到数据库（见 {@link UserPreferenceService}），每次调用
 * 直接读库：进程内不再缓存偏好，也就不存在「实例 A 已清除、实例 B 还在用
 * 旧缓存」或「重启后旧值复活」的问题。渲染出的合并视图里显式设置覆盖
 * 模型推断（见 {@code UserPreferenceService#loadForModel}）。
 */
@Slf4j
@Component
public class UserPreferenceLoaderHook {

    private static final String KEY_LIKED_STYLES = "liked_styles";
    private static final String KEY_DISLIKED_STYLES = "disliked_styles";
    private static final String KEY_LIKED_COLORS = "liked_colors";
    private static final String KEY_NOTES = "notes";

    private final UserPreferenceService userPreferenceService;

    public UserPreferenceLoaderHook(UserPreferenceService userPreferenceService) {
        this.userPreferenceService = userPreferenceService;
    }

    /**
     * 加载用户偏好（显式设置优先于推断）。无偏好时返回空 map。
     */
    public Map<String, Object> loadUserPreferences(Long userId) {
        if (userId == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> preferences = userPreferenceService.loadForModel(userId);
        if (preferences.isEmpty()) {
            log.debug("用户无偏好, userId={}", userId);
        } else {
            log.debug("已加载用户偏好, userId={}, keys={}", userId, preferences.keySet());
        }
        return preferences;
    }

    /**
     * 渲染注入模型消息的偏好片段；无偏好返回空串（不注入）。
     *
     * <p>偏好读取失败时对话不因此中断（fail-open），但失败必须可见——记
     * warn 日志后按无偏好处理；偏好缺失只会让本次回答少一点个性化，
     * 不会产生错误副作用。
     */
    public String renderPreferenceBlock(Long userId) {
        Map<String, Object> preferences;
        try {
            preferences = loadUserPreferences(userId);
        } catch (Exception e) {
            log.warn("偏好加载失败，本次对话按无偏好处理, userId={}, error={}", userId, e.getMessage());
            return "";
        }
        if (preferences.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n[用户长期偏好]（以下为用户显式设置与历史学习结论，显式设置优先）");
        appendEntry(sb, "喜欢的风格", preferences.get(KEY_LIKED_STYLES));
        appendEntry(sb, "不喜欢的风格", preferences.get(KEY_DISLIKED_STYLES));
        appendEntry(sb, "喜欢的色调", preferences.get(KEY_LIKED_COLORS));
        appendEntry(sb, "补充说明", preferences.get(KEY_NOTES));
        // 其余键（模型此前学到的非标准键）原样带出，不丢弃也不臆测含义
        for (Map.Entry<String, Object> entry : preferences.entrySet()) {
            if (KEY_LIKED_STYLES.equals(entry.getKey()) || KEY_DISLIKED_STYLES.equals(entry.getKey())
                    || KEY_LIKED_COLORS.equals(entry.getKey()) || KEY_NOTES.equals(entry.getKey())) {
                continue;
            }
            appendEntry(sb, entry.getKey(), entry.getValue());
        }
        return sb.toString();
    }

    private void appendEntry(StringBuilder sb, String label, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof List list) {
            if (list.isEmpty()) {
                return;
            }
            sb.append("\n- ").append(label).append(": ").append(join(list));
            return;
        }
        String text = String.valueOf(value).trim();
        if (!text.isEmpty()) {
            sb.append("\n- ").append(label).append(": ").append(text);
        }
    }

    private String join(List<Object> items) {
        StringBuilder sb = new StringBuilder();
        for (Object item : items) {
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(item);
        }
        return sb.toString();
    }
}
