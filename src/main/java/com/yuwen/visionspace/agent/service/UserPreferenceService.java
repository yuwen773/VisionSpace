package com.yuwen.visionspace.agent.service;

import com.alibaba.cloud.ai.graph.store.stores.MemoryStore;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 用户偏好服务
 * 负责保存和加载用户偏好到 MemoryStore
 */
@Slf4j
@Service
public class UserPreferenceService {

    private static final String NAMESPACE_PREFIX = "preferences";

    private final MemoryStore memoryStore;

    public UserPreferenceService(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    /**
     * 保存用户反馈
     */
    public void saveFeedback(String userId, String action, String reason) {
        List<String> namespace = List.of(NAMESPACE_PREFIX, userId);
        String key = "user_profile";

        // 获取现有偏好
        Map<String, Object> profile = getOrCreateProfile(namespace, key);

        // 更新反馈历史
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) profile.getOrDefault("feedback_history", new ArrayList<>());
        Map<String, Object> feedback = new HashMap<>();
        feedback.put("time", new Date().toString());
        feedback.put("action", action);
        feedback.put("reason", reason);
        history.add(feedback);
        profile.put("feedback_history", history);

        // 分析并更新偏好
        if (action != null && action.equals("regenerate") && reason != null) {
            updatePreferencesFromFeedback(profile, reason);
        }

        // 保存
        StoreItem item = StoreItem.of(namespace, key, profile);
        memoryStore.putItem(item);
        log.info("保存用户偏好, userId={}, profile={}", userId, profile);
    }

    /**
     * 获取用户偏好
     */
    public Map<String, Object> getUserPreferences(String userId) {
        List<String> namespace = List.of(NAMESPACE_PREFIX, userId);
        Optional<StoreItem> item = memoryStore.getItem(namespace, "user_profile");

        if (item.isPresent()) {
            return item.get().getValue();
        }
        return new HashMap<>();
    }

    private Map<String, Object> getOrCreateProfile(List<String> namespace, String key) {
        Optional<StoreItem> item = memoryStore.getItem(namespace, key);
        if (item.isPresent()) {
            return new HashMap<>(item.get().getValue());
        }
        return new HashMap<>();
    }

    private void updatePreferencesFromFeedback(Map<String, Object> profile, String reason) {
        // 简单的关键词提取
        List<String> likedStyles = getOrCreateList(profile, "liked_styles");
        List<String> dislikedStyles = getOrCreateList(profile, "disliked_styles");
        List<String> likedColors = getOrCreateList(profile, "liked_colors");

        // 检测颜色偏好
        if (reason.contains("暗")) {
            dislikedStyles.add("暗色调");
            likedColors.add("亮色系");
        }
        if (reason.contains("亮") || reason.contains("太亮")) {
            dislikedStyles.add("过亮色调");
        }

        // 检测风格偏好
        if (reason.contains("赛博朋克")) {
            likedStyles.add("赛博朋克");
        }
        if (reason.contains("写实")) {
            dislikedStyles.add("过于写实");
        }

        profile.put("liked_styles", likedStyles);
        profile.put("disliked_styles", dislikedStyles);
        profile.put("liked_colors", likedColors);
    }

    @SuppressWarnings("unchecked")
    private List<String> getOrCreateList(Map<String, Object> profile, String key) {
        Object value = profile.get(key);
        if (value instanceof List) {
            return new ArrayList<>((List<String>) value);
        }
        return new ArrayList<>();
    }
}
