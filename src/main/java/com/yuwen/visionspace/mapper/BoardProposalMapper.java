package com.yuwen.visionspace.mapper;

import org.apache.ibatis.annotations.*;

/** 提案是权威执行状态；与旧 Checkpoint 分表，迟到备份不能覆盖决定。 */
@Mapper
public interface BoardProposalMapper {
    record Row(String proposalId, String taskId, Long ownerId, String threadId, String boardId, Long spaceId,
               String proposalRequestId, String requestDigest, String operationId, Long expectedRevision,
               String target, String baseline, String status, String reasonCode, String decision, Long revision) { }
    String COLUMNS = "proposal_id, task_id, owner_id, thread_id, board_id, space_id, proposal_request_id, request_digest, operation_id, expected_revision, target, baseline, status, reason_code, decision, revision";
    @Select("SELECT " + COLUMNS + " FROM asset_board_proposal WHERE owner_id=#{owner} AND proposal_request_id=#{key}")
    Row byRequest(@Param("owner") Long owner, @Param("key") String key);
    @Select("SELECT " + COLUMNS + " FROM asset_board_proposal WHERE owner_id=#{owner} AND task_id=#{task}")
    Row byTask(@Param("owner") Long owner, @Param("task") String task);
    @Select("SELECT " + COLUMNS + " FROM asset_board_proposal WHERE owner_id=#{owner} AND proposal_id=#{id} FOR UPDATE")
    Row lock(@Param("owner") Long owner, @Param("id") String id);
    @Insert("INSERT INTO asset_board_proposal (" + COLUMNS + ") VALUES (#{proposalId},#{taskId},#{ownerId},#{threadId},#{boardId},#{spaceId},#{proposalRequestId},#{requestDigest},#{operationId},#{expectedRevision},#{target},#{baseline},#{status},#{reasonCode},#{decision},#{revision})")
    int create(Row row);
    @Update("UPDATE asset_board_proposal SET status='CONFLICT', reason_code='PROPOSAL_SUPERSEDED' WHERE owner_id=#{owner} AND board_id=#{board} AND thread_id=#{thread} AND status='AWAITING_CONFIRMATION'")
    int supersede(@Param("owner") Long owner, @Param("board") String board, @Param("thread") String thread);
    @Update("UPDATE asset_board_proposal SET status=#{status}, reason_code=#{reason}, decision=#{decision}, revision=#{revision} WHERE owner_id=#{owner} AND proposal_id=#{id} AND status='AWAITING_CONFIRMATION'")
    int finish(@Param("owner") Long owner, @Param("id") String id, @Param("status") String status,
               @Param("reason") String reason, @Param("decision") String decision, @Param("revision") Long revision);
    @Select("SELECT planning_digest FROM asset_board_proposal WHERE owner_id=#{owner} AND proposal_request_id=#{key}")
    String planningDigest(@Param("owner") Long owner, @Param("key") String key);
    @Update("UPDATE asset_board_proposal SET planning_digest=#{digest} WHERE owner_id=#{owner} AND proposal_id=#{id}")
    int bindPlan(@Param("owner") Long owner, @Param("id") String id, @Param("digest") String digest);
}
