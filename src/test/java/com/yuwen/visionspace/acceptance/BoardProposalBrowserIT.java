package com.yuwen.visionspace.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@IsolatedSpringBootTest
@Import(BoardProposalBrowserIT.PlannerFixture.class)
class BoardProposalBrowserIT extends BoardProposalHttpFixture {
    String objectKey;
    @TestConfiguration(proxyBeanMethods=false)
    static class PlannerFixture {
        @Bean @Primary ChatModel browserPlanner(ExternalCallRecorder recorder) {
            return prompt -> {
                var options=(ToolCallingChatOptions)prompt.getOptions();
                assertEquals(Boolean.FALSE,options.getInternalToolExecutionEnabled());assertTrue(options.getToolCallbacks().isEmpty());
                recorder.record(new ExternalCallRecorder.Call("MODEL","/controlled/browser-board-planner","called"));
                try {
                    var input=new com.fasterxml.jackson.databind.ObjectMapper().readTree(prompt.getInstructions().getLast().getText());
                    var items=input.path("items").deepCopy();
                    for(var i:items) {var n=(com.fasterxml.jackson.databind.node.ObjectNode)i;n.put("selected",false);n.put("groupId","g1");}
                    var output=Map.of("groups",List.of(Map.of("groupId","g1","position",0,"name","<b>Agent精选</b>")),"items",items);
                    return new ChatResponse(List.of(new Generation(new AssistantMessage(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(output)))));
                } catch(java.io.IOException e) {throw new IllegalStateException(e);}
            };
        }
    }
    @Override void seedContent() {
        objectKey="/proposal-browser/"+picture+".png";
        jdbc.update("UPDATE user SET userRole='moderator' WHERE id=?",user);
        jdbc.update("INSERT INTO picture_file (ownerUserId,spaceId,pictureId,purpose,kind,platform,objectKey,url,fileSize,status) VALUES (?,?,?,'gallery','original',?,?,'https://acceptance.invalid/proposal.png',10,1)",user+1,space,picture,RecordingStorageConfiguration.PLATFORM,objectKey);
        RecordingStorageConfiguration.SEEDED_CONTENT.put(objectKey,Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="));
    }
    @Test void browserReviewsDecidesAndRecoversLostResponseAndRevokedReferences() throws Exception {
        Path artifacts=Path.of("target/proposal-browser").toAbsolutePath();Files.createDirectories(artifacts);
        Path log=artifacts.resolve("browser.log");int calls=recorder.calls().size();
        Process process=new ProcessBuilder("node","frontend/e2e/board-proposal.mjs",String.valueOf(port),account,board,String.valueOf(picture),artifacts.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(120,TimeUnit.SECONDS),"browser timed out: "+log);
            assertEquals(0,process.exitValue(),Files.readString(log));
            var recorded=recorder.calls().subList(calls,recorder.calls().size());
            assertTrue(recorded.stream().anyMatch(c->c.method().equals("MODEL")));
            assertTrue(recorded.stream().allMatch(c->Set.of("READ","MODEL").contains(c.method())),"No external tool or storage mutation is permitted");
            assertEquals(10L,jdbc.queryForObject("SELECT totalSize FROM space WHERE id=?",Long.class,space));
        } finally {process.destroyForcibly();RecordingStorageConfiguration.SEEDED_CONTENT.remove(objectKey);}
    }
}
