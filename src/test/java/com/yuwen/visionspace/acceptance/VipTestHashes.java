package com.yuwen.visionspace.acceptance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 测试内复现服务端兑换码摘要算法，避免测试与实现共享同一个工具类而失去对抗性。
 */
final class VipTestHashes {

    private VipTestHashes() {
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
