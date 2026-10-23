package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.AgentActionRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AgentActionRecordMapper extends BaseMapper<AgentActionRecord> {

    @Select("SELECT * FROM agent_action_record " +
            "WHERE threadId = #{threadId} AND userId = #{userId} ORDER BY id ASC")
    List<AgentActionRecord> selectByThread(@Param("threadId") String threadId,
                                           @Param("userId") Long userId);

    @Select("SELECT * FROM agent_action_record " +
            "WHERE threadId = #{threadId} AND userId = #{userId} AND actionId = #{actionId}")
    AgentActionRecord selectOne(@Param("threadId") String threadId,
                                @Param("userId") Long userId,
                                @Param("actionId") String actionId);

    /**
     * 登记一次动作开始。同一动作重复登记是空操作，不会把已确认完成的动作打回执行中。
     */
    @Insert("INSERT INTO agent_action_record " +
            "(threadId, userId, actionId, toolName, argumentsDigest, status, idempotency, attempt, " +
            "createTime, updateTime) " +
            "VALUES (#{threadId}, #{userId}, #{actionId}, #{toolName}, #{argumentsDigest}, " +
            "#{status}, #{idempotency}, 1, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE threadId = threadId")
    int insertIfAbsent(@Param("threadId") String threadId,
                       @Param("userId") Long userId,
                       @Param("actionId") String actionId,
                       @Param("toolName") String toolName,
                       @Param("argumentsDigest") String argumentsDigest,
                       @Param("status") String status,
                       @Param("idempotency") String idempotency);

    /**
     * 收敛动作结果。只允许从「未完成」状态推进，已确认完成不会被后续写入改写。
     *
     * @return 1 表示已更新，0 表示动作已是终态或不属于该用户
     */
    @Update("UPDATE agent_action_record SET status = #{status}, " +
            "resultSummary = #{resultSummary}, attempt = attempt + 1, updateTime = NOW() " +
            "WHERE threadId = #{threadId} AND userId = #{userId} AND actionId = #{actionId} " +
            "AND status IN ('EXECUTING', 'UNKNOWN_NEEDS_REVIEW', 'FAILED')")
    int settle(@Param("threadId") String threadId,
               @Param("userId") Long userId,
               @Param("actionId") String actionId,
               @Param("status") String status,
               @Param("resultSummary") String resultSummary);

    /**
     * 人工核对后标记为已确认完成。只能从待核对或失败态进入。
     */
    @Update("UPDATE agent_action_record SET status = 'SUCCEEDED', " +
            "resultSummary = #{resultSummary}, updateTime = NOW() " +
            "WHERE threadId = #{threadId} AND userId = #{userId} AND actionId = #{actionId} " +
            "AND status IN ('UNKNOWN_NEEDS_REVIEW', 'FAILED')")
    int confirmCompleted(@Param("threadId") String threadId,
                         @Param("userId") Long userId,
                         @Param("actionId") String actionId,
                         @Param("resultSummary") String resultSummary);

    /**
     * 人工核对后放弃该动作（视为未发生或接受不再执行）。
     */
    @Update("UPDATE agent_action_record SET status = 'FAILED', " +
            "resultSummary = #{resultSummary}, updateTime = NOW() " +
            "WHERE threadId = #{threadId} AND userId = #{userId} AND actionId = #{actionId} " +
            "AND status IN ('UNKNOWN_NEEDS_REVIEW', 'FAILED')")
    int dismissUnknown(@Param("threadId") String threadId,
                       @Param("userId") Long userId,
                       @Param("actionId") String actionId,
                       @Param("resultSummary") String resultSummary);

    @Select("SELECT COUNT(*) FROM agent_action_record " +
            "WHERE threadId = #{threadId} AND userId = #{userId} AND status = 'UNKNOWN_NEEDS_REVIEW'")
    int countPendingReview(@Param("threadId") String threadId, @Param("userId") Long userId);
}
