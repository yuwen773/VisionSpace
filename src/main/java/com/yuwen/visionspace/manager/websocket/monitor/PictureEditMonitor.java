package com.yuwen.visionspace.manager.websocket.monitor;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 图片编辑协作的可观察性计数器。
 *
 * <p>积压、拒绝、异常、非法消息、租约状态都要求可观察：
 * 这里集中维护各维度计数，通过 {@link #snapshot()} 暴露给管理端点，
 * 队列的实时积压由 Disruptor 环形缓冲剩余容量动态计算。
 */
@Component
public class PictureEditMonitor {

    private final AtomicLong received = new AtomicLong();
    private final AtomicLong rejectedByBackpressure = new AtomicLong();
    private final AtomicLong invalidMessages = new AtomicLong();
    private final AtomicLong processingFailures = new AtomicLong();
    private final AtomicLong authRejections = new AtomicLong();
    private final AtomicLong leaseConflicts = new AtomicLong();
    private final AtomicLong broadcastsSent = new AtomicLong();
    private final AtomicLong broadcastFailures = new AtomicLong();
    /** 各类失败最后一次发生的时间戳（epoch millis），便于判断「还在发生吗」。 */
    private final Map<String, AtomicLong> lastFailureAt = new ConcurrentHashMap<>();

    public void onReceived() {
        received.incrementAndGet();
    }

    public void onRejectedByBackpressure() {
        rejectedByBackpressure.incrementAndGet();
        markFailure("backpressure");
    }

    public void onInvalidMessage() {
        invalidMessages.incrementAndGet();
        markFailure("invalid-message");
    }

    public void onProcessingFailure() {
        processingFailures.incrementAndGet();
        markFailure("processing-failure");
    }

    public void onAuthRejection() {
        authRejections.incrementAndGet();
        markFailure("auth-rejection");
    }

    public void onLeaseConflict() {
        leaseConflicts.incrementAndGet();
        markFailure("lease-conflict");
    }

    public void onBroadcastSent(int count) {
        broadcastsSent.addAndGet(count);
    }

    public void onBroadcastFailure() {
        broadcastFailures.incrementAndGet();
        markFailure("broadcast-failure");
    }

    private void markFailure(String kind) {
        lastFailureAt.computeIfAbsent(kind, k -> new AtomicLong()).set(System.currentTimeMillis());
    }

    /**
     * 管理端点读取的只读快照。
     *
     * @param ringBufferRemaining 队列剩余容量（由调用方提供，-1 表示不可用）
     */
    public Map<String, Object> snapshot(long ringBufferRemaining) {
        Map<String, Object> view = new ConcurrentHashMap<>();
        view.put("received", received.get());
        view.put("queueRemaining", ringBufferRemaining);
        view.put("rejectedByBackpressure", rejectedByBackpressure.get());
        view.put("invalidMessages", invalidMessages.get());
        view.put("processingFailures", processingFailures.get());
        view.put("authRejections", authRejections.get());
        view.put("leaseConflicts", leaseConflicts.get());
        view.put("broadcastsSent", broadcastsSent.get());
        view.put("broadcastFailures", broadcastFailures.get());
        lastFailureAt.forEach((k, v) -> view.put("lastFailureAt." + k, v.get()));
        return view;
    }
}
