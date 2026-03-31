package com.yuwen.visionspace.agent.hook;

import com.alibaba.cloud.ai.graph.store.stores.MemoryStore;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hook to load user preferences before model invocation.
 * Uses a cache to short-circuit lookups for users confirmed to have no preferences.
 */
@Slf4j
@Component
public class UserPreferenceLoaderHook {

    private static final String NAMESPACE_PREFIX = "preferences";

    @Resource
    private MemoryStore memoryStore;

    /**
     * Cache to track users confirmed to have no preferences.
     * Key: userId, Value: Boolean.TRUE (indicates confirmed no preferences)
     */
    private final ConcurrentHashMap<String, Boolean> noPreferencesCache = new ConcurrentHashMap<>();

    /**
     * Load user preferences for the given user.
     * Short-circuits using cache if user is known to have no preferences.
     *
     * @param userId the user ID to load preferences for
     * @return the user preferences map, or empty map if not found
     */
    public Map<String, Object> loadUserPreferences(String userId) {
        if (userId == null || userId.isBlank()) {
            return Map.of();
        }

        // Short-circuit: check cache first for users known to have no preferences
        if (noPreferencesCache.containsKey(userId)) {
            log.debug("Short-circuiting preference lookup for userId={}, user has no preferences", userId);
            return Map.of();
        }

        // Cache miss: look up in MemoryStore
        List<String> namespace = List.of(NAMESPACE_PREFIX, userId);
        Optional<StoreItem> item = memoryStore.getItem(namespace, "user_profile");

        if (item.isEmpty()) {
            // Cache the negative result to avoid future lookups
            noPreferencesCache.put(userId, Boolean.TRUE);
            log.debug("No preferences found for userId={}, cached negative result", userId);
            return Map.of();
        }

        log.debug("Loaded preferences for userId={}: {}", userId, item.get().getValue());
        return item.get().getValue();
    }

    /**
     * Clear the cache entry for a user (e.g., after preferences are updated).
     *
     * @param userId the user ID to clear from cache
     */
    public void clearCache(String userId) {
        if (userId != null) {
            noPreferencesCache.remove(userId);
            log.debug("Cleared cache for userId={}", userId);
        }
    }
}
