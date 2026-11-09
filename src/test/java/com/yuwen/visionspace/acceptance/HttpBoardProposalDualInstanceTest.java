package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@IsolatedSpringBootTest
class HttpBoardProposalDualInstanceTest extends BoardProposalHttpFixture {
    @Test void twoInstancesArbitrateDecisionsSupersessionAndRestartWithoutCheckpointReplay() throws Exception {
        Path logs=Path.of("target/proposal-dual").toAbsolutePath(); Files.createDirectories(logs);
        JsonNode committed, pending;
        int calls=recorder.calls().size();
        try(var second=start(logs,false)) {
            var creations=race(()->call("POST","/agent/board-proposals",proposal("dual")),
                    ()->callAt(second.port,"POST","/agent/board-proposals",proposal("dual")));
            committed=creations.get(0).path("data");
            assertEquals(0,creations.get(0).path("code").asInt(),creations.toString());
            assertEquals(committed.path("operationId"),creations.get(1).path("data").path("operationId"));
            String path="/agent/board-proposals/"+committed.path("proposalId").asText()+"/decision";
            var decision=Map.of("operationId",committed.path("operationId").asText(),"proposalRevision",1,"decision","CONFIRM");
            var receipts=race(()->call("POST",path,decision),()->callAt(second.port,"POST",path,decision));
            for(var receipt:receipts) assertEquals("COMMITTED",receipt.path("data").path("status").asText(),receipts.toString());
            assertEquals(2,readBoard().path("revision").asInt());
            var next=new HashMap<>(proposal("next"));next.put("expectedRevision",2);
            pending=call("POST","/agent/board-proposals",next).path("data");
            String p="/agent/board-proposals/"+pending.path("proposalId").asText()+"/decision";
            var confirm=Map.of("operationId",pending.path("operationId").asText(),"proposalRevision",1,"decision","CONFIRM");
            var cancel=Map.of("operationId",pending.path("operationId").asText(),"proposalRevision",1,"decision","CANCEL");
            var competed=race(()->call("POST",p,confirm),()->callAt(second.port,"POST",p,cancel));
            String state=competed.get(0).path("data").path("status").asText();
            assertTrue(Set.of("COMMITTED","CANCELLED").contains(state),competed.toString());
            assertEquals(state,competed.get(1).path("data").path("status").asText());
            assertEquals(state.equals("COMMITTED")?3:2,readBoard().path("revision").asInt());
            var another=new HashMap<>(proposal("rollback-pending"));another.put("expectedRevision",readBoard().path("revision").asInt());
            pending=call("POST","/agent/board-proposals",another).path("data");
        }
        try(var restarted=start(logs,true)) {
            var state=callAt(restarted.port,"GET","/agent/tasks/"+committed.path("taskId").asText(),null).path("data");
            assertEquals("COMMITTED",state.path("status").asText());assertEquals(2,state.path("revision").asInt());
            assertEquals("COMMITTED",decide(committed,"CONFIRM").path("data").path("status").asText());
            String path="/agent/board-proposals/"+pending.path("proposalId").asText()+"/decision";
            var confirm=Map.of("operationId",pending.path("operationId").asText(),"proposalRevision",1,"decision","CONFIRM");
            assertEquals("INVALID_OPERATION_STATE",callAt(restarted.port,"POST",path,confirm).path("message").asText());
            assertEquals("CANCELLED",callAt(restarted.port,"POST",path,Map.of("operationId",pending.path("operationId").asText(),"proposalRevision",1,"decision","CANCEL")).path("data").path("status").asText());
            var fresh=new HashMap<>(proposal("blocked-new"));fresh.put("expectedRevision",readBoard().path("revision").asInt());
            assertEquals("INVALID_OPERATION_STATE",callAt(restarted.port,"POST","/agent/board-proposals",fresh).path("message").asText());
            jdbc.update("UPDATE agent_session SET checkpointData='stale-backup' WHERE sessionId=?",thread);
            assertEquals("CANCELLED",callAt(restarted.port,"GET","/agent/tasks/"+pending.path("taskId").asText(),null).path("data").path("status").asText());
            jdbc.update("UPDATE agent_session SET isDelete=1 WHERE sessionId=?",thread);
            assertEquals(40101,callAt(restarted.port,"GET","/agent/tasks/"+committed.path("taskId").asText(),null).path("code").asInt());
            assertEquals(40101,decide(pending,"CANCEL").path("code").asInt());
            assertEquals(0,callAt(restarted.port,"GET","/agent/boards/"+board,null).path("code").asInt());
        }
        assertEquals(calls,recorder.calls().size());
    }
    private List<JsonNode> race(Callable<JsonNode> one,Callable<JsonNode> two) throws Exception {
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var gate=new CountDownLatch(1);
            var a=pool.submit(()->{gate.await();return one.call();});var b=pool.submit(()->{gate.await();return two.call();});gate.countDown();
            return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
        }
    }
    private Instance start(Path logs,boolean rollback) throws Exception {
        Path ready=logs.resolve(UUID.randomUUID()+".port"); Path log=logs.resolve(ready.getFileName()+".log");
        String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?"java.exe":"java").toString();
        Process process=new ProcessBuilder(java,"-cp",System.getProperty("surefire.test.class.path"),VipSecondInstanceMain.class.getName(),ready.toString(),
                "--vs.instance-id-file="+logs.resolve(UUID.randomUUID()+".instance"),"--vision-space.board-proposal.writes-enabled="+!rollback)
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);
        while(!Files.exists(ready)&&process.isAlive()&&System.nanoTime()<deadline) Thread.sleep(100);
        if(!Files.exists(ready)){process.destroyForcibly();fail("Second instance not ready: "+log);}
        return new Instance(process,ready,Integer.parseInt(Files.readString(ready).strip()));
    }
    private record Instance(Process process,Path ready,int port) implements AutoCloseable {
        public void close() throws Exception {Files.writeString(Path.of(ready+".stop"),"stop");if(!process.waitFor(25,TimeUnit.SECONDS)){process.destroyForcibly();assertTrue(process.waitFor(10,TimeUnit.SECONDS));}}
    }
}
