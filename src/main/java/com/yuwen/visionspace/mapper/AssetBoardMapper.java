package com.yuwen.visionspace.mapper;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface AssetBoardMapper {
    @Insert("INSERT INTO asset_board_operation (owner_id,operation_id,operation_type,board_id,request_digest,proposal_id) VALUES (#{owner},#{operation},'PROPOSAL',#{board},#{digest},#{proposal})")
    int reserve(@Param("owner") Long owner, @Param("operation") String operation, @Param("board") String board,
                @Param("digest") String digest, @Param("proposal") String proposal);
    @Update("UPDATE asset_board_operation SET revision=#{revision} WHERE owner_id=#{owner} AND operation_id=#{operation} AND proposal_id=#{proposal} AND revision IS NULL")
    int commitProposal(@Param("owner") Long owner, @Param("operation") String operation, @Param("proposal") String proposal, @Param("revision") long revision);
    record BoardRow(String id, Long ownerId, Long spaceId, Long revision, String payload, LocalDateTime createTime, LocalDateTime updateTime) { }
    record OperationRow(Long ownerId, String operationId, String operationType, String boardId, String requestDigest, Long revision, String proposalId) { }

    @Select("SELECT id, owner_id, space_id, revision, payload, create_time, update_time FROM asset_board WHERE id=#{id} AND owner_id=#{owner}")
    BoardRow find(@Param("id") String id, @Param("owner") Long owner);
    @Select("SELECT id, owner_id, space_id, revision, payload, create_time, update_time FROM asset_board WHERE id=#{id} AND owner_id=#{owner} FOR SHARE")
    BoardRow shareBoard(@Param("id") String id, @Param("owner") Long owner);
    @Select("SELECT id, owner_id, space_id, revision, payload, create_time, update_time FROM asset_board WHERE id=#{id} AND owner_id=#{owner} FOR UPDATE")
    BoardRow lockBoard(@Param("id") String id, @Param("owner") Long owner);
    @Select("SELECT owner_id, operation_id, operation_type, board_id, request_digest, revision, proposal_id FROM asset_board_operation WHERE owner_id=#{owner} AND operation_id=#{operation}")
    OperationRow operation(@Param("owner") Long owner, @Param("operation") String operation);
    // 串行化同一所有者的操作键判定；唯一主键仍是最终约束，跨实例生效。
    @Select("SELECT id FROM app_user WHERE id=#{owner} AND is_delete=0 FOR UPDATE")
    Long lockOwner(Long owner);
    @Select("SELECT id FROM space WHERE id=#{space} AND space_type=1 AND is_delete=0 FOR SHARE")
    Long lockSpace(Long space);
    @Select("SELECT space_role FROM space_user WHERE space_id=#{space} AND user_id=#{owner} FOR SHARE")
    String lockMembership(@Param("space") Long space, @Param("owner") Long owner);
    @Select("SELECT session_id FROM agent_session WHERE session_id=#{thread} AND user_id=#{owner} AND is_delete=0 FOR SHARE")
    String lockSession(@Param("thread") String thread, @Param("owner") Long owner);
    @Insert("INSERT INTO asset_board (id, owner_id, space_id, revision, payload) VALUES (#{id}, #{owner}, #{space}, 1, #{payload})")
    int create(@Param("id") String id, @Param("owner") Long owner, @Param("space") Long space, @Param("payload") String payload);
    @Update("UPDATE asset_board SET revision=revision+1, payload=#{payload}, update_time=statement_timestamp() WHERE id=#{id} AND owner_id=#{owner} AND revision=#{revision}")
    int replace(@Param("id") String id, @Param("owner") Long owner, @Param("revision") long revision, @Param("payload") String payload);
    @Insert("INSERT INTO asset_board_operation (owner_id, operation_id, operation_type, board_id, request_digest, revision) VALUES (#{owner}, #{operation}, #{type}, #{board}, #{digest}, #{revision})")
    int commitOperation(@Param("owner") Long owner, @Param("operation") String operation, @Param("type") String type,
                        @Param("board") String board, @Param("digest") String digest, @Param("revision") long revision);
}
