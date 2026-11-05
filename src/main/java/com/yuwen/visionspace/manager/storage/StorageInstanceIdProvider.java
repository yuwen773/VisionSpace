package com.yuwen.visionspace.manager.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 本实例标识：首次启动生成并持久化到本地文件，重启复用同一标识。
 *
 * <p>用于切换确认集合的幂等键（同一实例对同一版本只计一次确认）。
 * 标识按「物理实例」而非「进程」稳定：进程重启读回同一文件，不会因重启
 * 换 ID 而在确认集合里占多席。文件路径经 {@code vs.instance-id-file}
 * 配置（默认工作目录下 {@code storage-instance-id}）；同机多实例部署
 * 必须为每个实例指定不同文件（隔离验收的第二实例由其入口类显式指定）。
 *
 * <p>残余限制（如实记录）：实例标识的持久性依赖该文件——文件被删除、
 * 或所在盘不可写时退化为进程级 UUID（显式 error 日志，不静默），此时
 * 重启后的实例会被确认为新实例。标识文件不属于任何持久化备份范围。
 */
@Slf4j
@Component
public class StorageInstanceIdProvider {

    private final String instanceId;

    public StorageInstanceIdProvider(
            @Value("${vs.instance-id-file:storage-instance-id}") String idFile) {
        this.instanceId = loadOrCreate(Path.of(idFile));
    }

    public String get() {
        return instanceId;
    }

    /**
     * 读回已持久化的实例标识；缺失时生成并写回。
     * 读取或写入失败都退化为进程级 UUID，但以 error 级别显式记录，不静默吞掉。
     */
    private static String loadOrCreate(Path idFile) {
        if (Files.isRegularFile(idFile)) {
            try {
                String stored = Files.readString(idFile, StandardCharsets.UTF_8).trim();
                if (isUsableUuid(stored)) {
                    return stored;
                }
                log.error("实例标识文件内容无效，将重新生成: file={}", idFile.toAbsolutePath());
            } catch (Exception e) {
                log.error("实例标识文件读取失败，退化为进程级 UUID: file={}", idFile.toAbsolutePath(), e);
                return UUID.randomUUID().toString();
            }
        }
        String generated = UUID.randomUUID().toString();
        try {
            Files.writeString(idFile, generated, StandardCharsets.UTF_8);
            log.info("已生成并持久化实例标识: file={}", idFile.toAbsolutePath());
        } catch (Exception e) {
            log.error("实例标识文件写入失败，退化为进程级 UUID（重启会换标识）: file={}",
                    idFile.toAbsolutePath(), e);
        }
        return generated;
    }

    private static boolean isUsableUuid(String value) {
        if (value.isEmpty() || value.length() > 64) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
