package com.yuwen.visionspace.config;

import java.util.Collection;
import java.util.Locale;

/**
 * 生产 profile 的唯一判定。
 *
 * <p>「生产」在不同守卫里含义必须一致：启动时的秘密检查、MCP 危险能力封禁、
 * 出站策略的放宽开关都以此为准，避免各处各写一套后其中一处先被绕过。
 */
public final class ProductionProfiles {

    private ProductionProfiles() {
    }

    public static boolean isProduction(Collection<String> activeProfiles) {
        if (activeProfiles == null) {
            return false;
        }
        return activeProfiles.stream()
                .filter(profile -> profile != null)
                .map(profile -> profile.trim().toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("prod") || profile.equals("production"));
    }

    public static boolean isProduction(String[] activeProfiles) {
        return activeProfiles != null && isProduction(java.util.List.of(activeProfiles));
    }
}
