package com.yuwen.visionspace.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yuwen.visionspace.model.dto.agent.SaveAssetBoardRequest.Group;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssetBoard(int schemaVersion, String boardId, String spaceId, String title,
                         @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = RevisionSerializer.class) long revision,
                         String createTime, String updateTime, List<Group> groups, List<Item> items) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @io.swagger.v3.oas.annotations.media.Schema(name = "AssetBoardItem")
    public record Item(String boardItemId, int position, String status, Boolean selected, String groupId,
                       String resultId, AssetSearchResult.AssetRef assetRef, String sourceVersion,
                       java.util.Map<String, Object> verifiedFacts, List<AssetSearchResult.Match> matches,
                       List<String> interpretations, AssetSearchResult.Display display) { }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @io.swagger.v3.oas.annotations.media.Schema(name = "AssetBoardOperation")
    public record Operation(int schemaVersion, String operationId, String status, String boardId,
                            @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = RevisionSerializer.class) Long revision) { }

    /** 项目全局把 Long ID 输出成字符串；revision 是 JS 安全整数，须单独保持数值协议。 */
    public static class RevisionSerializer extends com.fasterxml.jackson.databind.JsonSerializer<Long> {
        @Override
        public void serialize(Long value, com.fasterxml.jackson.core.JsonGenerator generator,
                              com.fasterxml.jackson.databind.SerializerProvider provider) throws java.io.IOException {
            generator.writeNumber(value);
        }
    }
}
