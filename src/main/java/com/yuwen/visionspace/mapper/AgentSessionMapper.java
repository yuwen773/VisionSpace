package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.AgentSession;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AgentSessionMapper extends BaseMapper<AgentSession> {

    @Select("SELECT * FROM agent_session WHERE session_id = #{sessionId} AND is_delete = 0")
    AgentSession selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 首次创建会话时原子绑定所属用户。
     * <p>
     * 唯一索引在 {@code session_id} 上，{@code ON CONFLICT (session_id) DO NOTHING}
     * 对已存在的行是空操作：既不会改写已有会话的归属，也不会把逻辑删除的行复活。
     * 调用方必须随后回读并核对 userId，不能把「插入成功」当作归属成立的证据。
     */
    @Insert("INSERT INTO agent_session (session_id, user_id, title, status, message_count, last_message, " +
            "created_time, updated_time, is_delete) " +
            "VALUES (#{sessionId}, #{userId}, #{title}, 1, 0, '', " +
            "statement_timestamp(), statement_timestamp(), 0) " +
            "ON CONFLICT (session_id) DO NOTHING")
    int insertIfAbsent(@Param("sessionId") String sessionId,
                       @Param("userId") Long userId,
                       @Param("title") String title);

    /**
     * 按游标取用户的会话列表（最近活跃在前）。
     * <p>
     * 排序键与游标键必须同源，否则 {@code id < beforeId} 会与 updatedTime 排序打架：
     * id 大但 updatedTime 旧的会话会被永久跳过。这里统一用 {@code (updatedTime, id)}
     * 复合键排序与比较。
     * <p>
     * 注意：updatedTime 会被新消息改写，分页期间若有会话收到新消息，它会上移到首页，
     * 可能被重复返回。会话列表按「最近活跃」排序本身就有这个特性，接受该抖动。
     */
    @Select("SELECT * FROM agent_session " +
            "WHERE user_id = #{userId} AND is_delete = 0 " +
            // 首页 beforeId 为 null：必须显式声明 bigint，否则 PostgreSQL 无法推断
            // 参数类型（could not determine data type of parameter）。
            "AND (#{beforeId,jdbcType=BIGINT} IS NULL OR (updated_time, id) < " +
            "(SELECT updated_time, id FROM agent_session " +
            "WHERE id = #{beforeId} AND user_id = #{userId} AND is_delete = 0)) " +
            "ORDER BY updated_time DESC, id DESC LIMIT #{limit}")
    java.util.List<AgentSession> selectByUserIdCursor(
            @Param("userId") Long userId,
            @Param("beforeId") Long beforeId,
            @Param("limit") int limit);

    @Update("UPDATE agent_session SET message_count = message_count + 1, " +
            "last_message = #{lastMessage}, updated_time = statement_timestamp() " +
            "WHERE session_id = #{sessionId} AND user_id = #{userId} AND is_delete = 0")
    int updateMessageCount(@Param("sessionId") String sessionId,
                          @Param("userId") Long userId,
                          @Param("lastMessage") String lastMessage);

    @Update("UPDATE agent_session SET checkpoint_data = #{checkpointData} " +
            "WHERE session_id = #{sessionId} AND user_id = #{userId} AND is_delete = 0")
    int updateCheckpointData(@Param("sessionId") String sessionId,
                            @Param("userId") Long userId,
                            @Param("checkpointData") byte[] checkpointData);

    @Update("UPDATE agent_session SET summary_content = #{summaryContent}, " +
            "summary_at = statement_timestamp(), last_message = #{lastMessage} " +
            "WHERE session_id = #{sessionId} AND user_id = #{userId} AND is_delete = 0")
    int updateSummaryOwned(@Param("sessionId") String sessionId,
                           @Param("userId") Long userId,
                           @Param("summaryContent") String summaryContent,
                           @Param("lastMessage") String lastMessage);

    /**
     * 逻辑删除会话。归属条件写进同一条 UPDATE：会话不存在或不属于该用户时
     * 不会产生任何行，调用方据此判定「删除成功」。
     */
    @Update("UPDATE agent_session SET is_delete = 1, updated_time = statement_timestamp() " +
            "WHERE session_id = #{sessionId} AND user_id = #{userId} AND is_delete = 0")
    int markDeleted(@Param("sessionId") String sessionId, @Param("userId") Long userId);
}
