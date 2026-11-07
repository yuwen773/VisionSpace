package com.yuwen.visionspace.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.manager.auth.PictureContentAuthManager;
import com.yuwen.visionspace.manager.auth.SpaceUserAuthManager;
import com.yuwen.visionspace.manager.auth.model.SpaceUserPermissionConstant;
import com.yuwen.visionspace.mapper.AssetSearchSnapshotMapper;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.mapper.AgentRunStateMapper;
import com.yuwen.visionspace.mapper.AgentMessageMapper;
import com.yuwen.visionspace.agent.recovery.RunStatus;
import com.yuwen.visionspace.model.dto.agent.AssetSearchRequest;
import com.yuwen.visionspace.model.entity.AssetSearchSnapshot;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.PictureFile;
import com.yuwen.visionspace.model.entity.Space;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.vo.AssetSearchResult;
import com.yuwen.visionspace.model.vo.AssetSearchResult.*;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.PictureFileService;
import com.yuwen.visionspace.service.PictureContentService;
import com.yuwen.visionspace.service.SpaceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.apache.commons.codec.digest.DigestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 站内找图唯一业务入口；不接入 ImageAgent 的外部工具或恢复路径。 */
@Service
public class AssetSearchService {
    @Resource private PictureService pictures;
    @Resource private SpaceService spaces;
    @Resource private SpaceUserAuthManager spaceAuth;
    @Resource private PictureContentAuthManager contentAuth;
    @Resource private AgentSessionGuard sessions;
    @Resource private AssetSearchSnapshotMapper snapshots;
    @Resource private ObjectMapper json;
    @Resource private PictureFileService files;
    @Resource private PictureContentService contents;
    @Resource private PictureMapper pictureMapper;
    @Resource private AgentRunStateMapper runStates;
    @Resource private AgentMessageMapper messages;
    @Resource private AssetColorRule colors;

    private record StoredSnapshot(AssetSearchResult result, List<String> contentVersions) { }
    private record Candidate(Picture picture, Map<String, Object> facts, List<Match> matches, double score) { }

