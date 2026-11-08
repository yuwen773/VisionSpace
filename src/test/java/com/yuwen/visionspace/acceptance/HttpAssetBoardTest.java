package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@IsolatedSpringBootTest
class HttpAssetBoardTest {
    @LocalServerPort private int port;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ExternalCallRecorder recorder;
    private HttpClient browser;
    private long user;
    private long space;
    private long picture;
    private JsonNode result;

    @org.junit.jupiter.api.AfterEach
    void closeBrowser() { if (browser != null) browser.close(); }

    @BeforeEach
    void fixture() throws Exception {
        browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        String account = "board_" + UUID.randomUUID().toString().replace("-", "");
        user = call("POST", "/user/register", Map.of("userAccount", account, "userPassword", "acceptance-password",
                "checkPassword", "acceptance-password")).path("data").asLong();
        assertEquals(0, call("POST", "/user/login", Map.of("userAccount", account, "userPassword", "acceptance-password")).path("code").asInt());
        space = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        picture = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        jdbc.update("INSERT INTO space (id, userId, spaceType, spaceName, totalSize, totalCount) VALUES (?, ?, 1, 'board-team', 10, 1)", space, user + 1);
        jdbc.update("INSERT INTO space_user (spaceId, userId, spaceRole) VALUES (?, ?, 'viewer')", space, user);
        jdbc.update("INSERT INTO picture (id, userId, spaceId, name, url, picWidth, picHeight, picSize) VALUES (?, ?, ?, 'gallery', 'https://origin.invalid/board.jpg', 1920, 1080, 10)", picture, user + 1, space);
        result = call("POST", "/agent/asset-search", Map.of("spaceId", String.valueOf(space), "criteria", Map.of("orientation", "LANDSCAPE"))).path("data");
        assertEquals(1, result.path("items").size());
    }

    @Test
    void viewerCreatesAndRecoversAPersonalBoardWithoutChangingAssets() throws Exception {
        int calls = recorder.calls().size();
        var receipt = call("POST", "/agent/boards", create("create-one"));
        assertEquals(0, receipt.path("code").asInt(), receipt.toString());
        assertEquals(1, receipt.path("data").path("revision").asInt());
        assertTrue(receipt.path("data").path("revision").isIntegralNumber(), "revision must round-trip as a JSON number, unlike Long IDs");
        String id = receipt.path("data").path("boardId").asText();
        var board = call("GET", "/agent/boards/" + id, null).path("data");
        assertEquals(String.valueOf(space), board.path("spaceId").asText());
        assertEquals("我的候选板", board.path("title").asText());
        assertEquals(String.valueOf(picture), board.path("items").get(0).path("assetRef").path("pictureId").asText());
        assertFalse(board.path("items").get(0).path("boardItemId").asText().isBlank());
        assertTrue(board.path("items").get(0).path("selected").asBoolean());
        assertEquals("COMMITTED", call("GET", "/agent/board-operations/create-one", null).path("data").path("status").asText());
        assertEquals(10L, jdbc.queryForObject("SELECT totalSize FROM space WHERE id = ?", Long.class, space));
        assertEquals(calls, recorder.calls().size());
    }

    private Map<String, Object> create(String operation) {
        return Map.of("operationId", operation, "resultId", result.path("resultId").asText(), "title", "我的候选板",
                "groups", List.of(), "items", List.of(Map.of("position", 0, "sourcePosition", 0,
                        "sourceVersion", result.path("items").get(0).path("sourceVersion").asText(), "selected", true)));
    }

