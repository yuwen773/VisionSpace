package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.AgentSession;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AgentSessionMapper extends BaseMapper<AgentSession> {

    @Select("SELECT * FROM agent_session WHERE sessionId = #{sessionId} AND isDelete = 0")
    AgentSession selectBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT * FROM agent_session " +
            "WHERE userId = #{userId} AND isDelete = 0 " +
            "AND (#{beforeId} IS NULL OR id < #{beforeId}) " +
            "ORDER BY updatedTime DESC LIMIT #{limit}")
    java.util.List<AgentSession> selectByUserIdCursor(
            @Param("userId") Long userId,
            @Param("beforeId") Long beforeId,
            @Param("limit") int limit);

    @Update("UPDATE agent_session SET messageCount = messageCount + 1, " +
            "lastMessage = #{lastMessage}, updatedTime = NOW() " +
            "WHERE sessionId = #{sessionId}")
    int updateMessageCount(@Param("sessionId") String sessionId,
                          @Param("lastMessage") String lastMessage);

    @Update("UPDATE agent_session SET checkpointData = #{checkpointData} " +
            "WHERE sessionId = #{sessionId}")
    int updateCheckpointData(@Param("sessionId") String sessionId,
                            @Param("checkpointData") byte[] checkpointData);

    @Update("UPDATE agent_session SET checkpointData = NULL " +
            "WHERE sessionId = #{sessionId}")
    int clearCheckpointData(@Param("sessionId") String sessionId);
}
