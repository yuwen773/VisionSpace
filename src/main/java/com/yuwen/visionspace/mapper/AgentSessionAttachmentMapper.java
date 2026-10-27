package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.AgentSessionAttachment;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 会话附件绑定。
 *
 * <p>绑定表是「这个引用是否有效」的唯一依据：回收任务清理对象之前必须先确认
 * 这里没有有效绑定，附件内容读取也必须先确认这里存在有效绑定。
 */
@Mapper
public interface AgentSessionAttachmentMapper extends BaseMapper<AgentSessionAttachment> {

    /**
     * 建立绑定。唯一索引在 (sessionId, resourceId) 上，重复绑定同一资源是空操作：
     * 既不会产生第二行，也不会把已解除的绑定重新变成有效——明确删除过的图片不会
     * 因为再次提交同一个资源标识而复活。
     */
    @Insert("INSERT INTO agent_session_attachment " +
            "(sessionId, resourceId, ownerUserId, status, legacy, createTime, updateTime) " +
            "VALUES (#{sessionId}, #{resourceId}, #{ownerUserId}, 0, #{legacy}, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE updateTime = updateTime")
    int insertIfAbsent(@Param("sessionId") String sessionId,
                       @Param("resourceId") Long resourceId,
                       @Param("ownerUserId") Long ownerUserId,
                       @Param("legacy") Integer legacy);

    /**
     * 锁定读一条绑定。绑定与回收并发时双方以行为锁为准，避免「判定未绑定」与
     * 「建立绑定」之间出现窗口。
     */
    @Select("SELECT * FROM agent_session_attachment " +
            "WHERE sessionId = #{sessionId} AND resourceId = #{resourceId} FOR UPDATE")
    AgentSessionAttachment selectForUpdate(@Param("sessionId") String sessionId,
                                           @Param("resourceId") Long resourceId);

    /**
     * 该资源当前是否还有有效绑定。没有有效绑定时才可以清理对象。
     */
    @Select("SELECT COUNT(*) FROM agent_session_attachment " +
            "WHERE resourceId = #{resourceId} AND status = 0")
    int countActiveByResource(@Param("resourceId") Long resourceId);

    /**
     * 会话内某资源是否处于有效绑定状态。
     */
    @Select("SELECT COUNT(*) FROM agent_session_attachment " +
            "WHERE sessionId = #{sessionId} AND resourceId = #{resourceId} AND status = 0")
    int countActive(@Param("sessionId") String sessionId,
                    @Param("resourceId") Long resourceId);

    /**
     * 解除资源的全部有效绑定。图片被明确删除时调用：先停访问，再清对象。
     */
    @Update("UPDATE agent_session_attachment SET status = 1, updateTime = NOW() " +
            "WHERE resourceId = #{resourceId} AND status = 0")
    int releaseByResource(@Param("resourceId") Long resourceId);

    /**
     * 解除会话的全部有效绑定。会话被明确删除时调用。
     */
    @Update("UPDATE agent_session_attachment SET status = 1, updateTime = NOW() " +
            "WHERE sessionId = #{sessionId} AND status = 0")
    int releaseBySession(@Param("sessionId") String sessionId);

    /**
     * 会话当前的有效绑定，用于会话删除后清理没有其他引用的对象。
     */
    @Select("SELECT * FROM agent_session_attachment " +
            "WHERE sessionId = #{sessionId} AND status = 0")
    List<AgentSessionAttachment> selectActiveBySession(@Param("sessionId") String sessionId);

    /**
     * 资源当前的有效绑定，用于「是否还有别人引用这个对象」的判定。
     */
    @Select("SELECT * FROM agent_session_attachment " +
            "WHERE resourceId = #{resourceId} AND status = 0")
    List<AgentSessionAttachment> selectActiveByResource(@Param("resourceId") Long resourceId);
}
