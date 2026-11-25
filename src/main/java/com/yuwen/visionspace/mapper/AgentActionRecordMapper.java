package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.AgentActionRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

@Mapper
public interface AgentActionRecordMapper extends BaseMapper<AgentActionRecord> {

    @Select("SELECT * FROM agent_action_record " +
            "WHERE thread_id = #{threadId} AND user_id = #{userId} ORDER BY id ASC")
    List<AgentActionRecord> selectByThread(@Param("threadId") String threadId,
                                           @Param("userId") Long userId);

    /**
     * 原子认领一个动作。
     *
     * <p>这是「同一动作不能被执行两次」的实现点：只有<b>尚不存在</b>或<b>已明确失败</b>
     * 的动作能被认领，其余状态（执行中、待核对、已完成、已放弃）一律影响 0 行。
     * 判定与写入是同一条语句，没有先查后写的窗口，也不依赖任何时间宽限。
     *
     * <p>认领成功时写入新的 {@code claimToken}；后续推进状态必须带同一个 token，
     * 这样即使调用方之间的状态判定发生竞态，也只有认领者能落结果。
     *
     * <p><b>返回值不能用来判断是否抢到</b>：PostgreSQL 的 {@code ON CONFLICT DO UPDATE}
     * 在插入与更新时都返回 1，命中但未改变值也不可靠。认领结果以读回的
     * {@code claimToken} 为准（见 {@code AgentActionLedger#claim}）。
     *
     * @return 受影响行数，仅供诊断
     */
    // PostgreSQL：ON CONFLICT 的所有 SET 右侧都看到**旧行**（不是 MySQL 的从左到右），
    // 因此每条表达式都显式引用 agent_action_record.status 的旧值；原 MySQL 里
    // “status 必须最后写”的约束在 PG 下不再需要，语义保持“只有 FAILED 可重新认领”。
    @Insert("INSERT INTO agent_action_record " +
            "(thread_id, user_id, action_id, tool_name, arguments_digest, status, idempotency, claim_token, " +
            "attempt, create_time, update_time) " +
            "VALUES (#{threadId}, #{userId}, #{actionId}, #{toolName}, #{argumentsDigest}, " +
            "'EXECUTING', #{idempotency}, #{claimToken}, 1, statement_timestamp(), statement_timestamp()) " +
            // 冲突目标必须与唯一索引 uq_agent_action_record_thread_action (thread_id, action_id)
            // 完全一致：action_id 由 threadId + toolCallId 派生，user_id 在索引里是冗余列。
            "ON CONFLICT (thread_id, action_id) DO UPDATE SET " +
            "claim_token = CASE WHEN agent_action_record.status = 'FAILED' " +
            "THEN EXCLUDED.claim_token ELSE agent_action_record.claim_token END, " +
            "attempt = CASE WHEN agent_action_record.status = 'FAILED' " +
            "THEN agent_action_record.attempt + 1 ELSE agent_action_record.attempt END, " +
            "update_time = CASE WHEN agent_action_record.status = 'FAILED' " +
            "THEN statement_timestamp() ELSE agent_action_record.update_time END, " +
            "status = CASE WHEN agent_action_record.status = 'FAILED' " +
            "THEN 'EXECUTING' ELSE agent_action_record.status END")
    int claim(@Param("threadId") String threadId,
              @Param("userId") Long userId,
              @Param("actionId") String actionId,
              @Param("toolName") String toolName,
              @Param("argumentsDigest") String argumentsDigest,
              @Param("idempotency") String idempotency,
              @Param("claimToken") String claimToken);

    /**
     * 读回动作当前状态与认领凭据。
     *
     * <p>认领结果只能靠它判定：{@code ON CONFLICT DO UPDATE} 的影响行数在
     * PostgreSQL 下无法区分「没抢到」与「抢到了」。
     */
    @Select("SELECT * FROM agent_action_record " +
            "WHERE thread_id = #{threadId} AND user_id = #{userId} AND action_id = #{actionId}")
    AgentActionRecord selectForClaim(@Param("threadId") String threadId,
                                     @Param("userId") Long userId,
                                     @Param("actionId") String actionId);

