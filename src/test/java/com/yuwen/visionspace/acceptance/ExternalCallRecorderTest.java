package com.yuwen.visionspace.acceptance;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExternalCallRecorderTest {
    @Test
    void unexpectedProviderCallIsRecordedAndRejected() throws Exception {
        try (var recorder = new ExternalCallRecorder(); var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create(recorder.baseUrl() + "/v1/chat/completions"))
                    .POST(HttpRequest.BodyPublishers.ofString("unexpected")).build();
            assertEquals(503, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(List.of(new ExternalCallRecorder.Call("POST", "/v1/chat/completions", "unexpected")), recorder.calls());
        }
    }
}
