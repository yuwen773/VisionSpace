package com.yuwen.visionspace.mapper;

import org.apache.ibatis.annotations.*;

/** 提案是权威执行状态；与旧 Checkpoint 分表，迟到备份不能覆盖决定。 */
@Mapper
public interface BoardProposalMapper {
    record Row(String proposalId, String taskId, Long ownerId, String threadId, String boardId, Long spaceId,
               String proposalRequestId, String requestDigest, String operationId, Long expectedRevision,
               String target, String baseline, String status, String reasonCode, String decision, Long revision) { }
    String COLUMNS = "proposalId, taskId, ownerId, threadId, boardId, spaceId, proposalRequestId, requestDigest, operationId, expectedRevision, target, baseline, status, reasonCode, decision, revision";
    @Select("SELECT " + COLUMNS + " FROM asset_board_proposal WHERE ownerId=#{owner} AND proposalRequestId=#{key}")
    Row byRequest(@Param("owner") Long owner, @Param("key") String key);
    @Select("SELECT " + COLUMNS + " FROM asset_board_proposal WHERE ownerId=#{owner} AND taskId=#{task}")
    Row byTask(@Param("owner") Long owner, @Param("task") String task);
    @Select("SELECT " + COLUMNS + " FROM asset_board_proposal WHERE ownerId=#{owner} AND proposalId=#{id} FOR UPDATE")
    Row lock(@Param("owner") Long owner, @Param("id") String id);
    @Insert("INSERT INTO asset_board_proposal (" + COLUMNS + ") VALUES (#{proposalId},#{taskId},#{ownerId},#{threadId},#{boardId},#{spaceId},#{proposalRequestId},#{requestDigest},#{operationId},#{expectedRevision},#{target},#{baseline},#{status},#{reasonCode},#{decision},#{revision})")
    int create(Row row);
    @Update("UPDATE asset_board_proposal SET status='CONFLICT', reasonCode='PROPOSAL_SUPERSEDED' WHERE ownerId=#{owner} AND boardId=#{board} AND threadId=#{thread} AND status='AWAITING_CONFIRMATION'")
    int supersede(@Param("owner") Long owner, @Param("board") String board, @Param("thread") String thread);
    @Update("UPDATE asset_board_proposal SET status=#{status}, reasonCode=#{reason}, decision=#{decision}, revision=#{revision} WHERE ownerId=#{owner} AND proposalId=#{id} AND status='AWAITING_CONFIRMATION'")
    int finish(@Param("owner") Long owner, @Param("id") String id, @Param("status") String status,
               @Param("reason") String reason, @Param("decision") String decision, @Param("revision") Long revision);
    @Select("SELECT planningDigest FROM asset_board_proposal WHERE ownerId=#{owner} AND proposalRequestId=#{key}")
    String planningDigest(@Param("owner") Long owner, @Param("key") String key);
    @Update("UPDATE asset_board_proposal SET planningDigest=#{digest} WHERE ownerId=#{owner} AND proposalId=#{id}")
    int bindPlan(@Param("owner") Long owner, @Param("id") String id, @Param("digest") String digest);
}