    /**
     * 把遗留的 {@code EXECUTING} 动作一次性收敛掉。
     *
     * <p>判定与改写是同一条语句：只有「仍在执行中」且「最后更新早于 staleBefore」的行会被改，
     * 另一个实例刚认领的行不受影响。可证明幂等的退回 {@code FAILED} 走安全重试，
     * 其余进入 {@code UNKNOWN_NEEDS_REVIEW} 等待核对——它们可能已经产生了外部副作用。
     *
     * <p>不依赖 {@code claimToken}：中断的进程带不走自己的 token，而这里要收敛的正是
     * 那些没人再推进的行。
     *
     * @return 被收敛的行数
     */
    @Update("UPDATE agent_action_record SET " +
            "status = CASE WHEN idempotency = 'PROVEN' THEN 'FAILED' ELSE 'UNKNOWN_NEEDS_REVIEW' END, " +
            "result_summary = #{resultSummary}, attempt = attempt + 1, update_time = statement_timestamp() " +
            "WHERE thread_id = #{threadId} AND user_id = #{userId} " +
            "AND status = 'EXECUTING' AND update_time <= #{staleBefore}")
    int reconcileStaleExecuting(@Param("threadId") String threadId,
                                @Param("userId") Long userId,
                                @Param("staleBefore") Date staleBefore,
                                @Param("resultSummary") String resultSummary);

    /**
     * 由认领者落结果：只有持有当前 {@code claimToken} 的调用方能推进状态。
     *
     * <p>没有这一层时，一个慢调用方可能在动作已被别人收敛之后再把它写成
     * {@code SUCCEEDED}，把「结果未知」洗成「已完成」。
     */
    @Update("UPDATE agent_action_record SET status = #{status}, " +
            "result_summary = #{resultSummary}, attempt = attempt + 1, update_time = statement_timestamp() " +
            "WHERE thread_id = #{threadId} AND user_id = #{userId} AND action_id = #{actionId} " +
            "AND claim_token = #{claimToken} AND status = 'EXECUTING'")
    int settleClaimed(@Param("threadId") String threadId,
                      @Param("userId") Long userId,
                      @Param("actionId") String actionId,
                      @Param("claimToken") String claimToken,
                      @Param("status") String status,
                      @Param("resultSummary") String resultSummary);

    /**
     * 人工核对后标记为已确认完成。只能从待核对或失败态进入。
     */
    @Update("UPDATE agent_action_record SET status = 'SUCCEEDED', " +
            "result_summary = #{resultSummary}, update_time = statement_timestamp() " +
            "WHERE thread_id = #{threadId} AND user_id = #{userId} AND action_id = #{actionId} " +
            "AND status IN ('UNKNOWN_NEEDS_REVIEW', 'FAILED')")
    int confirmCompleted(@Param("threadId") String threadId,
                         @Param("userId") Long userId,
                         @Param("actionId") String actionId,
                         @Param("resultSummary") String resultSummary);

    /**
     * 人工核对后放弃该动作：确认外部没有发生，或接受不再执行。
     *
     * <p>落到 {@code DISMISSED} 而不是 {@code FAILED}：{@code FAILED} 可以被重新认领，
     * 而用户明确核对过的动作不该因为一次重试又被悄悄执行。
     */
    @Update("UPDATE agent_action_record SET status = 'DISMISSED', " +
            "result_summary = #{resultSummary}, update_time = statement_timestamp() " +
            "WHERE thread_id = #{threadId} AND user_id = #{userId} AND action_id = #{actionId} " +
            "AND status = 'UNKNOWN_NEEDS_REVIEW'")
    int dismissUnknown(@Param("threadId") String threadId,
                       @Param("userId") Long userId,
                       @Param("actionId") String actionId,
                       @Param("resultSummary") String resultSummary);

    @Select("SELECT COUNT(*) FROM agent_action_record " +
            "WHERE thread_id = #{threadId} AND user_id = #{userId} AND status = 'UNKNOWN_NEEDS_REVIEW'")
    int countPendingReview(@Param("threadId") String threadId, @Param("userId") Long userId);
}