    @Transactional(rollbackFor = Exception.class)
    public AssetSearchResult search(AssetSearchRequest request, User viewer) {
        requireSearchAccess(Long.valueOf(request.spaceId()), request.threadId(), viewer);
        AssetColorRule.Rule colorRule = request.criteria().dominantColorFamily() == null ? null : colors.requireReady();
        String ruleVersion = (request.criteria().orientation() == null ? "" : "orientation-v1")
                + (colorRule == null ? "" : (request.criteria().orientation() == null ? "" : "+") + colorRule.version());
        List<Picture> candidates = pictures.lambdaQuery().eq(Picture::getSpaceId, Long.valueOf(request.spaceId())).list();
        List<Candidate> matched = new ArrayList<>();
        for (Picture picture : candidates) {
            if (!contentAuth.canReadContent(picture, viewer)) continue;
            Integer width = picture.getPicWidth();
            Integer height = picture.getPicHeight();
            boolean knownDimensions = width != null && height != null && width > 0 && height > 0;
            if (request.criteria().orientation() != null && (!knownDimensions || width <= height)) continue;
            String color = AssetColorRule.normalize(picture.getPicColor());
            double score = colorRule == null || color == null ? 0 : colorRule.score(color);
            if (colorRule != null && (color == null || score < colorRule.threshold())) continue;
            Map<String, Object> facts = new LinkedHashMap<>();
            if (width != null && width > 0) facts.put("width", width);
            if (height != null && height > 0) facts.put("height", height);
            if (knownDimensions) facts.put("orientation", width > height ? "LANDSCAPE" : width.equals(height) ? "SQUARE" : "PORTRAIT");
            if (color != null) facts.put("dominantColor", color);
            List<Match> matches = new ArrayList<>();
            if (request.criteria().orientation() != null) matches.add(new Match("LANDSCAPE", "MATCHED",
                    Map.of("rule", "WIDTH_GT_HEIGHT", "width", width, "height", height)));
            if (colorRule != null) matches.add(new Match("BLUE_OR_PURPLE", "MATCHED",
                    Map.of("rule", colorRule.version(), "blueAnchor", colorRule.blueAnchor(), "purpleAnchor", colorRule.purpleAnchor(),
                            "score", score, "threshold", colorRule.threshold())));
            matched.add(new Candidate(picture, facts, matches, score));
        }
        matched.sort(Comparator.comparingDouble(Candidate::score).reversed().thenComparing(candidate -> candidate.picture().getId()));
        List<Item> items = new ArrayList<>();
        List<String> contentVersions = new ArrayList<>();
        String id = UUID.randomUUID().toString();
        for (Candidate candidate : matched.subList(0, Math.min(request.limit(), matched.size()))) {
            Picture picture = candidate.picture();
            String contentVersion = contentVersion(picture);
            contentVersions.add(contentVersion);
            items.add(new Item(items.size(), "AVAILABLE", new AssetRef("INTERNAL", picture.getId().toString()),
                    sourceVersion(picture, contentVersion), candidate.facts(), candidate.matches(),
                    List.of(), new Display("/agent/asset-results/" + id + "/items/" + items.size() + "/content")));
        }
        AssetSearchResult result = new AssetSearchResult(1, id, request.spaceId(), Instant.now().toString(),
                request.criteria(), ruleVersion, items);
        AssetSearchSnapshot snapshot = new AssetSearchSnapshot();
        snapshot.setId(id);
        snapshot.setOwnerId(viewer.getId());
        snapshot.setSpaceId(Long.valueOf(request.spaceId()));
        snapshot.setThreadId(request.threadId());
        try { snapshot.setPayload(json.writeValueAsString(new StoredSnapshot(result, contentVersions))); }
        catch (JsonProcessingException e) { throw new BusinessException(ErrorCode.SYSTEM_ERROR); }
        snapshots.insert(snapshot);
        if (request.threadId() != null) {
            // 历史仅保存引用；每次展示通过 readResult 重新授权，不固化私人事实或直链。
            ThrowUtils.throwIf(messages.insertOwned(request.threadId(), viewer.getId(), "ASSISTANT", "asset-result", id, null) != 1,
                    ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
        }
        return result;
    }

    public AssetSearchResult readResult(String resultId, User viewer) {
        ThrowUtils.throwIf(viewer == null || viewer.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        AssetSearchSnapshot snapshot = snapshots.selectOwned(resultId, viewer.getId());
        ThrowUtils.throwIf(snapshot == null, ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
        requireSpace(snapshot.getSpaceId(), viewer);
        if (snapshot.getThreadId() != null) sessions.requireOwned(snapshot.getThreadId(), viewer.getId());
        try {
            StoredSnapshot stored = json.readValue(snapshot.getPayload(), StoredSnapshot.class);
            AssetSearchResult original = stored.result();
            List<Item> projected = new ArrayList<>();
            for (Item item : original.items()) {
                Picture current = pictures.getById(Long.valueOf(item.assetRef().pictureId()));
                if (current == null || !snapshot.getSpaceId().equals(current.getSpaceId())
                        || !contentAuth.canReadContent(current, viewer)
                        || !stored.contentVersions().get(item.position()).equals(contentVersion(current))) {
                    projected.add(new Item(item.position(), "UNAVAILABLE", null, null, null, null, null, null));
                } else {
                    String status = item.sourceVersion().equals(sourceVersion(current, contentVersion(current))) ? "AVAILABLE" : "STALE";
                    projected.add(new Item(item.position(), status, item.assetRef(), item.sourceVersion(),
                            item.verifiedFacts(), item.matches(), item.interpretations(), item.display()));
                }
            }
            return new AssetSearchResult(1, original.resultId(), original.spaceId(), original.observedAt(),
                    original.criteria(), original.ruleVersion(), projected);
        }
        catch (JsonProcessingException e) { throw new BusinessException(ErrorCode.SYSTEM_ERROR); }
    }

    private String contentVersion(Picture picture) {
        List<PictureFile> active = new ArrayList<>(files.listActiveByPicture(picture.getId()));
        active.sort(Comparator.comparing(PictureFile::getId));
        var locations = active.stream().map(file -> Arrays.asList(file.getId(), file.getKind(),
                file.getPlatform(), file.getObjectKey(), file.getFileSize())).toList();
        return digest(Arrays.asList(picture.getId(), picture.getSpaceId(), picture.getUrl(),
                picture.getThumbnailUrl(), picture.getPreviewUrl(), picture.getPicSize(), locations));
    }

    /** 图片行锁与替换/删除共用，避免校验旧版本后读取到替换内容。 */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public PictureContentService.Content loadPreview(String resultId, int position, User viewer) {
        AssetSearchResult result = readResult(resultId, viewer);
        ThrowUtils.throwIf(position < 0 || position >= result.items().size(), ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
        Item item = result.items().get(position);
        ThrowUtils.throwIf(item.assetRef() == null, ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
        Long id = Long.valueOf(item.assetRef().pictureId());
        ThrowUtils.throwIf(pictureMapper.selectForUpdate(id) == null, ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
        ThrowUtils.throwIf(readResult(resultId, viewer).items().get(position).assetRef() == null,
                ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
        return contents.loadContent(id, PictureContentService.ContentKind.PREVIEW, viewer, false);
    }

    private String sourceVersion(Picture picture, String contentVersion) {
        return digest(Arrays.asList(contentVersion, picture.getPicWidth(), picture.getPicHeight(), picture.getPicColor()));
    }

    private String digest(Object value) {
        try { return DigestUtils.sha256Hex(json.writeValueAsBytes(value)); }
        catch (JsonProcessingException e) { throw new BusinessException(ErrorCode.SYSTEM_ERROR); }
    }

    private void requireSpace(Long spaceId, User viewer) {
        ThrowUtils.throwIf(viewer == null || viewer.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        Space space = spaces.getById(spaceId);
        ThrowUtils.throwIf(space == null || !Integer.valueOf(1).equals(space.getSpaceType())
                        || !spaceAuth.getPermissionList(space, viewer).contains(SpaceUserPermissionConstant.PICTURE_VIEW),
                ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
    }

    public void requireSearchAccess(Long spaceId, String threadId, User viewer) {
        requireSpace(spaceId, viewer);
        if (threadId == null) return;
        var session = sessions.requireOwned(threadId, viewer.getId());
        var state = runStates.selectOwned(threadId, viewer.getId());
        ThrowUtils.throwIf(state != null && !RunStatus.COMPLETED.equals(state.getRunStatus())
                        || state == null && session.getCheckpointData() != null,
                ErrorCode.OPERATION_ERROR, "INVALID_OPERATION_STATE");
    }
}
