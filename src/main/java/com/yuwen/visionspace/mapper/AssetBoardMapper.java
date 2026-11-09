package com.yuwen.visionspace.mapper;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface AssetBoardMapper {
    @Insert("INSERT INTO asset_board_operation (ownerId,operationId,operationType,boardId,requestDigest,proposalId) VALUES (#{owner},#{operation},'PROPOSAL',#{board},#{digest},#{proposal})")
    int reserve(@Param("owner") Long owner, @Param("operation") String operation, @Param("board") String board,
                @Param("digest") String digest, @Param("proposal") String proposal);
    @Update("UPDATE asset_board_operation SET revision=#{revision} WHERE ownerId=#{owner} AND operationId=#{operation} AND proposalId=#{proposal} AND revision IS NULL")
    int commitProposal(@Param("owner") Long owner, @Param("operation") String operation, @Param("proposal") String proposal, @Param("revision") long revision);
    record BoardRow(String id, Long ownerId, Long spaceId, Long revision, String payload, LocalDateTime createTime, LocalDateTime updateTime) { }
    record OperationRow(Long ownerId, String operationId, String operationType, String boardId, String requestDigest, Long revision, String proposalId) { }

    @Select("SELECT id, ownerId, spaceId, revision, payload, createTime, updateTime FROM asset_board WHERE id=#{id} AND ownerId=#{owner}")
    BoardRow find(@Param("id") String id, @Param("owner") Long owner);
    @Select("SELECT id, ownerId, spaceId, revision, payload, createTime, updateTime FROM asset_board WHERE id=#{id} AND ownerId=#{owner} FOR SHARE")
    BoardRow shareBoard(@Param("id") String id, @Param("owner") Long owner);
    @Select("SELECT id, ownerId, spaceId, revision, payload, createTime, updateTime FROM asset_board WHERE id=#{id} AND ownerId=#{owner} FOR UPDATE")
    BoardRow lockBoard(@Param("id") String id, @Param("owner") Long owner);
    @Select("SELECT ownerId, operationId, operationType, boardId, requestDigest, revision, proposalId FROM asset_board_operation WHERE ownerId=#{owner} AND operationId=#{operation}")
    OperationRow operation(@Param("owner") Long owner, @Param("operation") String operation);
    // 串行化同一所有者的操作键判定；唯一主键仍是最终约束，跨实例生效。
    @Select("SELECT id FROM user WHERE id=#{owner} AND isDelete=0 FOR UPDATE")
    Long lockOwner(Long owner);
    @Select("SELECT id FROM space WHERE id=#{space} AND spaceType=1 AND isDelete=0 FOR SHARE")
    Long lockSpace(Long space);
    @Select("SELECT spaceRole FROM space_user WHERE spaceId=#{space} AND userId=#{owner} FOR SHARE")
    String lockMembership(@Param("space") Long space, @Param("owner") Long owner);
    @Select("SELECT sessionId FROM agent_session WHERE sessionId=#{thread} AND userId=#{owner} AND isDelete=0 FOR SHARE")
    String lockSession(@Param("thread") String thread, @Param("owner") Long owner);
    @Insert("INSERT INTO asset_board (id, ownerId, spaceId, revision, payload) VALUES (#{id}, #{owner}, #{space}, 1, #{payload})")
    int create(@Param("id") String id, @Param("owner") Long owner, @Param("space") Long space, @Param("payload") String payload);
    @Update("UPDATE asset_board SET revision=revision+1, payload=#{payload}, updateTime=CURRENT_TIMESTAMP WHERE id=#{id} AND ownerId=#{owner} AND revision=#{revision}")
    int replace(@Param("id") String id, @Param("owner") Long owner, @Param("revision") long revision, @Param("payload") String payload);
    @Insert("INSERT INTO asset_board_operation (ownerId, operationId, operationType, boardId, requestDigest, revision) VALUES (#{owner}, #{operation}, #{type}, #{board}, #{digest}, #{revision})")
    int commitOperation(@Param("owner") Long owner, @Param("operation") String operation, @Param("type") String type,
                        @Param("board") String board, @Param("digest") String digest, @Param("revision") long revision);
}
