package com.yuwen.visionspace.utils;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务提交后再执行的动作。
 *
 * <p>索引、清理这类副作用发生在数据库事务之外：事务回滚时它们不该已经生效。
 * 没有事务时立即执行，行为与调用方一致。
 */
public final class AfterCommitExecutor {

    private AfterCommitExecutor() {
    }

    /**
     * 在当前事务提交后执行给定动作；当前没有事务时立即执行。
     *
     * @param action 要执行的动作
     */
    public static void run(Runnable action) {
        if (action == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
