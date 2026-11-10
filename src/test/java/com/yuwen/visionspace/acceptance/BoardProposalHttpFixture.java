package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

abstract class BoardProposalHttpFixture {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ExternalCallRecorder recorder;
    HttpClient browser;
    long user, space, picture;
    String thread, board, account;
    JsonNode item;

    @BeforeEach void fixture() throws Exception {
        browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        account = "proposal_" + UUID.randomUUID().toString().replace("-", "");
        user = call("POST", "/user/register", Map.of("userAccount", account, "userPassword", "acceptance-password", "checkPassword", "acceptance-password")).path("data").asLong();
        assertEquals(0, call("POST", "/user/login", Map.of("userAccount", account, "userPassword", "acceptance-password")).path("code").asInt());
        space = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        picture = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        thread = "proposal-" + UUID.randomUUID();
        jdbc.update("INSERT INTO agent_session (sessionId, userId, title) VALUES (?, ?, 'proposal')", thread, user);
        jdbc.update("INSERT INTO space (id, userId, spaceType, spaceName, totalSize, totalCount) VALUES (?, ?, 1, 'proposal-team', 10, 1)", space, user + 1);
        jdbc.update("INSERT INTO space_user (spaceId, userId, spaceRole) VALUES (?, ?, 'viewer')", space, user);
        jdbc.update("INSERT INTO picture (id, userId, spaceId, name, url, picWidth, picHeight, picSize) VALUES (?, ?, ?, 'gallery', 'https://origin.invalid/proposal.jpg', 1920, 1080, 10)", picture, user + 1, space);
        seedContent();
        var searchResponse = call("POST", "/agent/asset-search", Map.of("spaceId", String.valueOf(space), "criteria", Map.of("orientation", "LANDSCAPE")));
        assertEquals(0, searchResponse.path("code").asInt(), searchResponse.toString());
        var result = searchResponse.path("data");
        assertEquals(1, result.path("items").size(), searchResponse.toString());
        var created = call("POST", "/agent/boards", Map.of("operationId", "initial", "resultId", result.path("resultId").asText(), "title", "个人草稿", "groups", List.of(),
                "items", List.of(Map.of("position", 0, "sourcePosition", 0, "sourceVersion", result.path("items").get(0).path("sourceVersion").asText(), "selected", true))));
        assertEquals(0, created.path("code").asInt(), created.toString());
        board = created.path("data").path("boardId").asText();
        item = readBoard().path("items").get(0);
    }
    @AfterEach void close() { browser.close(); }
    void seedContent() { }

    Map<String,Object> proposal(String key) {
        return Map.of("proposalRequestId", key, "threadId", thread, "boardId", board, "expectedRevision", 1,
                "title", "个人草稿", "groups", List.of(Map.of("groupId", "g1", "position", 0, "name", "精选")),
                "items", List.of(Map.of("boardItemId", item.path("boardItemId").asText(), "sourceVersion", item.path("sourceVersion").asText(), "position", 0, "selected", false, "groupId", "g1")));
    }
    JsonNode decide(JsonNode task, String decision) throws Exception {
        return call("POST", "/agent/board-proposals/" + task.path("proposalId").asText() + "/decision",
                Map.of("operationId", task.path("operationId").asText(), "proposalRevision", 1, "decision", decision));
    }
    JsonNode readBoard() throws Exception { return call("GET", "/agent/boards/" + board, null).path("data"); }
    JsonNode call(String method, String path, Object body) throws Exception { return callAt(port, method, path, body); }
    JsonNode callAt(int servicePort, String method, String path, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + servicePort + "/api" + path));
        if (body == null) request.GET();
        else request.header("Content-Type", "application/json").header(CsrfToken.HEADER, CsrfToken.of(browser))
                .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return json.readTree(browser.send(request.build(), HttpResponse.BodyHandlers.ofString()).body());
    }
}
