package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.AgentMessage;
import org.apache.ibatis.annotations.*;

import java.util.Date;

@Mapper
public interface AgentMessageMapper extends BaseMapper<AgentMessage> {

    @Select("<script>" +
            "SELECT * FROM agent_message " +
            "WHERE sessionId = #{sessionId} " +
            "<if test='beforeId != null'> AND id &lt; #{beforeId} </if>" +
            "ORDER BY createdTime DESC LIMIT #{limit}" +
            "</script>")
    java.util.List<AgentMessage> selectByCursor(
            @Param("sessionId") String sessionId,
            @Param("beforeId") Long beforeId,
            @Param("limit") int limit);

    @Select("SELECT * FROM agent_message " +
            "WHERE sessionId = #{sessionId} " +
            "ORDER BY createdTime ASC LIMIT #{limit}")
    java.util.List<AgentMessage> selectBySessionIdOrderByTimeASC(
            @Param("sessionId") String sessionId,
            @Param("limit") int limit);

    @Select("SELECT * FROM agent_message " +
            "WHERE sessionId = #{sessionId} " +
            "ORDER BY createdTime DESC LIMIT #{limit}")
    java.util.List<AgentMessage> selectBySessionIdOrderByTimeDESC(
            @Param("sessionId") String sessionId,
            @Param("limit") int limit);

    @Update("UPDATE agent_message SET isSummary = 1 " +
            "WHERE sessionId = #{sessionId} AND createdTime < #{summaryTime}")
    int markAsSummary(@Param("sessionId") String sessionId,
                     @Param("summaryTime") Date summaryTime);
}
