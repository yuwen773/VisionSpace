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

@IsolatedSpringBootTest
class HttpBoardProposalTest extends BoardProposalHttpFixture {
    @Test void proposalOnlySavesAfterExplicitDecisionAndRetryReturnsOriginalReceipt() throws Exception {
        int calls = recorder.calls().size();
        var response = call("POST", "/agent/board-proposals", proposal("request-one"));
        assertEquals(0, response.path("code").asInt(), response.toString());
        var task = response.path("data");
        assertEquals("AWAITING_CONFIRMATION", task.path("status").asText());
        assertEquals(1, task.path("proposalRevision").asInt());
        assertEquals(1, readBoard().path("revision").asInt());
        assertEquals("个人草稿", readBoard().path("title").asText());
        assertEquals("NOT_RECORDED", call("GET", "/agent/board-operations/" + task.path("operationId").asText(), null).path("data").path("status").asText());
        var committed = decide(task, "CONFIRM").path("data");
        assertEquals("COMMITTED", committed.path("status").asText());
        assertEquals(2, committed.path("revision").asInt());
        assertEquals("COMMITTED", decide(task, "CONFIRM").path("data").path("status").asText());
        assertEquals(2, readBoard().path("revision").asInt());
        assertFalse(readBoard().path("items").get(0).path("selected").asBoolean());
        assertEquals(10L, jdbc.queryForObject("SELECT totalSize FROM space WHERE id=?", Long.class, space));
        assertEquals(calls, recorder.calls().size(), "No model, tool or storage execution at proposal/decision interfaces");
    }

    @Test void replacedContentIsASourceConflictAndCannotBeSavedUnderOldEvidence() throws Exception {
        var task = call("POST", "/agent/board-proposals", proposal("source-change")).path("data");
        int calls = recorder.calls().size();
        jdbc.update("UPDATE picture SET url='https://origin.invalid/replacement.jpg' WHERE id=?", picture);
        assertEquals("CONFLICT", decide(task, "CONFIRM").path("data").path("status").asText());
        assertEquals(1, readBoard().path("revision").asInt());
        assertEquals("NOT_RECORDED", call("GET", "/agent/board-operations/" + task.path("operationId").asText(), null).path("data").path("status").asText());
        assertEquals(calls, recorder.calls().size());
    }

    @Test void metadataChangesKeepHistoricalEvidenceButDisableConfirmationUntilANewProposal() throws Exception {
        var task=call("POST","/agent/board-proposals",proposal("metadata-change")).path("data");
        jdbc.update("UPDATE picture SET picWidth=2000 WHERE id=?",picture);
        var stale=readTask(task);
        assertTrue(stale.has("target"));
        assertEquals("STALE",stale.path("target").path("items").get(0).path("status").asText());
        assertFalse(stale.path("canConfirm").asBoolean());
        assertEquals("CONFLICT",decide(task,"CONFIRM").path("data").path("status").asText());
        assertEquals(1,readBoard().path("revision").asInt());
    }

    @Test void anotherBoardSaveHidesOldProposalDifferencesButLeavesStatusAndCancellation() throws Exception {
        var task=call("POST","/agent/board-proposals",proposal("board-changed")).path("data");
        assertEquals(0,call("PUT","/agent/boards/"+board,Map.of("operationId","other-save","expectedRevision",1,"title","新版本","groups",List.of(),"items",List.of())).path("code").asInt());
        var state=readTask(task);
        assertEquals("AWAITING_CONFIRMATION",state.path("status").asText());
        for(String field:List.of("current","target","boardId","spaceId","expectedRevision")) assertFalse(state.has(field),state.toString());
        assertEquals("CANCELLED",decide(task,"CANCEL").path("data").path("status").asText());
        assertEquals(2,readBoard().path("revision").asInt());
    }

