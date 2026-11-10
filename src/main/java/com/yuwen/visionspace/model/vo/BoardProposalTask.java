package com.yuwen.visionspace.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BoardProposalTask(int schemaVersion, String type, String taskId, String proposalId, int proposalRevision,
                                String operationId, String status, String reasonCode, String boardId, String spaceId, String threadId,
                                @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = AssetBoard.RevisionSerializer.class) Long expectedRevision,
                                @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = AssetBoard.RevisionSerializer.class) Long revision,
                                AssetBoard current, AssetBoard target, boolean canConfirm) { }
