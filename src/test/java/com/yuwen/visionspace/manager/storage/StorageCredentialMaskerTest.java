package com.yuwen.visionspace.manager.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 脱敏规则：响应只出现固定遮蔽值，请求中的遮蔽/空白一律视为"不修改"。
 */
class StorageCredentialMaskerTest {

    @Test
    void configuredSecretsBecomeFixedMask() {
        assertEquals(StorageCredentialMasker.MASK, StorageCredentialMasker.mask("AK-real-value"));
        assertNull(StorageCredentialMasker.mask(null));
        assertNull(StorageCredentialMasker.mask("   "));
    }

    @Test
    void maskAndEmptyRequestsAreNotReplacements() {
        assertFalse(StorageCredentialMasker.isReplacement(null));
        assertFalse(StorageCredentialMasker.isReplacement(""));
        assertFalse(StorageCredentialMasker.isReplacement("   "));
        assertFalse(StorageCredentialMasker.isReplacement(StorageCredentialMasker.MASK));
    }

    @Test
    void differentValueIsAlwaysAReplacement() {
        assertTrue(StorageCredentialMasker.isReplacement("AK-new-value"));
        assertTrue(StorageCredentialMasker.isReplacement("  padded  "));
    }
}
