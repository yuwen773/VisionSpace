package com.yuwen.visionspace.model.dto.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;

import java.util.Set;

/** v1 只读检索契约；HTTP 边界拒绝未知字段与隐式类型转换。 */
public record AssetSearchRequest(String spaceId, String threadId, Criteria criteria, Integer limit) {
    public record Criteria(String orientation, String dominantColorFamily) { }

    public static AssetSearchRequest parse(JsonNode body) {
        fields(body, Set.of("spaceId", "threadId", "criteria", "limit"));
        JsonNode criteria = body.path("criteria");
        fields(criteria, Set.of("orientation", "dominantColorFamily"));
        String orientation = text(criteria, "orientation");
        String color = text(criteria, "dominantColorFamily");
        ThrowUtils.throwIf(orientation == null && color == null
                        || orientation != null && !"LANDSCAPE".equals(orientation)
                        || color != null && !"BLUE_OR_PURPLE".equals(color),
                ErrorCode.PARAMS_ERROR, "INVALID_REQUEST");
        String spaceId = text(body, "spaceId");
        ThrowUtils.throwIf(spaceId == null || !spaceId.matches("[1-9][0-9]{0,18}"),
                ErrorCode.PARAMS_ERROR, "INVALID_REQUEST");
        try {
            Long.parseLong(spaceId);
        } catch (NumberFormatException e) {
            ThrowUtils.throwIf(true, ErrorCode.PARAMS_ERROR, "INVALID_REQUEST");
        }
        int limit = 12;
        if (body.has("limit")) {
            ThrowUtils.throwIf(!body.get("limit").isIntegralNumber() || !body.get("limit").canConvertToInt(),
                    ErrorCode.PARAMS_ERROR, "INVALID_REQUEST");
            limit = body.get("limit").intValue();
        }
        ThrowUtils.throwIf(limit < 1 || limit > 50, ErrorCode.PARAMS_ERROR, "INVALID_REQUEST");
        return new AssetSearchRequest(spaceId, text(body, "threadId"), new Criteria(orientation, color), limit);
    }

    private static String text(JsonNode node, String key) {
        if (!node.has(key)) return null;
        ThrowUtils.throwIf(!node.get(key).isTextual() || node.get(key).textValue().isBlank(),
                ErrorCode.PARAMS_ERROR, "INVALID_REQUEST");
        return node.get(key).textValue();
    }

    private static void fields(JsonNode node, Set<String> allowed) {
        ThrowUtils.throwIf(node == null || !node.isObject(), ErrorCode.PARAMS_ERROR, "INVALID_REQUEST");
        node.fieldNames().forEachRemaining(key -> ThrowUtils.throwIf(!allowed.contains(key),
                ErrorCode.PARAMS_ERROR, "INVALID_REQUEST"));
    }
}
