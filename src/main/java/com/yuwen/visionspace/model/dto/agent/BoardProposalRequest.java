package com.yuwen.visionspace.model.dto.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import java.util.Set;

public record BoardProposalRequest(String proposalRequestId, String threadId, String boardId, SaveAssetBoardRequest target) {
    public record Input(String proposalRequestId, String threadId, String boardId, Long expectedRevision,
                        String title, java.util.List<SaveAssetBoardRequest.Group> groups, java.util.List<SaveAssetBoardRequest.Entry> items) { }
    public record Plan(String proposalRequestId, String threadId, String boardId, long expectedRevision, String message) {
        public static Plan parse(JsonNode body) {
            ThrowUtils.throwIf(body == null || !body.isObject(), ErrorCode.PARAMS_ERROR, "INVALID_REQUEST");
            body.fieldNames().forEachRemaining(k -> ThrowUtils.throwIf(!Set.of("proposalRequestId", "threadId", "boardId", "expectedRevision", "message").contains(k), ErrorCode.PARAMS_ERROR, "INVALID_REQUEST"));
            ThrowUtils.throwIf(!body.path("expectedRevision").isIntegralNumber() || !body.path("expectedRevision").canConvertToLong()
                    || body.path("expectedRevision").longValue() < 1 || body.path("expectedRevision").longValue() > 9007199254740991L
                    || !body.path("message").isTextual() || body.path("message").asText().isBlank() || body.path("message").asText().length() > 2000, ErrorCode.PARAMS_ERROR, "INVALID_REQUEST");
            return new Plan(id(body,"proposalRequestId"), id(body,"threadId"), id(body,"boardId"), body.path("expectedRevision").longValue(), body.path("message").asText());
        }
    }
    public static BoardProposalRequest parse(JsonNode body) {
        ThrowUtils.throwIf(body == null || !body.isObject(), ErrorCode.PARAMS_ERROR, "INVALID_REQUEST");
        var target = ((ObjectNode) body).deepCopy();
        String key = id(body, "proposalRequestId"), thread = id(body, "threadId"), board = id(body, "boardId");
        target.remove(Set.of("proposalRequestId", "threadId", "boardId"));
        // 模型/客户端没有 operationId 或身份字段入口。
        ThrowUtils.throwIf(target.has("operationId"), ErrorCode.PARAMS_ERROR, "INVALID_REQUEST");
        target.put("operationId", "proposal-target");
        return new BoardProposalRequest(key, thread, board, SaveAssetBoardRequest.parse(target, true));
    }
    public record Decision(String operationId, int proposalRevision, String decision) {
        public static Decision parse(JsonNode body) {
            ThrowUtils.throwIf(body == null || !body.isObject(), ErrorCode.PARAMS_ERROR, "INVALID_REQUEST");
            body.fieldNames().forEachRemaining(k -> ThrowUtils.throwIf(!Set.of("operationId", "proposalRevision", "decision").contains(k), ErrorCode.PARAMS_ERROR, "INVALID_REQUEST"));
            String operation = id(body, "operationId");
            ThrowUtils.throwIf(!body.path("proposalRevision").isIntegralNumber() || !body.path("proposalRevision").canConvertToInt()
                    || body.path("proposalRevision").intValue() != 1 || !body.path("decision").isTextual()
                    || !Set.of("CONFIRM", "CANCEL").contains(body.path("decision").asText()), ErrorCode.PARAMS_ERROR, "INVALID_REQUEST");
            return new Decision(operation, 1, body.path("decision").asText());
        }
    }
    private static String id(JsonNode body, String field) {
        ThrowUtils.throwIf(!body.path(field).isTextual() || !body.path(field).asText().matches("[A-Za-z0-9_-]{1,64}"), ErrorCode.PARAMS_ERROR, "INVALID_REQUEST");
        return body.path(field).asText();
    }
}
