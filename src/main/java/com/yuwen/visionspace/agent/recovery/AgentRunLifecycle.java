package com.yuwen.visionspace.agent.recovery;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 运行生命周期的唯一写入者。
 *
 * <p>「一个运行可以进入哪些状态」以及每种状态的固定措辞只在这里出现。
 * 执行入口（对话、流式、反馈、兼容调用、续跑）报告结果，不再各自拼状态与文案，
 * 所以暂停原因、待确认摘要和失败状态不会在入口之间漂移。
 *
 * <p>恢复认领（版本、租约、守卫）由 {@link AgentRecoveryService} 独占；
 * 本模块只负责状态转移，不判断「能不能恢复」。
 */
@Service
public class AgentRunLifecycle {

    /** 结果未知的外部动作统一文案前缀：界面据此区分暂停原因。 */
    public static final String UNKNOWN_ACTION_PREFIX = "外部动作结果未知，等待核对：";

    private final AgentRunStateStore runStateStore;

    public AgentRunLifecycle(AgentRunStateStore runStateStore) {
        this.runStateStore = runStateStore;
    }

    /** 一次执行开始：状态推进为 RUNNING。 */
    public void started(String threadId, Long userId) {
        runStateStore.updateStatus(threadId, userId, RunStatus.RUNNING, null);
    }

    /** 一次执行明确失败：可安全重试。 */
    public void failed(String threadId, Long userId) {
        runStateStore.updateStatus(threadId, userId, RunStatus.FAILED, null);
    }

    /**
     * 外部动作结果未知：暂停自动执行，等待人工核对。
     *
     * <p>不能折成普通失败或「稍后重试」，否则用户会以为可以直接再跑一次。
     */
    public void pausedForUnknownAction(String threadId, Long userId, String toolName) {
        runStateStore.updateStatus(threadId, userId, RunStatus.PAUSED_UNRECOVERABLE,
                UNKNOWN_ACTION_PREFIX + toolName);
    }

    /**
     * 其他不可恢复的暂停（缺少恢复点、工具环境无法确认等）。
     *
     * @param reason 面向用户的暂停原因
     */
    public void paused(String threadId, Long userId, String reason) {
        runStateStore.updateStatus(threadId, userId, RunStatus.PAUSED_UNRECOVERABLE, reason);
    }

    /**
     * 停在人工确认点。
     *
     * <p>只保存工具名：确认信息要能被下一次执行读到，让界面区分「停在待确认」
     * 与「已经确认过」；描述里通常带着生成参数或提示词，属于私人正文，不写进状态列。
     */
    public void awaitingConfirmation(String threadId, Long userId, List<String> toolNames) {
        String summary = toolNames == null ? "" : toolNames.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining("\n"));
        runStateStore.updateStatus(threadId, userId, RunStatus.AWAITING_CONFIRMATION, summary);
    }
}
