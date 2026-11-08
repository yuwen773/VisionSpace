package com.yuwen.visionspace.model.dto.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import java.util.*;

/** 明确的全量替换契约；来源位置与板中排序位置分别表达。 */
public record SaveAssetBoardRequest(String operationId, String resultId, Long expectedRevision,
                                    String title, List<Group> groups, List<Entry> items) {
    @io.swagger.v3.oas.annotations.media.Schema(name = "AssetBoardGroup")
    public record Group(String groupId, int position, String name) { }
    @io.swagger.v3.oas.annotations.media.Schema(name = "AssetBoardInputEntry")
    public record Entry(String boardItemId, String resultId, Integer sourcePosition, String sourceVersion,
                        int position, boolean selected, String groupId) { }

    public static SaveAssetBoardRequest parse(JsonNode body, boolean replace) {
        fields(body, Set.of("operationId", "resultId", "expectedRevision", "title", "groups", "items"));
        String operation = id(body, "operationId", true);
        String result = id(body, "resultId", !replace);
        Long revision = null;
        if (replace) {
            JsonNode n = body.path("expectedRevision");
            invalid(!n.isIntegralNumber() || !n.canConvertToLong() || n.longValue() < 1 || n.longValue() > 9007199254740991L);
            revision = n.longValue();
        } else invalid(body.has("expectedRevision"));
        String title = name(body, "title");
        invalid(!body.path("groups").isArray() || body.path("groups").size() > 20
                || !body.path("items").isArray() || body.path("items").size() > 50);
        List<Group> groups = new ArrayList<>();
        Set<String> groupIds = new HashSet<>();
        for (JsonNode n : body.path("groups")) {
            fields(n, Set.of("groupId", "position", "name"));
            String groupId = id(n, "groupId", true);
            invalid(!groupIds.add(groupId));
            groups.add(new Group(groupId, position(n, "position"), name(n, "name")));
        }
        groups.sort(Comparator.comparingInt(Group::position));
        for (int i = 0; i < groups.size(); i++) invalid(groups.get(i).position() != i);
        List<Entry> items = new ArrayList<>();
        for (JsonNode n : body.path("items")) {
            fields(n, Set.of("boardItemId", "resultId", "sourcePosition", "sourceVersion", "position", "selected", "groupId"));
            String boardItem = id(n, "boardItemId", false);
            String sourceResult = id(n, "resultId", false);
            Integer sourcePosition = n.has("sourcePosition") ? position(n, "sourcePosition") : null;
            invalid(boardItem == null && sourcePosition == null || boardItem != null && (sourcePosition != null || sourceResult != null)
                    || !replace && boardItem != null || !n.path("selected").isBoolean());
            String group = id(n, "groupId", false);
            invalid(group != null && !groupIds.contains(group));
            items.add(new Entry(boardItem, sourceResult, sourcePosition, id(n, "sourceVersion", true),
                    position(n, "position"), n.path("selected").booleanValue(), group));
        }
        items.sort(Comparator.comparingInt(Entry::position));
        for (int i = 0; i < items.size(); i++) invalid(items.get(i).position() != i);
        return new SaveAssetBoardRequest(operation, result, revision, title, List.copyOf(groups), List.copyOf(items));
    }

    private static int position(JsonNode n, String key) {
        invalid(!n.path(key).isIntegralNumber() || !n.path(key).canConvertToInt() || n.path(key).intValue() < 0);
        return n.path(key).intValue();
    }
    private static String id(JsonNode n, String key, boolean required) {
        if (!required && !n.has(key)) return null;
        invalid(!n.path(key).isTextual() || !n.path(key).asText().matches("[A-Za-z0-9_-]{1,64}"));
        return n.path(key).textValue();
    }
    private static String name(JsonNode n, String key) {
        invalid(!n.path(key).isTextual());
        String value = n.path(key).asText().strip();
        invalid(value.isBlank() || value.codePointCount(0, value.length()) > 100);
        return value;
    }
    private static void fields(JsonNode n, Set<String> allowed) {
        invalid(n == null || !n.isObject());
        n.fieldNames().forEachRemaining(key -> invalid(!allowed.contains(key)));
    }
    private static void invalid(boolean condition) { ThrowUtils.throwIf(condition, ErrorCode.PARAMS_ERROR, "INVALID_REQUEST"); }
}
