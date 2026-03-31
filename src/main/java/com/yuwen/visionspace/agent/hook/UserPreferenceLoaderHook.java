package com.yuwen.visionspace.agent.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import com.alibaba.cloud.ai.graph.store.stores.MemoryStore;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 用户偏好加载 Hook
 * 在模型调用前自动加载用户偏好到上下文
 */
public class UserPreferenceLoaderHook extends ModelHook {

    private final MemoryStore memoryStore;

    public UserPreferenceLoaderHook(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    @Override
    public String getName() {
        return "user_preference_loader";
    }

    @Override
    public HookPosition[] getHookPositions() {
        return new HookPosition[]{HookPosition.BEFORE_MODEL};
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(
            com.alibaba.cloud.ai.graph.OverAllState state,
            RunnableConfig config) {

        // 从配置中获取用户ID
        Optional<Object> userIdObj = config.metadata("user_id");
        if (userIdObj.isEmpty() || !(userIdObj.get() instanceof String)) {
            return CompletableFuture.completedFuture(Map.of());
        }

        String userId = (String) userIdObj.get();

        // 从记忆存储中加载用户偏好
        Optional<StoreItem> itemOpt = memoryStore.getItem(List.of("preferences", userId), "user_profile");
        if (itemOpt.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        Map<String, Object> profile = itemOpt.get().getValue();

        // 构建用户上下文
        StringBuilder context = new StringBuilder();
        context.append("\n\n【用户偏好上下文】\n");

        @SuppressWarnings("unchecked")
        List<String> likedStyles = (List<String>) profile.getOrDefault("liked_styles", Collections.emptyList());
        @SuppressWarnings("unchecked")
        List<String> dislikedStyles = (List<String>) profile.getOrDefault("disliked_styles", Collections.emptyList());
        @SuppressWarnings("unchecked")
        List<String> likedColors = (List<String>) profile.getOrDefault("liked_colors", Collections.emptyList());

        if (!likedStyles.isEmpty()) {
            context.append("- 用户喜欢的风格: ").append(String.join(", ", likedStyles)).append("\n");
        }
        if (!dislikedStyles.isEmpty()) {
            context.append("- 用户不喜欢的风格: ").append(String.join(", ", dislikedStyles)).append("\n");
        }
        if (!likedColors.isEmpty()) {
            context.append("- 用户喜欢的颜色: ").append(String.join(", ", likedColors)).append("\n");
        }

        if (likedStyles.isEmpty() && dislikedStyles.isEmpty() && likedColors.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        context.append("请参考用户偏好，生成更符合用户口味的内容。");

        // 返回需要注入的上下文
        Map<String, Object> result = new HashMap<>();
        result.put("user_preference_context", context.toString());

        return CompletableFuture.completedFuture(result);
    }
}
