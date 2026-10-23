package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.AgentRunState;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AgentRunStateMapper extends BaseMapper<AgentRunState> {

    /**
     * 按会话读取执行状态。归属条件写进查询，读到别人的状态在这里就不可能发生。
     */
    @Select("SELECT * FROM agent_run_state WHERE threadId = #{threadId} AND userId = #{userId}")
    AgentRunState selectOwned(@Param("threadId") String threadId, @Param("userId") Long userId);

    /**
     * 读取当前版本号，用于让本实例的版本顺序与共享持久状态对齐。
     *
     * <p>没有行时返回 null：调用方按「从 1 开始」处理。
     */
    @Select("SELECT stateVersion FROM agent_run_state WHERE threadId = #{threadId} AND userId = #{userId}")
    Long selectVersion(@Param("threadId") String threadId, @Param("userId") Long userId);

    /**
     * 首次创建执行状态行。已存在时是空操作，绝不改写归属。
     *
     * @return 1 表示本次创建，0 表示已存在
     */
    @Insert("INSERT INTO agent_run_state " +
            "(threadId, userId, stateVersion, stateFormat, runStatus, legacyArchiveOnly, backupAttempts, " +
            "createTime, updateTime) " +
            "VALUES (#{threadId}, #{userId}, 0, #{stateFormat}, #{runStatus}, 0, 0, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE threadId = threadId")
    int insertIfAbsent(@Param("threadId") String threadId,
                       @Param("userId") Long userId,
                       @Param("stateFormat") String stateFormat,
                       @Param("runStatus") String runStatus);

    /**
     * 按版本 CAS 写入完整状态：只有目标版本严格大于当前版本、且执行世代与当前
     * 一致时才生效。
     *
     * <p>这是「旧异步备份晚到不能覆盖新版本」的实现点：迟到的备份带着旧
     * {@code stateVersion}，条件不成立，影响 0 行。
     *
     * <p>世代是比版本更强的约束：版本可以被对齐重试刷新，世代不能——它标识
     * 「这次写入属于哪个执行」。一个已被新一轮执行取代的旧执行，无论把版本
     * 刷到多高，世代对不上就写不进去（见 {@link #bumpRunEpoch}）。
     * {@code runEpoch} 为 null 表示调用方不持有世代（旧调用方/种子数据），
     * 此时只按版本判断。
     *
     * <p>版本号必须由调用方按共享持久状态推算（见 {@code AgentRunStateStore#currentVersion}），
     * 不能只用进程本地计数器：否则启动更早的实例接管会话后每次写入都会被拒绝。
     *
     * <p>归属条件同样在 SQL 里，跨用户写入影响 0 行。
     *
     * <p>{@code mcpServerCodes} 与载荷在同一条 UPDATE 里落库：为 null 表示
     * 本次执行没有声明工具环境（例如续跑沿用已记录的环境），列保持不变——
     * 环境与恢复点因此永远是同一版本的配对，不会出现旧载荷配新环境。
     *
     * @return 1 表示已写入，0 表示版本不够新、世代已更替或不属于该用户
     */
    @Update("UPDATE agent_run_state SET stateVersion = #{stateVersion}, stateFormat = #{stateFormat}, " +
            "checkpointId = #{checkpointId}, nodeId = #{nodeId}, nextNodeId = #{nextNodeId}, " +
            "statePayload = #{statePayload}, runStatus = #{runStatus}, " +
            "pendingConfirmation = #{pendingConfirmation}, legacyArchiveOnly = 0, " +
            "mcpServerCodes = COALESCE(#{mcpServerCodes}, mcpServerCodes), " +
            "backupError = NULL, backupAttempts = 0, updateTime = NOW() " +
            "WHERE threadId = #{threadId} AND userId = #{userId} AND stateVersion < #{stateVersion} " +
            "AND (#{runEpoch} IS NULL OR runEpoch = #{runEpoch})")
    int updateIfNewer(@Param("threadId") String threadId,
                      @Param("userId") Long userId,
                      @Param("stateVersion") long stateVersion,
                      @Param("stateFormat") String stateFormat,
                      @Param("checkpointId") String checkpointId,
                      @Param("nodeId") String nodeId,
                      @Param("nextNodeId") String nextNodeId,
                      @Param("statePayload") byte[] statePayload,
                      @Param("runStatus") String runStatus,
                      @Param("pendingConfirmation") String pendingConfirmation,
                      @Param("runEpoch") Long runEpoch,
                      @Param("mcpServerCodes") String mcpServerCodes);

    /**
     * 读取当前执行世代。
     *
     * @return 当前世代；没有状态行时返回 null
     */
    @Select("SELECT runEpoch FROM agent_run_state WHERE threadId = #{threadId} AND userId = #{userId}")
    Long selectRunEpoch(@Param("threadId") String threadId, @Param("userId") Long userId);

    /**
     * 原子推进执行世代：只有世代仍等于调用方读到的那一刻才生效。
     *
     * <p>世代递增即所有权更替：从这一刻起，只有拿到新世代的执行能写完整状态，
     * 上一轮执行的迟到写入（哪怕重新编号到更高版本）全部被拒。
     *
     * @return 1 表示本次推进成功，0 表示世代已被别的执行推进，需重读后重试
     */
    @Update("UPDATE agent_run_state SET runEpoch = runEpoch + 1, updateTime = NOW() " +
            "WHERE threadId = #{threadId} AND userId = #{userId} AND runEpoch = #{expectedEpoch}")
    int bumpRunEpoch(@Param("threadId") String threadId,
                     @Param("userId") Long userId,
                     @Param("expectedEpoch") long expectedEpoch);

    /**
     * 认领一次续跑：推进版本号与执行世代、置为运行中，并持有一份租约。
     *
     * <p>只按版本号是不够的：第一个请求推进版本后，第二个请求只要刷新一下就能拿到
     * 新版本再次认领，于是同一次续跑被执行两次。这里额外要求「没有别的请求持有
     * 有效租约」，两条并发请求只有一个能命中。
     *
     * <p>世代同时递增：续跑是一次新执行，接管后旧执行（以及任何它遗留的迟到写入）
     * 从这一刻起不再能写完整状态。
     *
     * <p>租约到期只是让崩溃遗留的租约不再永久阻塞续跑，<b>不是</b>判断动作是否安全的
     * 依据：动作能否重放由 {@code agent_action_record} 的认领保证。
     *
     * @return 1 表示本次认领成功，0 表示版本已推进或已有请求在执行
     */
    @Update("UPDATE agent_run_state SET stateVersion = stateVersion + 1, runEpoch = runEpoch + 1, " +
            "runStatus = #{runStatus}, " +
            "resumeLease = #{leaseToken}, " +
            "resumeLeaseUntil = DATE_ADD(NOW(), INTERVAL #{leaseSeconds} SECOND), " +
            "updateTime = NOW() " +
            "WHERE threadId = #{threadId} AND userId = #{userId} " +
            "AND stateVersion = #{expectedVersion} " +
            "AND (resumeLease IS NULL OR resumeLeaseUntil IS NULL OR resumeLeaseUntil <= NOW())")
    int claimForResume(@Param("threadId") String threadId,
                       @Param("userId") Long userId,
                       @Param("expectedVersion") long expectedVersion,
                       @Param("runStatus") String runStatus,
                       @Param("leaseToken") String leaseToken,
                       @Param("leaseSeconds") int leaseSeconds);

    /**
     * 释放续跑租约。只释放自己持有的那一份，不会解掉别人的。
     */
    @Update("UPDATE agent_run_state SET resumeLease = NULL, resumeLeaseUntil = NULL, updateTime = NOW() " +
            "WHERE threadId = #{threadId} AND userId = #{userId} AND resumeLease = #{leaseToken}")
    int releaseResumeLease(@Param("threadId") String threadId,
                           @Param("userId") Long userId,
                           @Param("leaseToken") String leaseToken);

    /**
     * 只更新运行状态与待确认信息，不动版本与载荷。
     */
    @Update("UPDATE agent_run_state SET runStatus = #{runStatus}, " +
            "pendingConfirmation = #{pendingConfirmation}, updateTime = NOW() " +
            "WHERE threadId = #{threadId} AND userId = #{userId}")
    int updateStatus(@Param("threadId") String threadId,
                     @Param("userId") Long userId,
                     @Param("runStatus") String runStatus,
                     @Param("pendingConfirmation") String pendingConfirmation);

    /**
     * 记录一次备份失败：失败必须可见（backupError）且可重试（backupAttempts）。
     * 失败原因只保存异常类型与简短消息，调用方负责先脱敏。
     */
    @Update("UPDATE agent_run_state SET backupError = #{backupError}, " +
            "backupAttempts = backupAttempts + 1, updateTime = NOW() " +
            "WHERE threadId = #{threadId} AND userId = #{userId}")
    int recordBackupFailure(@Param("threadId") String threadId,
                            @Param("userId") Long userId,
                            @Param("backupError") String backupError);

    /**
     * 把状态标记为「仅有旧轻量归档」：可查看历史，但不能当作完整恢复点。
     */
    @Update("UPDATE agent_run_state SET legacyArchiveOnly = 1, runStatus = #{runStatus}, " +
            "statePayload = NULL, checkpointId = NULL, nextNodeId = NULL, updateTime = NOW() " +
            "WHERE threadId = #{threadId} AND userId = #{userId}")
    int markLegacyArchiveOnly(@Param("threadId") String threadId,
                              @Param("userId") Long userId,
                              @Param("runStatus") String runStatus);

    /**
     * 释放会话时清理状态行。运行计数（步骤计数等进程内资源）与状态行一起释放，
     * 避免无界增长；不属于该用户时影响 0 行。
     */
    @Update("UPDATE agent_run_state SET runStatus = #{runStatus}, statePayload = NULL, " +
            "checkpointId = NULL, nextNodeId = NULL, pendingConfirmation = NULL, updateTime = NOW() " +
            "WHERE threadId = #{threadId} AND userId = #{userId}")
    int clearForRelease(@Param("threadId") String threadId,
                        @Param("userId") Long userId,
                        @Param("runStatus") String runStatus);
}