    @Test void laterBoardChangesDoNotRewriteCommittedStateOrItsOperationReceipt() throws Exception {
        var task=call("POST","/agent/board-proposals",proposal("committed-then-changed")).path("data");
        assertEquals("COMMITTED",decide(task,"CONFIRM").path("data").path("status").asText());
        assertEquals(0,call("PUT","/agent/boards/"+board,Map.of("operationId","after-commit","expectedRevision",2,"title","更新板","groups",List.of(),"items",List.of())).path("code").asInt());
        var state=readTask(task);assertEquals("COMMITTED",state.path("status").asText());assertFalse(state.has("target"));assertFalse(state.has("current"));
        assertEquals("COMMITTED",decide(task,"CONFIRM").path("data").path("status").asText());
        assertEquals(2,call("GET","/agent/board-operations/"+task.path("operationId").asText(),null).path("data").path("revision").asInt());
        assertEquals(3,readBoard().path("revision").asInt());
    }

    @Test void taskPreviewUsesCurrentSessionAndBoardVersionAndRejectsWithoutOutboundReads() throws Exception {
        var task=call("POST","/agent/board-proposals",proposal("preview-authorization")).path("data");
        String path="/api/agent/tasks/"+task.path("taskId").asText()+"/items/"+item.path("boardItemId").asText()+"/content";
        int calls=recorder.calls().size();
        jdbc.update("DELETE FROM space_user WHERE spaceId=? AND userId=?",space,user);
        assertEquals(403,browser.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
        jdbc.update("INSERT INTO space_user (spaceId,userId,spaceRole) VALUES (?,?,'viewer')",space,user);
        assertEquals(0,call("PUT","/agent/boards/"+board,Map.of("operationId","preview-new-version","expectedRevision",1,"title","新板","groups",List.of(),"items",List.of())).path("code").asInt());
        assertEquals(403,browser.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
        jdbc.update("UPDATE agent_session SET isDelete=1 WHERE sessionId=?",thread);
        assertEquals(403,browser.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(calls,recorder.calls().size());
    }

    @Test void stableRequestsAreImmutableAndNewTargetsSupersedeOnlyPendingProposals() throws Exception {
        int calls = recorder.calls().size();
        var a=call("POST","/agent/board-proposals",proposal("a")).path("data");
        var retry=call("POST","/agent/board-proposals",proposal("a")).path("data");
        assertEquals(a.path("taskId"),retry.path("taskId"));
        assertEquals(a.path("operationId"),retry.path("operationId"));
        var changed=new HashMap<>(proposal("a")); changed.put("items",List.of());
        assertEquals("OPERATION_KEY_CONFLICT",call("POST","/agent/board-proposals",changed).path("message").asText());
        assertEquals("AWAITING_CONFIRMATION",readTask(a).path("status").asText());
        var b=call("POST","/agent/board-proposals",proposal("b")).path("data");
        assertEquals("PROPOSAL_SUPERSEDED",readTask(a).path("reasonCode").asText());
        assertEquals("CONFLICT",decide(a,"CONFIRM").path("data").path("status").asText());
        assertEquals("CANCELLED",decide(b,"CANCEL").path("data").path("status").asText());
        assertEquals("CANCELLED",decide(b,"CANCEL").path("data").path("status").asText());
        var c=call("POST","/agent/board-proposals",proposal("c")).path("data");
        assertEquals("CANCELLED",readTask(b).path("status").asText());
        assertEquals("AWAITING_CONFIRMATION",readTask(c).path("status").asText());
        assertEquals(1,readBoard().path("revision").asInt());
        assertEquals(calls,recorder.calls().size());
    }

    @Test void proposalCannotExtendTheAllowedActionsToRenameTheBoard() throws Exception {
        var changed=new HashMap<>(proposal("rename"));changed.put("title","模型试图更改标题");
        int calls=recorder.calls().size();
        assertEquals(40000,call("POST","/agent/board-proposals",changed).path("code").asInt());
        assertEquals("个人草稿",readBoard().path("title").asText());
        assertEquals(1,readBoard().path("revision").asInt());assertEquals(calls,recorder.calls().size());
    }

    @Test void notificationsReferToCommittedFactsWithNumericVersionsAndHidePrivateTargetsAfterRevocation() throws Exception {
        var task=call("POST","/agent/board-proposals",proposal("events")).path("data");
        int calls=recorder.calls().size();
        String events=events(task);
        var notice=json.readTree(events.lines().filter(l->l.startsWith("data:")).findFirst().orElseThrow().substring(5));
        assertEquals("CONFIRMATION_REQUIRED",notice.path("type").asText());
        assertTrue(notice.path("payload").path("expectedRevision").isIntegralNumber());
        assertFalse(events.contains("个人草稿"));assertFalse(events.contains("精选"));assertFalse(events.contains("sourceVersion"));
        assertEquals("COMMITTED",decide(task,"CONFIRM").path("data").path("status").asText());
        String committed=events(task);assertTrue(committed.contains("OPERATION_COMMITTED"));assertTrue(committed.contains("BOARD_UPDATED"));
        jdbc.update("DELETE FROM space_user WHERE spaceId=? AND userId=?",space,user);
        String denied=events(task);assertFalse(denied.contains(board));assertFalse(denied.contains("OPERATION_COMMITTED"));
        assertEquals(calls,recorder.calls().size());
    }
    private String events(JsonNode task) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/agent/tasks/"+task.path("taskId").asText()+"/events"))
                .header("X-VS-AI-Event-Version","1").header("Accept","text/event-stream").GET().build(),HttpResponse.BodyHandlers.ofString()).body();
    }

    @Test void invalidBindingsAndDirectSaveCannotConsumeReservedOperationAndDoNotInvalidateTask() throws Exception {
        var task=call("POST","/agent/board-proposals",proposal("binding")).path("data");
        int calls=recorder.calls().size();
        var before=readBoard();
        for (var body : List.of(Map.of("operationId","forged","proposalRevision",1,"decision","CONFIRM"),
                Map.of("operationId",task.path("operationId").asText(),"proposalRevision",2,"decision","CANCEL"),
                Map.of("operationId",task.path("operationId").asText(),"proposalRevision",1,"decision","CONFIRM","ownerId",user))) {
            assertEquals(40000,call("POST","/agent/board-proposals/"+task.path("proposalId").asText()+"/decision",body).path("code").asInt());
        }
        var direct=new HashMap<>(proposal("binding")); direct.remove("proposalRequestId");direct.remove("threadId");direct.remove("boardId");
        direct.put("operationId",task.path("operationId").asText());
        assertEquals("OPERATION_KEY_CONFLICT",call("PUT","/agent/boards/"+board,direct).path("message").asText());
        assertEquals("AWAITING_CONFIRMATION",readTask(task).path("status").asText());
        assertEquals(before,readBoard());
        assertEquals(calls,recorder.calls().size());
    }

    @Test void revokedOwnerGetsOnlyMinimalStatusAndCanCancelButCannotConfirm() throws Exception {
        var task=call("POST","/agent/board-proposals",proposal("revoked")).path("data");
        int calls=recorder.calls().size();
        jdbc.update("DELETE FROM space_user WHERE spaceId=? AND userId=?",space,user);
        var minimal=readTask(task);
        for(String key:List.of("boardId","spaceId","target","current","expectedRevision","revision")) assertFalse(minimal.has(key),minimal.toString());
        assertFalse(minimal.path("canConfirm").asBoolean());
        assertEquals("CANCELLED",decide(task,"CANCEL").path("data").path("status").asText());
        assertEquals("INVALID_OPERATION_STATE",decide(task,"CONFIRM").path("data").path("reasonCode").asText());
        jdbc.update("INSERT INTO space_user (spaceId,userId,spaceRole) VALUES (?,?,'viewer')",space,user);
        assertEquals(1,readBoard().path("revision").asInt());
        var another=call("POST","/agent/board-proposals",proposal("reject")).path("data");
        jdbc.update("DELETE FROM space_user WHERE spaceId=? AND userId=?",space,user);
        assertEquals("REJECTED",decide(another,"CONFIRM").path("data").path("status").asText());
        assertEquals(calls,recorder.calls().size());
    }

    @Test void deletedReferencesHideDifferencesAndHistoricalCommitCannotBeRewritten() throws Exception {
        var task=call("POST","/agent/board-proposals",proposal("deleted")).path("data");
        int calls=recorder.calls().size();
        assertEquals("COMMITTED",decide(task,"CONFIRM").path("data").path("status").asText());
        jdbc.update("UPDATE picture SET isDelete=1 WHERE id=?",picture);
        var state=readTask(task);
        assertEquals("COMMITTED",state.path("status").asText());
        assertFalse(state.has("target")); assertFalse(state.has("current"));
        assertEquals("COMMITTED",decide(task,"CONFIRM").path("data").path("status").asText());
        assertEquals("INVALID_OPERATION_STATE",decide(task,"CANCEL").path("data").path("reasonCode").asText());
        assertEquals(2,readBoard().path("revision").asInt());
        assertEquals(calls,recorder.calls().size());
    }

    @Test void unavailablePendingReferenceRejectsConfirmationWithoutLeakingOrSaving() throws Exception {
        var task=call("POST","/agent/board-proposals",proposal("pending-delete")).path("data");
        int calls=recorder.calls().size();
        jdbc.update("UPDATE picture SET isDelete=1 WHERE id=?",picture);
        assertFalse(readTask(task).has("target"));
        var rejected=decide(task,"CONFIRM").path("data");
        assertEquals("REJECTED",rejected.path("status").asText());
        for(String key:List.of("current","target","boardId","spaceId")) assertFalse(rejected.has(key),rejected.toString());
        assertEquals(1,readBoard().path("revision").asInt());
        assertEquals(calls,recorder.calls().size());
    }

    @Test void failureAtTaskCommitRollsBackSharedBoardSaveAndReceiptAndRemainsRetryable() throws Exception {
        var task=call("POST","/agent/board-proposals",proposal("rollback")).path("data");
        int calls=recorder.calls().size();
        String constraint="proposal_failure_"+Long.toUnsignedString(System.nanoTime());
        jdbc.execute("ALTER TABLE asset_board_proposal ADD CONSTRAINT "+constraint+" CHECK (proposalId <> '"+task.path("proposalId").asText()+"' OR status <> 'COMMITTED')");
        try {
            assertEquals(50000,decide(task,"CONFIRM").path("code").asInt());
            assertEquals(1,readBoard().path("revision").asInt());
            assertEquals("AWAITING_CONFIRMATION",readTask(task).path("status").asText());
            assertEquals("NOT_RECORDED",call("GET","/agent/board-operations/"+task.path("operationId").asText(),null).path("data").path("status").asText());
        } finally {jdbc.execute("ALTER TABLE asset_board_proposal DROP CHECK "+constraint);}
        assertEquals("COMMITTED",decide(task,"CONFIRM").path("data").path("status").asText());
        assertEquals(2,readBoard().path("revision").asInt());
        assertEquals(calls,recorder.calls().size());
    }

    @Test void foreignAdminCsrfAndLoggedOutSessionsCannotReadDecideOrPlan() throws Exception {
        var task=call("POST","/agent/board-proposals",proposal("security")).path("data");
        int calls=recorder.calls().size();
        String decisionPath="/agent/board-proposals/"+task.path("proposalId").asText()+"/decision";
        for(String path:List.of("/agent/board-proposals",decisionPath,"/agent/board-proposals/plan")) {
            var denied=browser.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path))
                    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(403,denied.statusCode());
        }
        var original=browser;
        try(var stranger=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build()) {
            browser=stranger;
            assertEquals(40100,call("GET","/agent/tasks/"+task.path("taskId").asText(),null).path("code").asInt());
            String account="outsider_"+System.nanoTime();
            long outsider=call("POST","/user/register",Map.of("userAccount",account,"userPassword","acceptance-password","checkPassword","acceptance-password")).path("data").asLong();
            call("POST","/user/login",Map.of("userAccount",account,"userPassword","acceptance-password"));
            jdbc.update("UPDATE user SET userRole='admin' WHERE id=?",outsider);
            assertEquals(40101,call("GET","/agent/tasks/"+task.path("taskId").asText(),null).path("code").asInt());
            assertEquals(40101,decide(task,"CANCEL").path("code").asInt());
            assertEquals(40101,call("POST","/agent/board-proposals",proposal("foreign")).path("code").asInt());
            assertEquals(40101,call("POST","/agent/board-proposals/plan",Map.of("proposalRequestId","foreign-plan","threadId",thread,"boardId",board,"expectedRevision",1,"message","save")).path("code").asInt());
        } finally {browser=original;}
        assertEquals("AWAITING_CONFIRMATION",readTask(task).path("status").asText());
        assertEquals(1,readBoard().path("revision").asInt());
        String oldCookie=((CookieManager)browser.cookieHandler().orElseThrow()).getCookieStore().getCookies().stream().map(Object::toString).collect(java.util.stream.Collectors.joining("; "));
        call("POST","/user/logout",Map.of());
        assertEquals(40100,call("GET","/agent/tasks/"+task.path("taskId").asText(),null).path("code").asInt());
        assertEquals(40100,decide(task,"CANCEL").path("code").asInt());
        try(var replay=HttpClient.newHttpClient()) {
            var response=replay.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/agent/tasks/"+task.path("taskId").asText()))
                    .header("Cookie",oldCookie).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(40100,json.readTree(response.body()).path("code").asInt());
        }
        assertEquals(calls,recorder.calls().size());
    }

    @Test void newProposalAndOldConfirmationRaceUsesTheSameBoardVersion() throws Exception {
        var task=call("POST","/agent/board-proposals",proposal("race-old")).path("data");
        int calls=recorder.calls().size();
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var gate=new java.util.concurrent.CountDownLatch(1);
            var newer=pool.submit(()->{gate.await();return call("POST","/agent/board-proposals",proposal("race-new"));});
            var confirm=pool.submit(()->{gate.await();return decide(task,"CONFIRM");});gate.countDown();
            var n=newer.get(20,java.util.concurrent.TimeUnit.SECONDS);var c=confirm.get(20,java.util.concurrent.TimeUnit.SECONDS);
            if("COMMITTED".equals(c.path("data").path("status").asText())) {
                assertEquals("REVISION_CONFLICT",n.path("message").asText());assertEquals(2,readBoard().path("revision").asInt());
            } else {
                assertEquals("CONFLICT",c.path("data").path("status").asText());assertEquals("AWAITING_CONFIRMATION",n.path("data").path("status").asText());
                assertEquals("PROPOSAL_SUPERSEDED",readTask(task).path("reasonCode").asText());assertEquals(1,readBoard().path("revision").asInt());
            }
        }
        assertEquals(calls,recorder.calls().size());
    }

    @Test void inFlightConfirmationAndNotRecordedReadCannotCauseANewSaveOrIdentifier() throws Exception {
        var task=call("POST","/agent/board-proposals",proposal("inflight")).path("data");
        int calls=recorder.calls().size();
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();var connection=jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try(var lock=connection.prepareStatement("SELECT id FROM picture WHERE id=? FOR UPDATE")){lock.setLong(1,picture);try(var rows=lock.executeQuery()){assertTrue(rows.next());}}
            var original=pool.submit(()->decide(task,"CONFIRM"));
            awaitSql("select*frompicturewhereid="+picture+"andisdelete=0forupdate");
            assertFalse(original.isDone());
            assertEquals("AWAITING_CONFIRMATION",readTask(task).path("status").asText());
            assertEquals("NOT_RECORDED",call("GET","/agent/board-operations/"+task.path("operationId").asText(),null).path("data").path("status").asText());
            var retry=pool.submit(()->decide(task,"CONFIRM"));
            awaitSql("selectidfromuserwhereid="+user+"andisdelete=0forupdate");assertFalse(retry.isDone());
            connection.commit();
            assertEquals("COMMITTED",original.get(20,java.util.concurrent.TimeUnit.SECONDS).path("data").path("status").asText());
            assertEquals("COMMITTED",retry.get(20,java.util.concurrent.TimeUnit.SECONDS).path("data").path("status").asText());
        }
        assertEquals(2,readBoard().path("revision").asInt());assertEquals(calls,recorder.calls().size());
    }
    private void awaitSql(String expected) throws Exception {
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<deadline) {
            if(jdbc.queryForList("SHOW FULL PROCESSLIST").stream().map(r->Objects.toString(r.get("Info"),"").replace("`", "").replaceAll("\\s+","").toLowerCase(Locale.ROOT)).anyMatch(expected::equals)) return;
            Thread.sleep(30);
        }
        fail("Expected fixture locking SQL was not observed");
    }
    private JsonNode readTask(JsonNode task) throws Exception {return call("GET","/agent/tasks/"+task.path("taskId").asText(),null).path("data");}
}
