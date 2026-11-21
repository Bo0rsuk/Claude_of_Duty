package com.claudeofduty.lambda.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.claudeofduty.core.json.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Talks to a local server that answers like the Gemini API and accepts only the key "current". */
class GeminiTextModelTest {

    private final List<String> keysSent = new ArrayList<>();
    private final List<JsonNode> bodies = new ArrayList<>();
    private HttpServer server;
    private String key = "current";
    private int forgotten;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/tiny-model:generateContent", exchange -> {
            String sentKey = exchange.getRequestHeaders().getFirst("x-goog-api-key");
            keysSent.add(sentKey);
            bodies.add(Json.tree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            boolean valid = "current".equals(sentKey);
            byte[] response = (valid
                    ? "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"{\\\"mode\\\":\"}, {\"text\": \" \\\"all\\\"}\"}]}}]}"
                    : "{\"error\": {\"message\": \"API key not valid.\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(valid ? 200 : 401, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsInstructionsAndInputAndReturnsTheAnswerText() {
        String answer = model().completeJson("Write a rule.", "Goal: errors");

        assertEquals("{\"mode\": \"all\"}", answer);
        JsonNode body = bodies.get(0);
        assertEquals("Write a rule.", body.at("/systemInstruction/parts/0/text").asText());
        assertEquals("Goal: errors", body.at("/contents/0/parts/0/text").asText());
        assertEquals("application/json", body.at("/generationConfig/responseMimeType").asText());
        assertEquals(0, forgotten);
    }

    @Test
    void rotatedKeyIsReloadedAndTheRequestRetriedOnce() {
        key = "revoked";

        String answer = model().completeJson("Write a rule.", "Goal: errors");

        assertEquals("{\"mode\": \"all\"}", answer);
        assertEquals(List.of("revoked", "current"), keysSent);
        assertEquals(1, forgotten);
    }

    @Test
    void keyThatStaysInvalidIsReportedAfterOneRetry() {
        GeminiTextModel model = new GeminiTextModel(baseUrl(), "tiny-model", () -> "wrong", () -> forgotten++);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> model.completeJson("a", "b"));

        assertTrue(error.getMessage().contains("HTTP 401: API key not valid."), error.getMessage());
        assertEquals(2, keysSent.size());
    }

    /** Forgetting the key stands for re-reading the parameter, which by then holds the rotated value. */
    private GeminiTextModel model() {
        return new GeminiTextModel(baseUrl(), "tiny-model", () -> key, () -> {
            forgotten++;
            key = "current";
        });
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
