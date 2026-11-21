package com.yuwen.visionspace.rag;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 站内检索的健康信号。
 *
 * <p>只反映向量库是否连上，<b>不进入 readiness 组</b>（{@code management.endpoint.health.group.readiness}
 * 只包含 readinessState/db/redis）：向量库挂了应当降级，不该让容器被判定为不可用而反复重启。
 * 没被用过时报告 UNKNOWN——「还没连过」不等于「坏了」。</p>
 */
@Component
@ConditionalOnProperty(name = RagVectorStore.PROPERTY, havingValue = RagVectorStore.CHROMA)
public class RagHealthIndicator implements HealthIndicator {

    private final ResilientVectorStore vectorStore;

    public RagHealthIndicator(ResilientVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public Health health() {
        if (vectorStore.isAvailable()) {
            return Health.up().build();
        }
        if (!vectorStore.wasAttempted()) {
            return Health.unknown().withDetail("reason", "尚未使用，未连接向量库").build();
        }
        return Health.down().withDetail("reason", vectorStore.lastError()).build();
    }
}
