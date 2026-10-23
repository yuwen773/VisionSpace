package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.AgentSession;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AgentSessionMapper extends BaseMapper<AgentSession> {

    @Select("SELECT * FROM agent_session WHERE sessionId = #{sessionId} AND isDelete = 0")
    AgentSession selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 首次创建会话时原子绑定所属用户。
     * <p>
     * 唯一索引在 {@code sessionId} 上，{@code ON DUPLICATE KEY UPDATE sessionId = sessionId}
     * 对已存在的行是空操作：既不会改写已有会话的归属，也不会把逻辑删除的行复活。
     * 调用方必须随后回读并核对 userId，不能把「插入成功」当作归属成立的证据。
     */
    @Insert("INSERT INTO agent_session (sessionId, userId, title, status, messageCount, lastMessage, " +
            "createdTime, updatedTime, isDelete) " +
            "VALUES (#{sessionId}, #{userId}, #{title}, 1, 0, '', NOW(), NOW(), 0) " +
            "ON DUPLICATE KEY UPDATE sessionId = sessionId")
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
            "WHERE userId = #{userId} AND isDelete = 0 " +
            "AND (#{beforeId} IS NULL OR (updatedTime, id) < " +
            "(SELECT updatedTime, id FROM agent_session " +
            "WHERE id = #{beforeId} AND userId = #{userId} AND isDelete = 0)) " +
            "ORDER BY updatedTime DESC, id DESC LIMIT #{limit}")
    java.util.List<AgentSession> selectByUserIdCursor(
            @Param("userId") Long userId,
            @Param("beforeId") Long beforeId,
            @Param("limit") int limit);

    @Update("UPDATE agent_session SET messageCount = messageCount + 1, " +
            "lastMessage = #{lastMessage}, updatedTime = NOW() " +
            "WHERE sessionId = #{sessionId} AND userId = #{userId} AND isDelete = 0")
    int updateMessageCount(@Param("sessionId") String sessionId,
                          @Param("userId") Long userId,
                          @Param("lastMessage") String lastMessage);

    @Update("UPDATE agent_session SET checkpointData = #{checkpointData} " +
            "WHERE sessionId = #{sessionId} AND userId = #{userId} AND isDelete = 0")
    int updateCheckpointData(@Param("sessionId") String sessionId,
                            @Param("userId") Long userId,
                            @Param("checkpointData") byte[] checkpointData);

    @Update("UPDATE agent_session SET summaryContent = #{summaryContent}, summaryAt = NOW(), " +
            "lastMessage = #{lastMessage} " +
            "WHERE sessionId = #{sessionId} AND userId = #{userId} AND isDelete = 0")
    int updateSummaryOwned(@Param("sessionId") String sessionId,
                           @Param("userId") Long userId,
                           @Param("summaryContent") String summaryContent,
                           @Param("lastMessage") String lastMessage);
}
