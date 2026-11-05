package com.yuwen.visionspace.model.vo;

import lombok.Data;

import java.util.Map;

/**
 * 协作通道健康快照（脱敏：只含计数与时间戳，不含用户、图片或消息内容）。
 */
@Data
public class CollabMonitorVO {

    /** 已接收的协作消息总数 */
    private long received;

    /** 队列剩余容量（有界背压的实时余量，-1 表示不可用） */
    private long queueRemaining;

    /** 因队列满被拒绝的消息数 */
    private long rejectedByBackpressure;

    /** 非法/未知类型/损坏消息数 */
    private long invalidMessages;

    /** 单次处理异常数（已隔离，不影响后续消息） */
    private long processingFailures;

    /** 会话失效或权限不足被拒绝的写入数 */
    private long authRejections;

    /** 租约竞争失败数 */
    private long leaseConflicts;

    /** 广播送达计数 */
    private long broadcastsSent;

    /** 广播失败计数 */
    private long broadcastFailures;

    private long broadcastRejections;
    private long broadcastQueueRemaining;
    private long activeLocalLeases;

    /** 各类失败最后一次发生的时间戳 */
    private Map<String, Long> lastFailureAt;

    public static CollabMonitorVO from(Map<String, Object> snapshot) {
        CollabMonitorVO vo = new CollabMonitorVO();
        vo.setReceived(((Number) snapshot.getOrDefault("received", 0L)).longValue());
        vo.setQueueRemaining(((Number) snapshot.getOrDefault("queueRemaining", -1L)).longValue());
        vo.setRejectedByBackpressure(((Number) snapshot.getOrDefault("rejectedByBackpressure", 0L)).longValue());
        vo.setInvalidMessages(((Number) snapshot.getOrDefault("invalidMessages", 0L)).longValue());
        vo.setProcessingFailures(((Number) snapshot.getOrDefault("processingFailures", 0L)).longValue());
        vo.setAuthRejections(((Number) snapshot.getOrDefault("authRejections", 0L)).longValue());
        vo.setLeaseConflicts(((Number) snapshot.getOrDefault("leaseConflicts", 0L)).longValue());
        vo.setBroadcastsSent(((Number) snapshot.getOrDefault("broadcastsSent", 0L)).longValue());
        vo.setBroadcastFailures(((Number) snapshot.getOrDefault("broadcastFailures", 0L)).longValue());
        vo.setBroadcastRejections(((Number) snapshot.getOrDefault("broadcastRejections", 0L)).longValue());
        var lastFailureAt = new java.util.HashMap<String, Long>();
        snapshot.forEach((k, v) -> {
            if (k.startsWith("lastFailureAt.") && v instanceof Number number) {
                lastFailureAt.put(k.substring("lastFailureAt.".length()), number.longValue());
            }
        });
        vo.setLastFailureAt(lastFailureAt);
        return vo;
    }
}
