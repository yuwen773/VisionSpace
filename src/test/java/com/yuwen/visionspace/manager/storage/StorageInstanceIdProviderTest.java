package com.yuwen.visionspace.manager.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实例标识持久化：同一物理实例跨重启同 ID，不同实例互不相同；
 * 文件损坏或不可写时显式退化，不静默。
 */
class StorageInstanceIdProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void reusesPersistedIdAcrossRestarts() throws Exception {
        Path idFile = tempDir.resolve("instance-id");
        StorageInstanceIdProvider first = new StorageInstanceIdProvider(idFile.toString());
        // 模拟重启：新进程、同一文件
        StorageInstanceIdProvider second = new StorageInstanceIdProvider(idFile.toString());

        assertEquals(first.get(), second.get(), "同一物理实例重启后必须复用同一标识");
        UUID.fromString(first.get());
        assertTrue(Files.isRegularFile(idFile));
    }

    @Test
    void differentIdFilesYieldDifferentIds() {
        StorageInstanceIdProvider a = new StorageInstanceIdProvider(
                tempDir.resolve("id-a").toString());
        StorageInstanceIdProvider b = new StorageInstanceIdProvider(
                tempDir.resolve("id-b").toString());

        assertNotEquals(a.get(), b.get(), "不同实例必须持有不同标识");
    }

    @Test
    void corruptIdFileIsRegeneratedExplicitly() throws Exception {
        Path idFile = tempDir.resolve("instance-id");
        Files.writeString(idFile, "not-a-valid-uuid");

        StorageInstanceIdProvider provider = new StorageInstanceIdProvider(idFile.toString());

        UUID.fromString(provider.get());
        // 重新生成后文件被有效标识覆盖
        assertEquals(provider.get(), Files.readString(idFile).trim());
    }

    /**
     * 文件路径不可写（此处为目录）时退化为进程级 UUID：
     * 标识仍可用，但不保证跨重启稳定——该退化必须显式可见，不静默。
     */
    @Test
    void unwritablePathFallsBackToProcessLevelId() throws Exception {
        // 用目录充当不可写的标识文件路径：读不存在、写必失败
        Path directory = Files.createDirectory(tempDir.resolve("id-is-a-directory"));
        StorageInstanceIdProvider first = new StorageInstanceIdProvider(directory.toString());
        StorageInstanceIdProvider second = new StorageInstanceIdProvider(directory.toString());

        UUID.fromString(first.get());
        // 退化为进程级：两次构造各得一个不同标识（如实反映旧行为）
        assertNotEquals(first.get(), second.get());
        assertFalse(Files.isRegularFile(directory));
    }
}