    @Test
    void replacementKeepsStableItemsAndRetriesDoNotOverwriteANewerBoard() throws Exception {
        String id = call("POST", "/agent/boards", create("initial")).path("data").path("boardId").asText();
        var item = call("GET", "/agent/boards/" + id, null).path("data").path("items").get(0);
        var replacement = Map.of("operationId", "replace-one", "expectedRevision", 1, "title", "整理完成",
                "groups", List.of(Map.of("groupId", "g1", "position", 0, "name", "海报")),
                "items", List.of(Map.of("boardItemId", item.path("boardItemId").asText(), "sourceVersion", item.path("sourceVersion").asText(),
                        "position", 0, "selected", false, "groupId", "g1")));
        var receipt = call("PUT", "/agent/boards/" + id, replacement);
        assertEquals(0, receipt.path("code").asInt(), receipt.toString());
        assertEquals(2, receipt.path("data").path("revision").asInt());
        assertEquals(receipt, call("PUT", "/agent/boards/" + id, replacement));
        var board = call("GET", "/agent/boards/" + id, null).path("data");
        assertEquals(2, board.path("revision").asInt());
        assertEquals(item.path("boardItemId"), board.path("items").get(0).path("boardItemId"));
        assertFalse(board.path("items").get(0).path("selected").asBoolean());
        assertEquals("海报", board.path("groups").get(0).path("name").asText());
        var empty = Map.of("operationId", "empty", "expectedRevision", 2, "title", "空板", "groups", List.of(), "items", List.of());
        assertEquals(0, call("PUT", "/agent/boards/" + id, empty).path("code").asInt());
        assertEquals(2, call("PUT", "/agent/boards/" + id, replacement).path("data").path("revision").asInt());
        var oldRevision = new java.util.HashMap<String, Object>(empty);
        oldRevision.put("operationId", "stale-client");
        assertEquals("REVISION_CONFLICT", call("PUT", "/agent/boards/" + id, oldRevision).path("message").asText());
        assertEquals("空板", call("GET", "/agent/boards/" + id, null).path("data").path("title").asText());
    }

