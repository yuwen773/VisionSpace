package com.yuwen.visionspace.service;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码摘要：新密码使用每用户独立盐的自适应摘要，旧摘要只用于兼容验证。
 *
 * <p>存储格式为 BCrypt（{@code $2a$<cost>$<salt><hash>}）。BCrypt 只取前 72 字节，
 * 所以先对原始密码做 SHA-256 再 Base64（43 字符）交给 BCrypt，长度超出时不会
 * 出现两个密码共享同一前缀的等价问题。
 *
 * <p>旧数据是固定盐 {@code "yuwen"} 的 MD5（32 位小写十六进制）。它只用于验证，
 * 登录成功后由调用方替换为新摘要；验证失败不会改写任何数据。
 */
@Component
public class PasswordHasher {

    /**
     * 旧版固定盐。仅用于兼容验证存量摘要。
     */
    private static final String LEGACY_SALT = "yuwen";

    private static final int HASH_LENGTH = 32;

    private final SecureRandom random = new SecureRandom();

    private final int cost;

    public PasswordHasher(@Value("${vision-space.security.password-bcrypt-cost:12}") int cost) {
        if (cost < 4 || cost > 31) {
            throw new IllegalArgumentException("password-bcrypt-cost must be between 4 and 31");
        }
        this.cost = cost;
    }

    /**
     * 生成新摘要。每次调用都会生成独立盐。
     */
    public String hash(String rawPassword) {
        if (rawPassword == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码不能为空");
        }
        return cn.hutool.crypto.digest.BCrypt.hashpw(prehashed(rawPassword), salt());
    }

    /**
     * 校验原始密码与已存储摘要是否匹配。旧摘要同样可以验证成功。
     */
    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || storedPassword.isEmpty()) {
            return false;
        }
        if (isLegacy(storedPassword)) {
            return constantTimeEquals(legacyDigest(rawPassword), storedPassword.toLowerCase());
        }
        try {
            return cn.hutool.crypto.digest.BCrypt.checkpw(prehashed(rawPassword), storedPassword);
        } catch (IllegalArgumentException e) {
            // 存储值不是可识别的 BCrypt 摘要：按不匹配处理，不抛给调用方
            return false;
        }
    }

    /**
     * 是否需要在这次成功登录后升级：旧摘要，或成本低于当前配置的摘要。
     */
    public boolean needsUpgrade(String storedPassword) {
        if (storedPassword == null || storedPassword.isEmpty()) {
            return false;
        }
        if (isLegacy(storedPassword)) {
            return true;
        }
        return bcryptCost(storedPassword) < cost;
    }

    /**
     * 旧摘要判定：32 位小写十六进制。
     */
    public static boolean isLegacy(String storedPassword) {
        if (storedPassword == null || storedPassword.length() != HASH_LENGTH) {
            return false;
        }
        for (int i = 0; i < storedPassword.length(); i++) {
            char c = storedPassword.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private String legacyDigest(String rawPassword) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest((LEGACY_SALT + rawPassword).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xf, 16));
                hex.append(Character.forDigit(b & 0xf, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "密码校验失败");
        }
    }

    /**
     * BCrypt 输入统一为 SHA-256 的 Base64，避免 72 字节截断带来的前缀等价。
     */
    private String prehashed(String rawPassword) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "密码处理失败");
        }
    }

    private String salt() {
        return cn.hutool.crypto.digest.BCrypt.gensalt(cost, random);
    }

    private int bcryptCost(String storedPassword) {
        // 格式：$2a$12$...；版本标记 3 位 + cost 2 位
        if (storedPassword.length() < 7 || storedPassword.charAt(0) != '$') {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(storedPassword.substring(4, 6));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
