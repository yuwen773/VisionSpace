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

    /** 短摘要取用的字节数：8 字节即 16 位十六进制字符 */
    private static final int SHORT_HASH_BYTES = 8;

    private HashUtils() {
    }

    /**
     * 计算完整的 SHA-256 十六进制摘要（64 位小写十六进制字符）。
     */
    public static String sha256Hex(String material) {
        return toHex(digest(material), SHA256_BYTES);
    }

    /**
     * 计算 SHA-256 摘要前 8 个字节对应的十六进制字符（16 位），用于缓存分槽
     * 与脱敏标识这类只需要低碰撞概率前缀的场合。
     */
    public static String shortHash(String material) {
        return toHex(digest(material), SHORT_HASH_BYTES);
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
