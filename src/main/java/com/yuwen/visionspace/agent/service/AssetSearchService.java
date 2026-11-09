package com.yuwen.visionspace.agent.service;

import com.yuwen.visionspace.mapper.BoardProposalMapper;
import com.yuwen.visionspace.model.dto.agent.BoardProposalRequest;
import com.yuwen.visionspace.model.vo.BoardProposalTask;
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
    @Resource private BoardProposalMapper proposals;
    @org.springframework.beans.factory.annotation.Value("${vision-space.board-proposal.writes-enabled:true}")
    private boolean proposalWritesEnabled;
    @org.springframework.beans.factory.annotation.Value("${vision-space.asset-board.writes-enabled:true}")
    private boolean boardWritesEnabled;

    private record BoardEntry(String boardItemId, String resultId, String contentVersion, Item source,
                              int position, boolean selected, String groupId) { }
    private record BoardPayload(String title, List<SaveAssetBoardRequest.Group> groups, List<BoardEntry> items) { }

    /** 同一事务绑定可信快照、板聚合和成功回执。 */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public AssetBoard.Operation saveBoard(String boardId, SaveAssetBoardRequest request, User viewer) {
        return saveBoard(boardId, request, viewer, null);
    }

    private AssetBoard.Operation saveBoard(String boardId, SaveAssetBoardRequest request, User viewer, String proposalId) {
        ThrowUtils.throwIf(viewer == null || viewer.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(boards.lockOwner(viewer.getId()) == null, ErrorCode.NOT_LOGIN_ERROR);
        String digest = digest(Arrays.asList(boardId, request));
        String type = boardId == null ? "CREATE" : "REPLACE";
        var previous = boards.operation(viewer.getId(), request.operationId());
        if (previous != null && proposalId == null) {
            ThrowUtils.throwIf(previous.proposalId() != null || !type.equals(previous.operationType())
                            || !digest.equals(previous.requestDigest()), ErrorCode.OPERATION_ERROR, "OPERATION_KEY_CONFLICT");
            return operationReceipt(previous, viewer);
        }
        if (proposalId != null) ThrowUtils.throwIf(previous == null || !proposalId.equals(previous.proposalId())
                || previous.revision() != null, ErrorCode.OPERATION_ERROR, "INVALID_OPERATION_STATE");
        ThrowUtils.throwIf(!boardWritesEnabled, ErrorCode.OPERATION_ERROR, "INVALID_OPERATION_STATE");
        var prepared = prepareBoard(boardId, request, viewer);
        var old = prepared.old();
        Long spaceId = prepared.spaceId();
        var entries = prepared.payload().items();
        String id = old == null ? UUID.randomUUID().toString() : old.id();
        long revision = old == null ? 1L : old.revision() + 1;
        String payload = encode(new BoardPayload(request.title(), request.groups(), entries));
        if (old == null) boards.create(id, viewer.getId(), spaceId, payload);
        else ThrowUtils.throwIf(boards.replace(id, viewer.getId(), request.expectedRevision(), payload) != 1,
                ErrorCode.OPERATION_ERROR, "REVISION_CONFLICT");
        if (proposalId == null) boards.commitOperation(viewer.getId(), request.operationId(), type, id, digest, revision);
        else ThrowUtils.throwIf(boards.commitProposal(viewer.getId(), request.operationId(), proposalId, revision) != 1,
                ErrorCode.OPERATION_ERROR, "INVALID_OPERATION_STATE");
        return new AssetBoard.Operation(1, request.operationId(), "COMMITTED", id, revision);
    }

    private record ProposalContent(SaveAssetBoardRequest request, BoardPayload payload) { }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public BoardProposalTask planSave(BoardProposalRequest.Plan request,
            User viewer, java.util.function.Function<AssetBoard, com.fasterxml.jackson.databind.JsonNode> planner) {
        requireOwnerLock(viewer);
        String digest = digest(request);
        var previous = proposals.byRequest(viewer.getId(), request.proposalRequestId());
        if (previous != null) {
            requireProposalSession(request.threadId(), viewer);
            ThrowUtils.throwIf(!digest.equals(proposals.planningDigest(viewer.getId(), request.proposalRequestId())), ErrorCode.OPERATION_ERROR, "OPERATION_KEY_CONFLICT");
            return taskProjection(previous, viewer, null);
        }
        ThrowUtils.throwIf(!proposalWritesEnabled || !boardWritesEnabled, ErrorCode.OPERATION_ERROR, "INVALID_OPERATION_STATE");
        var board = readBoard(request.boardId(), viewer);
        ThrowUtils.throwIf(board.revision() != request.expectedRevision(), ErrorCode.OPERATION_ERROR, "REVISION_CONFLICT");
        ThrowUtils.throwIf(board.items().stream().anyMatch(i -> !"AVAILABLE".equals(i.status())), ErrorCode.OPERATION_ERROR, "REFERENCE_STALE");
        sessions.bindOwner(request.threadId(), viewer.getId(), "候选板整理");
        requireProposalSession(request.threadId(), viewer);
        requireSearchAccess(Long.valueOf(board.spaceId()), request.threadId(), viewer);
        var output = planner.apply(board);
        ThrowUtils.throwIf(output == null || !output.isObject(), ErrorCode.PARAMS_ERROR, "INVALID_REQUEST");
        output.fieldNames().forEachRemaining(k -> ThrowUtils.throwIf(!java.util.Set.of("groups", "items").contains(k), ErrorCode.PARAMS_ERROR, "INVALID_REQUEST"));
        ThrowUtils.throwIf(!output.path("items").isArray(), ErrorCode.PARAMS_ERROR, "INVALID_REQUEST");
        var target = json.createObjectNode().put("proposalRequestId", request.proposalRequestId()).put("threadId", request.threadId())
                .put("boardId", request.boardId()).put("expectedRevision", request.expectedRevision()).put("title", board.title());
        target.set("groups", output.path("groups"));
        var entries = target.putArray("items");
        for (var item : output.path("items")) {
            ThrowUtils.throwIf(!item.isObject(), ErrorCode.PARAMS_ERROR, "INVALID_REQUEST");
            item.fieldNames().forEachRemaining(k -> ThrowUtils.throwIf(!java.util.Set.of("boardItemId", "position", "selected", "groupId").contains(k), ErrorCode.PARAMS_ERROR, "INVALID_REQUEST"));
            var source = board.items().stream().filter(i -> i.boardItemId().equals(item.path("boardItemId").asText())).findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.PARAMS_ERROR, "INVALID_REQUEST"));
            var entry = ((com.fasterxml.jackson.databind.node.ObjectNode) item).deepCopy().put("sourceVersion", source.sourceVersion());
            entries.add(entry);
        }
        var task = proposeSave(BoardProposalRequest.parse(target), viewer);
        proposals.bindPlan(viewer.getId(), task.proposalId(), digest);
        return task;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public BoardProposalTask proposeSave(BoardProposalRequest request, User viewer) {
        requireOwnerLock(viewer);
        requireProposalSession(request.threadId(), viewer);
        String digest = digest(Arrays.asList(request.threadId(), request.boardId(), request.target()));
        var previous = proposals.byRequest(viewer.getId(), request.proposalRequestId());
        if (previous != null) {
            ThrowUtils.throwIf(!digest.equals(previous.requestDigest()), ErrorCode.OPERATION_ERROR, "OPERATION_KEY_CONFLICT");
            return taskProjection(previous, viewer, null);
        }
        ThrowUtils.throwIf(!proposalWritesEnabled || !boardWritesEnabled, ErrorCode.OPERATION_ERROR, "INVALID_OPERATION_STATE");
        var prepared = prepareBoard(request.boardId(), request.target(), viewer);
        ThrowUtils.throwIf(!decodeBoard(prepared.old().payload()).title().equals(request.target().title()), ErrorCode.PARAMS_ERROR, "INVALID_REQUEST");
        String proposalId = UUID.randomUUID().toString(), taskId = UUID.randomUUID().toString(), operationId = UUID.randomUUID().toString();
        var t = request.target();
        var target = new SaveAssetBoardRequest(operationId, t.resultId(), t.expectedRevision(), t.title(), t.groups(), t.items());
        proposals.supersede(viewer.getId(), request.boardId(), request.threadId());
        var row = new BoardProposalMapper.Row(proposalId, taskId, viewer.getId(), request.threadId(),
                request.boardId(), prepared.spaceId(), request.proposalRequestId(), digest, operationId, t.expectedRevision(),
                encode(new ProposalContent(target, prepared.payload())), prepared.old().payload(), "AWAITING_CONFIRMATION", null, null, null);
        proposals.create(row);
        boards.reserve(viewer.getId(), operationId, request.boardId(), digest, proposalId);
        ThrowUtils.throwIf(messages.insertOwned(request.threadId(), viewer.getId(), "ASSISTANT", "board-proposal", taskId, null) != 1,
                ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
        return taskProjection(row, viewer, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public BoardProposalTask decideProposal(String proposalId,
            BoardProposalRequest.Decision decision, User viewer) {
        requireOwnerLock(viewer);
        var row = proposals.lock(viewer.getId(), proposalId);
        ThrowUtils.throwIf(row == null, ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
        requireProposalSession(row.threadId(), viewer);
        ThrowUtils.throwIf(decision.proposalRevision() != 1 || !row.operationId().equals(decision.operationId()), ErrorCode.PARAMS_ERROR, "INVALID_REQUEST");
        if (!"AWAITING_CONFIRMATION".equals(row.status())) {
            return taskProjection(row, viewer, decision.decision().equals(row.decision()) ? null : "INVALID_OPERATION_STATE");
        }
        String status = "CANCELLED", reason = null;
        Long revision = null;
        if ("CONFIRM".equals(decision.decision())) {
            ThrowUtils.throwIf(!proposalWritesEnabled || !boardWritesEnabled, ErrorCode.OPERATION_ERROR, "INVALID_OPERATION_STATE");
            var board = boards.lockBoard(row.boardId(), viewer.getId());
            try {
                ThrowUtils.throwIf(board == null, ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
                var content = proposalContent(row);
                // 资源锁持有到同一保存事务完成；区分不可用引用与仍有权但依据过期。
                content.payload().items().stream().map(e -> Long.valueOf(e.source().assetRef().pictureId())).sorted().distinct()
                        .forEach(pictureMapper::selectForUpdate);
                lockBoardSpace(row.spaceId(), viewer);
                if (!board.revision().equals(row.expectedRevision())) {
                    status = "CONFLICT"; reason = "REVISION_CONFLICT";
                } else {
                    boolean unavailable = content.payload().items().stream().anyMatch(e -> {
                        var picture = pictures.getById(Long.valueOf(e.source().assetRef().pictureId()));
                        return picture == null || !row.spaceId().equals(picture.getSpaceId()) || !contentAuth.canReadContent(picture, viewer);
                    });
                    if (unavailable) { status = "REJECTED"; reason = "RESOURCE_UNAVAILABLE"; }
                    else if (content.payload().items().stream().anyMatch(e -> !"AVAILABLE".equals(projectBoardEntry(e, row.spaceId(), viewer, null).status()))) {
                        status = "CONFLICT"; reason = "REFERENCE_STALE";
                    } else {
                        revision = saveBoard(row.boardId(), content.request(), viewer, row.proposalId()).revision();
                        status = "COMMITTED";
                    }
                }
            } catch (BusinessException failure) {
                if (failure.getCode() == ErrorCode.NO_AUTH_ERROR.getCode()) { status = "REJECTED"; reason = "RESOURCE_UNAVAILABLE"; }
                else if ("REFERENCE_STALE".equals(failure.getMessage()) || "REVISION_CONFLICT".equals(failure.getMessage())) {
                    status = "CONFLICT"; reason = failure.getMessage();
                } else throw failure;
            }
        }
        ThrowUtils.throwIf(proposals.finish(viewer.getId(), proposalId, status, reason, decision.decision(), revision) != 1,
                ErrorCode.OPERATION_ERROR, "INVALID_OPERATION_STATE");
        return taskProjection(proposals.lock(viewer.getId(), proposalId), viewer, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, readOnly = true)
    public BoardProposalTask readTask(String taskId, User viewer) {
        ThrowUtils.throwIf(viewer == null || viewer.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        var row = proposals.byTask(viewer.getId(), taskId);
        ThrowUtils.throwIf(row == null, ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
        requireProposalSession(row.threadId(), viewer);
        return taskProjection(row, viewer, null);
    }

    private void requireOwnerLock(User viewer) {
        ThrowUtils.throwIf(viewer == null || viewer.getId() == null || boards.lockOwner(viewer.getId()) == null, ErrorCode.NOT_LOGIN_ERROR);
    }
    private void requireProposalSession(String thread, User viewer) {
        ThrowUtils.throwIf(boards.lockSession(thread, viewer.getId()) == null, ErrorCode.NO_AUTH_ERROR, "RESOURCE_UNAVAILABLE");
    }
    private ProposalContent proposalContent(BoardProposalMapper.Row row) {
        try { return json.readValue(row.target(), ProposalContent.class); }
        catch (JsonProcessingException e) { throw new BusinessException(ErrorCode.SYSTEM_ERROR); }
    }
    private BoardProposalTask taskProjection(BoardProposalMapper.Row row, User viewer, String error) {
        AssetBoard before = null, target = null;
        boolean allowed = false;
        boolean currentVersion = false;
        try {
            lockBoardSpace(row.spaceId(), viewer);
            var board = boards.find(row.boardId(), viewer.getId());
            if (board != null) {
                before = proposalBoard(row, decodeBoard(row.baseline()), viewer);
                target = proposalBoard(row, proposalContent(row).payload(), viewer);
                allowed = before.items().stream().noneMatch(i -> "UNAVAILABLE".equals(i.status()))
                        && target.items().stream().noneMatch(i -> "UNAVAILABLE".equals(i.status()));
                currentVersion = board.revision().equals(row.expectedRevision()) && target.items().stream().allMatch(i -> "AVAILABLE".equals(i.status()));
            }
        } catch (BusinessException denied) { if (denied.getCode() != ErrorCode.NO_AUTH_ERROR.getCode()) throw denied; }
        return new BoardProposalTask(1, "BOARD_SAVE_PROPOSAL", row.taskId(), row.proposalId(), 1,
                row.operationId(), row.status(), error == null ? row.reasonCode() : error,
                allowed ? row.boardId() : null, allowed ? row.spaceId().toString() : null, allowed ? row.threadId() : null,
                allowed ? row.expectedRevision() : null, allowed ? row.revision() : null, allowed ? before : null, allowed ? target : null,
                allowed && currentVersion && proposalWritesEnabled && boardWritesEnabled && "AWAITING_CONFIRMATION".equals(row.status()));
    }
    private AssetBoard proposalBoard(BoardProposalMapper.Row row, BoardPayload payload, User viewer) {
        return new AssetBoard(1, row.boardId(), row.spaceId().toString(), payload.title(), row.expectedRevision(), null, null,
                payload.groups(), payload.items().stream().map(e -> projectBoardEntry(e, row.spaceId(), viewer, null)).toList());
    }

    private record PreparedBoard(AssetBoardMapper.BoardRow old, Long spaceId, BoardPayload payload) { }
    private PreparedBoard prepareBoard(String boardId, SaveAssetBoardRequest request, User viewer) {
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
        return new PreparedBoard(old, spaceId, new BoardPayload(request.title(), request.groups(), entries));
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
