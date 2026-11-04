package com.yuwen.visionspace.agent.hook;

import com.yuwen.visionspace.agent.service.UserPreferenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.Map;

/**
 * 模型调用前加载用户长期偏好。
 *
 * <p>偏好已持久化到数据库（见 {@link UserPreferenceService}），每次调用
 * 直接读库：进程内不再缓存偏好，也就不存在「实例 A 已清除、实例 B 还在用
 * 旧缓存」或「重启后旧值复活」的问题。显式设置在合并视图里覆盖模型推断。
 */
@Slf4j
@Component
public class UserPreferenceLoaderHook {

    @Resource
    private UserPreferenceService userPreferenceService;

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
}
