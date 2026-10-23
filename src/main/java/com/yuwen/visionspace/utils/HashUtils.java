package com.yuwen.visionspace.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 摘要工具。
 *
 * <p>事件幂等键、缓存分槽键与脱敏后的客户端标识都从这里派生，避免各处
 * 各写一份 SHA-256 计算。
 */
public final class HashUtils {

    /** SHA-256 摘要的字节长度 */
    private static final int SHA256_BYTES = 32;

    private HashUtils() {
    }

    /**
     * 计算完整的 SHA-256 十六进制摘要（64 位小写十六进制字符）。
     */
    public static String sha256Hex(String material) {
        return toHex(digest(material), SHA256_BYTES);
    }

    /**
     * 计算 SHA-256 摘要的前若干个字节对应的十六进制字符。
     *
     * @param bytes 取用的字节数，8 表示 16 位十六进制字符
     */
    public static String shortHash(String material, int bytes) {
        int length = Math.min(Math.max(bytes, 1), SHA256_BYTES);
        return toHex(digest(material), length);
    }

    private static byte[] digest(String material) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest((material == null ? "" : material).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法计算 SHA-256 摘要", e);
        }
    }

    private static String toHex(byte[] hash, int length) {
        StringBuilder builder = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            builder.append(Character.forDigit((hash[i] >> 4) & 0xF, 16));
            builder.append(Character.forDigit(hash[i] & 0xF, 16));
        }
        return builder.toString();
    }
}
