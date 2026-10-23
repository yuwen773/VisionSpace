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
     * 按版本 CAS 写入完整状态：只有目标版本严格大于当前版本时才生效。
     *
     * <p>这是「旧异步备份晚到不能覆盖新版本」的实现点：迟到的备份带着旧
     * {@code stateVersion}，条件不成立，影响 0 行。
     *
     * <p>归属条件同样在 SQL 里，跨用户写入影响 0 行。
     *
     * @return 1 表示已写入，0 表示版本不够新或不属于该用户
     */
    @Update("UPDATE agent_run_state SET stateVersion = #{stateVersion}, stateFormat = #{stateFormat}, " +
            "checkpointId = #{checkpointId}, nodeId = #{nodeId}, nextNodeId = #{nextNodeId}, " +
            "statePayload = #{statePayload}, runStatus = #{runStatus}, " +
            "pendingConfirmation = #{pendingConfirmation}, legacyArchiveOnly = 0, " +
            "backupError = NULL, backupAttempts = 0, updateTime = NOW() " +
            "WHERE threadId = #{threadId} AND userId = #{userId} AND stateVersion < #{stateVersion}")
    int updateIfNewer(@Param("threadId") String threadId,
                      @Param("userId") Long userId,
                      @Param("stateVersion") long stateVersion,
                      @Param("stateFormat") String stateFormat,
                      @Param("checkpointId") String checkpointId,
                      @Param("nodeId") String nodeId,
                      @Param("nextNodeId") String nextNodeId,
                      @Param("statePayload") byte[] statePayload,
                      @Param("runStatus") String runStatus,
                      @Param("pendingConfirmation") String pendingConfirmation);

    /**
     * 认领一次续跑：按期望版本推进版本号并置为运行中。
     *
     * <p>这是「重复或并发续跑请求不能产生第二次执行」的实现点。两个请求带同一个
     * {@code expectedVersion} 时，只有一个能命中条件；另一个影响 0 行，被拒绝。
     * 版本推进后，旧请求自然过期，不需要额外的分布式锁。
     *
     * @return 1 表示本次认领成功，0 表示版本已推进（重复/过期/并发落败）
     */
    @Update("UPDATE agent_run_state SET stateVersion = stateVersion + 1, runStatus = #{runStatus}, " +
            "updateTime = NOW() " +
            "WHERE threadId = #{threadId} AND userId = #{userId} AND stateVersion = #{expectedVersion}")
    int claimForResume(@Param("threadId") String threadId,
                       @Param("userId") Long userId,
                       @Param("expectedVersion") long expectedVersion,
                       @Param("runStatus") String runStatus);

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
