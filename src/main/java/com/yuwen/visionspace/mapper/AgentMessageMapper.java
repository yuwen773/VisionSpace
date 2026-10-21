package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.AgentMessage;
import org.apache.ibatis.annotations.*;

import java.util.Date;

@Mapper
public interface AgentMessageMapper extends BaseMapper<AgentMessage> {

    /**
     * 按游标倒序取一页消息（最新在前）。
     * <p>
     * 排序键与游标键必须同源：createdTime 是秒级 datetime，同一秒内常有多条消息，
     * 只按 createdTime 排序时并列组内的顺序不确定；若游标又用 {@code id <}，
     * 并列组被分页边界切开就会跳页或重复。因此统一用 {@code (createdTime, id)}
     * 复合键排序与比较。
     * <p>
     * 游标行（beforeId 指向的消息）必须仍然存在；agent_message 没有删除路径，
     * 摘要只置 isSummary=1，所以该前提成立。
     */
    @Select("<script>" +
            "SELECT * FROM agent_message " +
            "WHERE sessionId = #{sessionId} " +
            "<if test='beforeId != null'> " +
            "AND (createdTime, id) &lt; (SELECT createdTime, id FROM agent_message " +
            "WHERE id = #{beforeId} AND sessionId = #{sessionId}) " +
            "</if>" +
            "ORDER BY createdTime DESC, id DESC LIMIT #{limit}" +
            "</script>")
    java.util.List<AgentMessage> selectByCursor(
            @Param("sessionId") String sessionId,
            @Param("beforeId") Long beforeId,
            @Param("limit") int limit);

    @Update("UPDATE agent_message SET isSummary = 1 " +
            "WHERE sessionId = #{sessionId} AND createdTime < #{summaryTime}")
    int markAsSummary(@Param("sessionId") String sessionId,
                     @Param("summaryTime") Date summaryTime);
}
