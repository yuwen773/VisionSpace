package com.yuwen.visionspace.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 兑换码摘要（#39）：sha-256 十六进制小写。
 * 生产侧唯一入口；验收测试另持独立实现以保持对抗性。
 */
public final class CodeHashes {

    private CodeHashes() {
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