    private JsonNode call(String method, String path, Object body) throws Exception {
        return callAt(port, method, path, body);
    }
    private JsonNode callAt(int servicePort, String method, String path, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + servicePort + "/api" + path));
        if (body == null) request.GET();
        else request.header("Content-Type", "application/json; charset=UTF-8").header(CsrfToken.HEADER, CsrfToken.of(browser))
                .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return json.readTree(browser.send(request.build(), HttpResponse.BodyHandlers.ofString()).body());
    }

    @Test
    void invalidInputAndForeignIdentityCannotCreateOrReadPrivateBoards() throws Exception {
        int calls = recorder.calls().size();
        for (String field : List.of("ownerId", "spaceId", "facts", "matches", "assetRef")) {
            var forged = new java.util.HashMap<>(create("forged-" + field));
            forged.put(field, "untrusted");
            assertEquals(40000, call("POST", "/agent/boards", forged).path("code").asInt());
        }
        for (Object groups : List.of(List.of(Map.of("groupId", "g", "position", 1, "name", "wrong-order")),
                java.util.stream.IntStream.range(0, 21).mapToObj(i -> Map.of("groupId", "g" + i, "position", i, "name", "group")).toList())) {
            var invalid = new java.util.HashMap<>(create("bad-groups")); invalid.put("groups", groups);
            assertEquals(40000, call("POST", "/agent/boards", invalid).path("code").asInt());
        }
        var originalEntry = Map.of("position", 0, "sourcePosition", 0, "sourceVersion", result.path("items").get(0).path("sourceVersion").asText(), "selected", true);
        var badGroup = new java.util.HashMap<String, Object>(originalEntry); badGroup.put("groupId", "unknown");
        var invalidShapes = List.of(Map.of("title", "x".repeat(101)),
                Map.of("items", java.util.Collections.nCopies(51, originalEntry)),
                Map.of("items", List.of(originalEntry, originalEntry)), Map.of("items", List.of(badGroup)),
                Map.of("groups", List.of(Map.of("groupId", "g", "position", 0, "name", "x".repeat(101)))));
        int shape = 0;
        for (var fields : invalidShapes) {
            String operation = "shape-" + shape++;
            var invalid = new java.util.HashMap<String, Object>(create(operation)); invalid.putAll(fields);
            assertEquals(40000, call("POST", "/agent/boards", invalid).path("code").asInt());
            assertEquals("NOT_RECORDED", call("GET", "/agent/board-operations/" + operation, null).path("data").path("status").asText());
        }
        String id = call("POST", "/agent/boards", create("private")).path("data").path("boardId").asText();
        HttpClient original = browser;
        browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        assertEquals(40100, call("GET", "/agent/boards/" + id, null).path("code").asInt());
        String account = "other_board_" + System.nanoTime();
        long stranger = call("POST", "/user/register", Map.of("userAccount", account, "userPassword", "acceptance-password", "checkPassword", "acceptance-password")).path("data").asLong();
        call("POST", "/user/login", Map.of("userAccount", account, "userPassword", "acceptance-password"));
        jdbc.update("UPDATE user SET userRole='admin' WHERE id=?", stranger);
        jdbc.update("INSERT INTO space_user (spaceId, userId, spaceRole) VALUES (?, ?, 'admin')", space, stranger);
        assertEquals(40101, call("GET", "/agent/boards/" + id, null).path("code").asInt());
        assertEquals(40101, call("POST", "/agent/boards", create("foreign-result")).path("code").asInt());
        assertEquals("NOT_RECORDED", call("GET", "/agent/board-operations/private", null).path("data").path("status").asText());
        browser = original;
        var state = call("GET", "/agent/boards/" + id, null).path("data");
        assertEquals(1, state.path("revision").asInt());
        assertEquals(calls, recorder.calls().size());
    }

    @Test
    void aFailureAfterBoardWriteRollsBackBoardAndReceiptAndSameKeyCanRetry() throws Exception {
        String id = call("POST", "/agent/boards", create("before-failure")).path("data").path("boardId").asText();
        String constraint = "board_failure_" + Long.toUnsignedString(System.nanoTime());
        var body = Map.of("operationId", "injected-failure", "expectedRevision", 1, "title", "事务新标题", "groups", List.of(), "items", List.of());
        jdbc.execute("ALTER TABLE asset_board_operation ADD CONSTRAINT " + constraint + " CHECK (operationId <> 'injected-failure')");
        try {
            assertEquals(50000, call("PUT", "/agent/boards/" + id, body).path("code").asInt());
            var board = call("GET", "/agent/boards/" + id, null).path("data");
            assertEquals(1, board.path("revision").asInt());
            assertEquals(1, board.path("items").size());
            assertEquals("NOT_RECORDED", call("GET", "/agent/board-operations/injected-failure", null).path("data").path("status").asText());
        } finally { jdbc.execute("ALTER TABLE asset_board_operation DROP CHECK " + constraint); }
        assertEquals(2, call("PUT", "/agent/boards/" + id, body).path("data").path("revision").asInt());
    }

    @Test
    void concurrentRequestsAreDecidedByMySqlAndRevocationWinsBeforeSaveCommit() throws Exception {
        var create = create("parallel-create");
        try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var a = pool.submit(() -> { start.await(); return call("POST", "/agent/boards", create); });
            var b = pool.submit(() -> { start.await(); return call("POST", "/agent/boards", create); });
            start.countDown();
            var receipt = a.get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(receipt, b.get(15, java.util.concurrent.TimeUnit.SECONDS));
            String id = receipt.path("data").path("boardId").asText();
            var one = Map.of("operationId", "race-one", "expectedRevision", 1, "title", "并发一", "groups", List.of(), "items", List.of());
            var two = Map.of("operationId", "race-two", "expectedRevision", 1, "title", "并发二", "groups", List.of(), "items", List.of());
            var gate = new java.util.concurrent.CountDownLatch(1);
            var f1 = pool.submit(() -> { gate.await(); return call("PUT", "/agent/boards/" + id, one); });
            var f2 = pool.submit(() -> { gate.await(); return call("PUT", "/agent/boards/" + id, two); });
            gate.countDown();
            var results = List.of(f1.get(15, java.util.concurrent.TimeUnit.SECONDS), f2.get(15, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(r -> r.path("code").asInt() == 0).count());
            assertEquals(1, results.stream().filter(r -> "REVISION_CONFLICT".equals(r.path("message").asText())).count());
            assertEquals(2, call("GET", "/agent/boards/" + id, null).path("data").path("revision").asInt());
            try (var connection = jdbc.getDataSource().getConnection()) {
                connection.setAutoCommit(false);
                try (var delete = connection.prepareStatement("DELETE FROM space_user WHERE spaceId=? AND userId=?")) {
                    delete.setLong(1, space); delete.setLong(2, user); delete.executeUpdate();
                }
                var saving = pool.submit(() -> call("POST", "/agent/boards", create("revoked-in-transaction")));
                awaitLockingSql("selectspacerolefromspace_userwherespaceid=" + space + "anduserid=" + user + "forshare");
                assertFalse(saving.isDone(), "Save must still be waiting inside its transaction");
                connection.commit();
                assertEquals(40101, saving.get(15, java.util.concurrent.TimeUnit.SECONDS).path("code").asInt());
                assertEquals("NOT_RECORDED", call("GET", "/agent/board-operations/revoked-in-transaction", null).path("data").path("status").asText());
            }
        }
    }

    @Test
    void notRecordedWhileOriginalIsInFlightAllowsOnlySameKeyRetryAndOneCommit() throws Exception {
        var body = create("in-flight");
        int calls = recorder.calls().size();
        try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
             var connection = jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (var lock = connection.prepareStatement("SELECT id FROM picture WHERE id=? FOR UPDATE")) {
                lock.setLong(1, picture);
                try (var rows = lock.executeQuery()) { assertTrue(rows.next()); }
            }
            var original = pool.submit(() -> call("POST", "/agent/boards", body));
            awaitLockingSql("select*frompicturewhereid=" + picture + "andisdelete=0forupdate");
            assertFalse(original.isDone());
            assertEquals("NOT_RECORDED", call("GET", "/agent/board-operations/in-flight", null).path("data").path("status").asText());
            var retry = pool.submit(() -> call("POST", "/agent/boards", body));
            awaitLockingSql("selectidfromuserwhereid=" + user + "andisdelete=0forupdate");
            assertFalse(retry.isDone());
            connection.commit();
            var receipt = original.get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(0, receipt.path("code").asInt(), receipt.toString());
            assertEquals(receipt, retry.get(15, java.util.concurrent.TimeUnit.SECONDS));
            String id = receipt.path("data").path("boardId").asText();
            assertEquals(1, call("GET", "/agent/boards/" + id, null).path("data").path("revision").asInt());
            assertEquals(calls, recorder.calls().size());
        }
    }

    private void awaitLockingSql(String expected) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            boolean waiting = jdbc.queryForList("SHOW FULL PROCESSLIST").stream().map(row -> row.get("Info"))
                    .filter(java.util.Objects::nonNull).map(Object::toString)
                    .map(sql -> sql.replace("`", "").replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT))
                    .anyMatch(expected::equals);
            if (waiting) return;
            Thread.sleep(50);
        }
        fail("The HTTP save did not reach the expected database row-lock barrier");
    }

    @Test
    void invalidOrStaleReferencesRejectTheWholeSaveIncludingDeselectedItems() throws Exception {
        int calls = recorder.calls().size();
        var request = new java.util.HashMap<String, Object>(create("duplicate"));
        var first = new java.util.HashMap<String, Object>(json.convertValue(result.path("items").get(0), Map.class));
        var entry = Map.of("position", 0, "sourcePosition", 0, "sourceVersion", first.get("sourceVersion"), "selected", true);
        request.put("items", List.of(entry, Map.of("position", 1, "sourcePosition", 0, "sourceVersion", first.get("sourceVersion"), "selected", false)));
        assertEquals("INVALID_REQUEST", call("POST", "/agent/boards", request).path("message").asText());
        assertEquals("NOT_RECORDED", call("GET", "/agent/board-operations/duplicate", null).path("data").path("status").asText());
        String id = call("POST", "/agent/boards", create("good")).path("data").path("boardId").asText();
        var board = call("GET", "/agent/boards/" + id, null).path("data");
        var old = board.path("items").get(0);
        jdbc.update("UPDATE picture SET picWidth=2000 WHERE id=?", picture);
        var replace = Map.of("operationId", "stale-ref", "expectedRevision", 1, "title", "不应保存", "groups", List.of(),
                "items", List.of(Map.of("boardItemId", old.path("boardItemId").asText(), "sourceVersion", old.path("sourceVersion").asText(), "position", 0, "selected", false)));
        assertEquals("REFERENCE_STALE", call("PUT", "/agent/boards/" + id, replace).path("message").asText());
        assertEquals(1, call("GET", "/agent/boards/" + id, null).path("data").path("revision").asInt());
        assertEquals("NOT_RECORDED", call("GET", "/agent/board-operations/stale-ref", null).path("data").path("status").asText());
        assertEquals(0, call("PUT", "/agent/boards/" + id, Map.of("operationId", "remove-bad", "expectedRevision", 1, "title", "空板", "groups", List.of(), "items", List.of())).path("code").asInt());
        assertEquals(calls, recorder.calls().size(), "Rejected references and explicit removal must not touch storage/models/tools");
    }

    @Test
    void boardPreviewAndOperationReceiptRespectCurrentPermissionsAndSessionDeletion() throws Exception {
        jdbc.update("INSERT INTO picture_file (ownerUserId, spaceId, pictureId, purpose, kind, platform, objectKey, url, fileSize, status) VALUES (?, ?, ?, 'gallery', 'original', ?, '/board/original.jpg', 'https://acceptance.invalid/board.jpg', 10, 1)", user, space, picture, RecordingStorageConfiguration.PLATFORM);
        String thread = "board-thread-" + UUID.randomUUID();
        jdbc.update("INSERT INTO agent_session (sessionId, userId, title) VALUES (?, ?, 'thread')", thread, user);
        result = call("POST", "/agent/asset-search", Map.of("spaceId", String.valueOf(space), "threadId", thread, "criteria", Map.of("orientation", "LANDSCAPE"))).path("data");
        String id = call("POST", "/agent/boards", create("preview")).path("data").path("boardId").asText();
        jdbc.update("UPDATE agent_session SET isDelete=1 WHERE sessionId=?", thread);
        var board = call("GET", "/agent/boards/" + id, null).path("data");
        String ref = board.path("items").get(0).path("display").path("contentRef").asText();
        var content = browser.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + ref)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals("acceptance-image-bytes", content.body());
        assertTrue(content.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        assertEquals(0, call("PUT", "/agent/boards/" + id, Map.of("operationId", "session-independent", "expectedRevision", 1, "title", "会话已删除仍可保存", "groups", List.of(), "items", List.of(Map.of("boardItemId", board.path("items").get(0).path("boardItemId").asText(), "sourceVersion", board.path("items").get(0).path("sourceVersion").asText(), "position", 0, "selected", true)))).path("code").asInt());
        int calls = recorder.calls().size();
        jdbc.update("DELETE FROM space_user WHERE spaceId=? AND userId=?", space, user);
        assertEquals(40101, call("GET", "/agent/boards/" + id, null).path("code").asInt());
        var receipt = call("GET", "/agent/board-operations/preview", null).path("data");
        assertEquals(json.readTree("{\"schemaVersion\":1,\"operationId\":\"preview\",\"status\":\"COMMITTED\"}"), receipt);
        assertEquals(receipt, call("POST", "/agent/boards", create("preview")).path("data"));
        var denied = browser.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + ref)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, denied.statusCode());
        assertEquals(calls, recorder.calls().size());
    }

    @Test
    void operationKeysCannotCrossTargetsTypesOrProposalReservations() throws Exception {
        var original = create("scoped-key");
        var receipt = call("POST", "/agent/boards", original).path("data");
        String id = receipt.path("boardId").asText();
        var changed = new java.util.HashMap<>(original); changed.put("title", "不同请求");
        assertEquals("OPERATION_KEY_CONFLICT", call("POST", "/agent/boards", changed).path("message").asText());
        var replace = Map.of("operationId", "scoped-key", "expectedRevision", 1, "title", "复用创建标识", "groups", List.of(), "items", List.of());
        assertEquals("OPERATION_KEY_CONFLICT", call("PUT", "/agent/boards/" + id, replace).path("message").asText());
        String another = call("POST", "/agent/boards", create("second-board")).path("data").path("boardId").asText();
        var firstReplace = Map.of("operationId", "replace-key", "expectedRevision", 1, "title", "替换", "groups", List.of(), "items", List.of());
        assertEquals(0, call("PUT", "/agent/boards/" + id, firstReplace).path("code").asInt());
        assertEquals("OPERATION_KEY_CONFLICT", call("PUT", "/agent/boards/" + another, firstReplace).path("message").asText());
        jdbc.update("INSERT INTO asset_board_operation (ownerId, operationId, operationType, boardId, requestDigest, proposalId) VALUES (?, 'reserved', 'PROPOSAL', ?, ?, 'future-proposal')", user, id, "0".repeat(64));
        assertEquals("OPERATION_KEY_CONFLICT", call("POST", "/agent/boards", create("reserved")).path("message").asText());
        assertEquals(1, call("GET", "/agent/boards/" + another, null).path("data").path("revision").asInt());
    }

    @Test
    void isolatedEntriesHidePrivateTextButCanBeExplicitlyRemovedAndRolesRemainPersonal() throws Exception {
        var body = new java.util.HashMap<>(create("quarantine"));
        body.put("title", "私人板标题");
        body.put("groups", List.of(Map.of("groupId", "private-group", "position", 0, "name", "私人组名")));
        var entry = new java.util.HashMap<String, Object>();
        entry.put("position", 0); entry.put("sourcePosition", 0); entry.put("sourceVersion", result.path("items").get(0).path("sourceVersion").asText()); entry.put("selected", true); entry.put("groupId", "private-group");
        body.put("items", List.of(entry));
        String id = call("POST", "/agent/boards", body).path("data").path("boardId").asText();
        jdbc.update("INSERT INTO resource_isolation (resourceType, resourceId, status, activeKey, operatorId, reason) VALUES ('picture', ?, 1, ?, ?, 'fixture')", picture, "picture:" + picture, user);
        var hidden = call("GET", "/agent/boards/" + id, null).path("data");
        assertFalse(hidden.toString().contains("私人"));
        assertFalse(hidden.toString().contains(String.valueOf(picture)));
        assertEquals(3, hidden.path("items").get(0).size());
        assertEquals("UNAVAILABLE", hidden.path("items").get(0).path("status").asText());
        assertEquals(0, call("PUT", "/agent/boards/" + id, Map.of("operationId", "remove-isolated", "expectedRevision", 1, "title", "已移除", "groups", List.of(), "items", List.of())).path("code").asInt());
        for (String role : List.of("editor", "viewer")) {
            jdbc.update("UPDATE space_user SET spaceRole=? WHERE spaceId=? AND userId=?", role, space, user);
            int revision = call("GET", "/agent/boards/" + id, null).path("data").path("revision").asInt();
            assertEquals(0, call("PUT", "/agent/boards/" + id, Map.of("operationId", "role-" + role, "expectedRevision", revision, "title", "我的板", "groups", List.of(), "items", List.of())).path("code").asInt());
        }
    }

    @Test
    void logoutAndCsrfRejectionsCannotReadOrWriteUsingAnOldSession() throws Exception {
        String id = call("POST", "/agent/boards", create("before-logout")).path("data").path("boardId").asText();
        int calls = recorder.calls().size();
        var missingCsrf = browser.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/agent/boards"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(create("no-csrf")))).build(), HttpResponse.BodyHandlers.ofString());
        assertNotEquals(0, json.readTree(missingCsrf.body()).path("code").asInt());
        assertEquals("NOT_RECORDED", call("GET", "/agent/board-operations/no-csrf", null).path("data").path("status").asText());
        var manager = (CookieManager) browser.cookieHandler().orElseThrow();
        String cookie = manager.getCookieStore().getCookies().stream().map(c -> c.getName() + "=" + c.getValue()).collect(java.util.stream.Collectors.joining("; "));
        assertEquals(0, call("POST", "/user/logout", Map.of()).path("code").asInt());
        try (var replay = HttpClient.newHttpClient()) {
            for (String path : List.of("/agent/boards/" + id, "/agent/board-operations/before-logout")) {
                var response = replay.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path)).header("Cookie", cookie).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(40100, json.readTree(response.body()).path("code").asInt());
                assertFalse(response.body().contains(id));
            }
        }
        assertEquals(calls, recorder.calls().size());
    }
}
