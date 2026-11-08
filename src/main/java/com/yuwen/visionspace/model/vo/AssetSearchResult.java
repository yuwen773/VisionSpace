package com.yuwen.visionspace.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yuwen.visionspace.model.dto.agent.AssetSearchRequest.Criteria;

import java.util.List;
import java.util.Map;

public record AssetSearchResult(int schemaVersion, String resultId, String spaceId, String observedAt,
                                Criteria criteria, String ruleVersion, List<Item> items) {
    public record AssetRef(String kind, String pictureId) { }
    public record Match(String criterion, String status, Map<String, Object> evidence) { }
    public record Display(String contentRef) { }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(int position, String status, AssetRef assetRef, String sourceVersion,
                       Map<String, Object> verifiedFacts, List<Match> matches,
                       List<String> interpretations, Display display) { }
}
