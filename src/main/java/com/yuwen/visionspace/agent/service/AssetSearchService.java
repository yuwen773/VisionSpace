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
import com.yuwen.visionspace.mapper.AssetBoardMapper;
import com.yuwen.visionspace.model.dto.agent.SaveAssetBoardRequest;
import com.yuwen.visionspace.model.vo.AssetBoard;
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
    @Resource private AssetBoardMapper boards;
    @org.springframework.beans.factory.annotation.Value("${vision-space.asset-board.writes-enabled:true}")
    private boolean boardWritesEnabled;

    private record BoardEntry(String boardItemId, String resultId, String contentVersion, Item source,
                              int position, boolean selected, String groupId) { }
    private record BoardPayload(String title, List<SaveAssetBoardRequest.Group> groups, List<BoardEntry> items) { }

    /** 同一事务绑定可信快照、板聚合和成功回执。 */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public AssetBoard.Operation saveBoard(String boardId, SaveAssetBoardRequest request, User viewer) {
        ThrowUtils.throwIf(viewer == null || viewer.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(boards.lockOwner(viewer.getId()) == null, ErrorCode.NOT_LOGIN_ERROR);
        String digest = digest(Arrays.asList(boardId, request));
        String type = boardId == null ? "CREATE" : "REPLACE";
        var previous = boards.operation(viewer.getId(), request.operationId());
        if (previous != null) {
            ThrowUtils.throwIf(previous.proposalId() != null || !type.equals(previous.operationType())
                            || !digest.equals(previous.requestDigest()), ErrorCode.OPERATION_ERROR, "OPERATION_KEY_CONFLICT");
            return operationReceipt(previous, viewer);
        }
        ThrowUtils.throwIf(!boardWritesEnabled, ErrorCode.OPERATION_ERROR, "INVALID_OPERATION_STATE");
        // 来源由持久快照解析；请求中的事实、pictureId、owner/space 均没有入口。
        Map<String, StoredSnapshot> sources = new LinkedHashMap<>();
        var old = boardId == null ? null : boards.lockBoard(boardId, viewer.getId());
        ThrowUtils.throwIf(boardId != null && old == null, ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
        if (old != null) {
            requireSpace(old.spaceId(), viewer);
            ThrowUtils.throwIf(!old.revision().equals(request.expectedRevision()), ErrorCode.OPERATION_ERROR, "REVISION_CONFLICT");
            ThrowUtils.throwIf(old.revision() >= 9007199254740991L, ErrorCode.OPERATION_ERROR, "INVALID_OPERATION_STATE");
        }
        StoredSnapshot source = request.resultId() == null ? null : decodeSnapshot(requireOwnedSnapshot(request.resultId(), viewer));
        if (source != null) sources.put(request.resultId(), source);
        Long spaceId = old == null ? Long.valueOf(source.result().spaceId()) : old.spaceId();
        var oldItems = old == null ? List.<BoardEntry>of() : decodeBoard(old.payload()).items();
        List<BoardEntry> entries = new ArrayList<>();
        for (var entry : request.items()) {
            if (entry.boardItemId() != null) {
                BoardEntry trusted = oldItems.stream().filter(item -> item.boardItemId().equals(entry.boardItemId())).findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE"));
                ThrowUtils.throwIf(!trusted.source().sourceVersion().equals(entry.sourceVersion()), ErrorCode.OPERATION_ERROR, "REFERENCE_STALE");
                entries.add(new BoardEntry(trusted.boardItemId(), trusted.resultId(), trusted.contentVersion(), trusted.source(),
                        entry.position(), entry.selected(), entry.groupId()));
                continue;
            }
            String resultId = entry.resultId() == null ? request.resultId() : entry.resultId();
            ThrowUtils.throwIf(resultId == null, ErrorCode.PARAMS_ERROR, "INVALID_REQUEST");
            StoredSnapshot snapshot = sources.computeIfAbsent(resultId, id -> decodeSnapshot(requireOwnedSnapshot(id, viewer)));
            ThrowUtils.throwIf(!spaceId.toString().equals(snapshot.result().spaceId()) || entry.sourcePosition() >= snapshot.result().items().size(),
                    ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
            Item item = snapshot.result().items().get(entry.sourcePosition());
            ThrowUtils.throwIf(!item.sourceVersion().equals(entry.sourceVersion()), ErrorCode.OPERATION_ERROR, "REFERENCE_STALE");
            entries.add(new BoardEntry(UUID.randomUUID().toString(), resultId, snapshot.contentVersions().get(entry.sourcePosition()),
                    new Item(item.position(), "AVAILABLE", item.assetRef(), item.sourceVersion(), item.verifiedFacts(), item.matches(), List.of(), null),
                    entry.position(), entry.selected(), entry.groupId()));
        }
        ThrowUtils.throwIf(entries.stream().map(entry -> entry.source().assetRef().pictureId()).distinct().count() != entries.size(),
                ErrorCode.PARAMS_ERROR, "INVALID_REQUEST");
        entries.stream().map(entry -> Long.valueOf(entry.source().assetRef().pictureId())).sorted().distinct()
                .forEach(id -> ThrowUtils.throwIf(pictureMapper.selectForUpdate(id) == null, ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE"));
        lockBoardSpace(spaceId, viewer);
        for (String resultId : sources.keySet()) {
            var row = snapshots.selectOwned(resultId, viewer.getId());
            if (row.getThreadId() != null) ThrowUtils.throwIf(boards.lockSession(row.getThreadId(), viewer.getId()) == null,
                    ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
        }
        for (BoardEntry entry : entries) {
            var projected = projectBoardEntry(entry, spaceId, viewer, null);
            ThrowUtils.throwIf(!"AVAILABLE".equals(projected.status()), ErrorCode.OPERATION_ERROR, "REFERENCE_STALE");
        }
        String id = old == null ? UUID.randomUUID().toString() : old.id();
        long revision = old == null ? 1L : old.revision() + 1;
        String payload = encode(new BoardPayload(request.title(), request.groups(), entries));
        if (old == null) boards.create(id, viewer.getId(), spaceId, payload);
        else ThrowUtils.throwIf(boards.replace(id, viewer.getId(), request.expectedRevision(), payload) != 1,
                ErrorCode.OPERATION_ERROR, "REVISION_CONFLICT");
        boards.commitOperation(viewer.getId(), request.operationId(), type, id, digest, revision);
        return new AssetBoard.Operation(1, request.operationId(), "COMMITTED", id, revision);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, readOnly = true)
    public AssetBoard readBoard(String boardId, User viewer) {
        var row = ownedBoard(boardId, viewer);
        lockBoardSpace(row.spaceId(), viewer);
        BoardPayload payload = decodeBoard(row.payload());
        var projected = payload.items().stream().map(entry -> projectBoardEntry(entry, row.spaceId(), viewer, row.id())).toList();
        boolean unavailable = projected.stream().anyMatch(item -> "UNAVAILABLE".equals(item.status()));
        return new AssetBoard(1, row.id(), row.spaceId().toString(), unavailable ? null : payload.title(), row.revision(),
                row.createTime().toString(), row.updateTime().toString(), unavailable ? payload.groups().stream()
                        .map(group -> new SaveAssetBoardRequest.Group(group.groupId(), group.position(), "不可用分组")).toList() : payload.groups(), projected);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, readOnly = true)
    public AssetBoard.Operation readOperation(String operationId, User viewer) {
        ThrowUtils.throwIf(viewer == null || viewer.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        var row = boards.operation(viewer.getId(), operationId);
        return row == null ? new AssetBoard.Operation(1, operationId, "NOT_RECORDED", null, null) : operationReceipt(row, viewer);
    }

    private AssetBoardMapper.BoardRow ownedBoard(String boardId, User viewer) {
        ThrowUtils.throwIf(viewer == null || viewer.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        var row = boards.shareBoard(boardId, viewer.getId());
        ThrowUtils.throwIf(row == null, ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
        return row;
    }

    private AssetBoard.Operation operationReceipt(AssetBoardMapper.OperationRow row, User viewer) {
        if (row.revision() == null) return new AssetBoard.Operation(1, row.operationId(), "NOT_RECORDED", null, null);
        boolean allowed = false;
        var board = boards.find(row.boardId(), viewer.getId());
        if (board != null) {
            try { lockBoardSpace(board.spaceId(), viewer); allowed = true; }
            catch (BusinessException denied) { if (denied.getCode() != ErrorCode.NO_AUTH_ERROR.getCode()) throw denied; }
        }
        return new AssetBoard.Operation(1, row.operationId(), "COMMITTED", allowed ? row.boardId() : null, allowed ? row.revision() : null);
    }

    private void lockBoardSpace(Long spaceId, User viewer) {
        ThrowUtils.throwIf(boards.lockSpace(spaceId) == null, ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
        String role = boards.lockMembership(spaceId, viewer.getId());
        ThrowUtils.throwIf(role == null || !spaceAuth.hasPermission(role, SpaceUserPermissionConstant.PICTURE_VIEW),
                ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
    }

    private AssetBoard.Item projectBoardEntry(BoardEntry entry, Long spaceId, User viewer, String boardId) {
        Item source = entry.source();
        Picture current = pictures.getById(Long.valueOf(source.assetRef().pictureId()));
        if (current == null || !spaceId.equals(current.getSpaceId()) || !contentAuth.canReadContent(current, viewer)
                || files.listActiveByPicture(current.getId()).stream().anyMatch(file -> !"gallery".equals(file.getPurpose()))
                || !entry.contentVersion().equals(contentVersion(current))) {
            return new AssetBoard.Item(entry.boardItemId(), entry.position(), "UNAVAILABLE", null, null, null, null, null, null, null, null, null);
        }
        String status = source.sourceVersion().equals(sourceVersion(current, entry.contentVersion())) ? "AVAILABLE" : "STALE";
        return new AssetBoard.Item(entry.boardItemId(), entry.position(), status, entry.selected(), entry.groupId(), entry.resultId(),
                source.assetRef(), source.sourceVersion(), source.verifiedFacts(), source.matches(), source.interpretations(),
                boardId == null ? null : new Display("/agent/boards/" + boardId + "/items/" + entry.boardItemId() + "/content"));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public PictureContentService.Content loadBoardPreview(String boardId, String itemId, User viewer) {
        var row = ownedBoard(boardId, viewer);
        var entry = decodeBoard(row.payload()).items().stream().filter(item -> item.boardItemId().equals(itemId)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE"));
        Long pictureId = Long.valueOf(entry.source().assetRef().pictureId());
        ThrowUtils.throwIf(pictureMapper.selectForUpdate(pictureId) == null, ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
        lockBoardSpace(row.spaceId(), viewer);
        ThrowUtils.throwIf(projectBoardEntry(entry, row.spaceId(), viewer, boardId).assetRef() == null, ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
        return contents.loadContent(pictureId, PictureContentService.ContentKind.PREVIEW, viewer, false);
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new BusinessException(ErrorCode.SYSTEM_ERROR); }
    }
    private BoardPayload decodeBoard(String payload) {
        try { return json.readValue(payload, BoardPayload.class); }
        catch (JsonProcessingException e) { throw new BusinessException(ErrorCode.SYSTEM_ERROR); }
    }

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
        StoredSnapshot stored = decodeSnapshot(requireOwnedSnapshot(resultId, viewer));
        AssetSearchResult original = stored.result();
        List<Item> projected = original.items().stream().map(item -> projectItem(stored, item, viewer)).toList();
        return new AssetSearchResult(1, original.resultId(), original.spaceId(), original.observedAt(),
                original.criteria(), original.ruleVersion(), projected);
    }

    private AssetSearchSnapshot requireOwnedSnapshot(String resultId, User viewer) {
        ThrowUtils.throwIf(viewer == null || viewer.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        AssetSearchSnapshot snapshot = snapshots.selectOwned(resultId, viewer.getId());
        ThrowUtils.throwIf(snapshot == null, ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
        requireSnapshotAccess(snapshot, viewer);
        return snapshot;
    }

    private void requireSnapshotAccess(AssetSearchSnapshot snapshot, User viewer) {
        requireSpace(snapshot.getSpaceId(), viewer);
        if (snapshot.getThreadId() != null) sessions.requireOwned(snapshot.getThreadId(), viewer.getId());
    }

    private StoredSnapshot decodeSnapshot(AssetSearchSnapshot snapshot) {
        try { return json.readValue(snapshot.getPayload(), StoredSnapshot.class); }
        catch (JsonProcessingException e) { throw new BusinessException(ErrorCode.SYSTEM_ERROR); }
    }

    private Item projectItem(StoredSnapshot stored, Item item, User viewer) {
        Picture current = pictures.getById(Long.valueOf(item.assetRef().pictureId()));
        if (current == null || !stored.result().spaceId().equals(String.valueOf(current.getSpaceId()))
                || !contentAuth.canReadContent(current, viewer)) {
            return new Item(item.position(), "UNAVAILABLE", null, null, null, null, null, null);
        }
        String currentContentVersion = contentVersion(current);
        if (!stored.contentVersions().get(item.position()).equals(currentContentVersion)) {
            return new Item(item.position(), "UNAVAILABLE", null, null, null, null, null, null);
        }
        String status = item.sourceVersion().equals(sourceVersion(current, currentContentVersion)) ? "AVAILABLE" : "STALE";
        return new Item(item.position(), status, item.assetRef(), item.sourceVersion(),
                item.verifiedFacts(), item.matches(), item.interpretations(), item.display());
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
        AssetSearchSnapshot snapshot = requireOwnedSnapshot(resultId, viewer);
        StoredSnapshot stored = decodeSnapshot(snapshot);
        ThrowUtils.throwIf(position < 0 || position >= stored.result().items().size(), ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
        Item item = stored.result().items().get(position);
        Long id = Long.valueOf(item.assetRef().pictureId());
        ThrowUtils.throwIf(pictureMapper.selectForUpdate(id) == null, ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
        requireSnapshotAccess(snapshot, viewer);
        ThrowUtils.throwIf(projectItem(stored, item, viewer).assetRef() == null, ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
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
