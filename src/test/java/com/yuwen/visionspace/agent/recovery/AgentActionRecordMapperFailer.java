package com.yuwen.visionspace.agent.recovery;

import com.yuwen.visionspace.mapper.AgentActionRecordMapper;

import java.lang.reflect.Proxy;

/**
 * 台账彻底不可用时的 Mapper 替身：每个方法都抛错。
 *
 * <p>用来证明「台账故障」不会被说成「已完成」——评审发现的正是这种情况被伪装成成功。
 */
final class AgentActionRecordMapperFailer {

    AgentActionRecordMapper proxy() {
        return (AgentActionRecordMapper) Proxy.newProxyInstance(
                AgentActionRecordMapper.class.getClassLoader(),
                new Class<?>[]{AgentActionRecordMapper.class},
                (proxy, method, args) -> {
                    throw new IllegalStateException("ledger unavailable: " + method.getName());
                });
    }
}
