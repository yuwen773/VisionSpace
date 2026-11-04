package com.yuwen.visionspace.manager.storage;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 本实例标识：进程启动时生成的 UUID。
 *
 * <p>用于切换确认集合的幂等键（同一实例对同一版本只计一次确认）。
 * 进程重启会产生新标识：旧确认行保留（对应一次真实的候选应用），
 * 新进程重新确认时走同一条幂等路径。
 */
@Component
public class StorageInstanceIdProvider {

    private final String instanceId = UUID.randomUUID().toString();

    public String get() {
        return instanceId;
    }
}
