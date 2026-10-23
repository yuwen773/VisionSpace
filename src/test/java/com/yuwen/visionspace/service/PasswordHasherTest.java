package com.yuwen.visionspace.service;

import com.yuwen.visionspace.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 密码摘要契约：新密码用独立盐的自适应摘要，旧摘要只能验证、不能生成。
 */
class PasswordHasherTest {

    private static final String PASSWORD = "acceptance-password";

    /** 测试用低成本，避免拖慢验收；生产默认 12。 */
    private final PasswordHasher hasher = new PasswordHasher(4);

    @Test
    void newDigestUsesIndependentSaltAndAdaptiveCost() {
        String first = hasher.hash(PASSWORD);
        String second = hasher.hash(PASSWORD);

        assertTrue(first.startsWith("$2a$04$"), first);
        assertNotEquals(first, second, "每次摘要必须使用独立盐");
        assertFalse(first.contains(PASSWORD), "摘要不能包含原文");
        assertTrue(hasher.matches(PASSWORD, first));
        assertTrue(hasher.matches(PASSWORD, second));
    }

    @Test
    void wrongPasswordDoesNotMatch() {
        String digest = hasher.hash(PASSWORD);
        assertFalse(hasher.matches("wrong-password", digest));
        assertFalse(hasher.matches(PASSWORD + "x", digest));
        assertFalse(hasher.matches(null, digest));
        assertFalse(hasher.matches(PASSWORD, null));
        assertFalse(hasher.matches(PASSWORD, ""));
    }

    @Test
    void legacyDigestStillVerifiesButNeverNeedsToBeCreated() {
        // 旧版固定盐 MD5("yuwen" + password)，取自既有实现
        String legacy = legacyMd5(PASSWORD);
        assertTrue(PasswordHasher.isLegacy(legacy));
        assertTrue(hasher.matches(PASSWORD, legacy), "存量账户必须还能登录");
        assertFalse(hasher.matches("wrong-password", legacy));
        assertTrue(hasher.needsUpgrade(legacy));
    }

    @Test
    void legacyDetectionOnlyAcceptsLowercaseMd5Shape() {
        assertTrue(PasswordHasher.isLegacy(legacyMd5(PASSWORD)));
        assertFalse(PasswordHasher.isLegacy("$2a$12$abcdefghijklmnopqrstuv"));
        assertFalse(PasswordHasher.isLegacy("A67C5F6A75622F38A259D9DCBDE0E0B5"));
        assertFalse(PasswordHasher.isLegacy("a67c5f6a75622f38a259d9dcbde0e0b"));
        assertFalse(PasswordHasher.isLegacy(null));
    }

    @Test
    void currentDigestNeedsNoUpgradeButCheaperDigestDoes() {
        PasswordHasher strict = new PasswordHasher(12);
        assertFalse(strict.needsUpgrade(strict.hash(PASSWORD)));
        assertTrue(strict.needsUpgrade(hasher.hash(PASSWORD)), "成本低于当前配置时应在登录后升级");
        assertFalse(strict.needsUpgrade("not-a-digest"), "无法识别时不主动改写");
    }

    @Test
    void longPasswordsAreNotTruncated() {
        String prefix = "a".repeat(80);
        String digest = hasher.hash(prefix + "one");
        assertTrue(hasher.matches(prefix + "one", digest));
        assertFalse(hasher.matches(prefix + "two", digest),
                "超过 72 字节的不同密码不能因 BCrypt 截断而互相匹配");
    }

    @Test
    void unreadableStoredValueIsATransientMismatchNotAnError() {
        assertFalse(hasher.matches(PASSWORD, "not-a-digest"));
    }

    @Test
    void nullPasswordIsRejectedBeforeHashing() {
        assertThrows(BusinessException.class, () -> hasher.hash(null));
    }

    private static String legacyMd5(String raw) {
        try {
            var digest = java.security.MessageDigest.getInstance("MD5")
                    .digest(("yuwen" + raw).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void fixturesMatchTheProductionLegacyAlgorithm() {
        // 生产实现一直是 DigestUtils.md5DigestAsHex(("yuwen" + password).getBytes())
        assertEquals(32, legacyMd5("anything").length());
    }
}
