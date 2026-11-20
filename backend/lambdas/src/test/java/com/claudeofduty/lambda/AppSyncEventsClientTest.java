package com.claudeofduty.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.claudeofduty.core.json.Json;
import com.claudeofduty.lambda.aws.AppSyncEventsClient;
import com.claudeofduty.lambda.aws.AwsClients;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

/** Sends a publish request to a local HTTP server and inspects what arrived. */
class AppSyncEventsClientTest {

    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedAuthorization = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private HttpServer server;
    private AppSyncEventsClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/event", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            receivedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"failed\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        client = new AppSyncEventsClient(
                AwsClients.http(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/event"),
                Region.EU_CENTRAL_1,
                StaticCredentialsProvider.create(AwsBasicCredentials.create("test-key", "test-secret")));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsTheEventAsAStringInsideASignedRequest() {
        client.publish("/alerts/org-1", Map.of("type", "alert.created"));

        JsonNode body = Json.tree(receivedBody.get());
        assertEquals("/alerts/org-1", body.get("channel").asText());
        assertEquals(1, body.get("events").size());
        assertTrue(body.get("events").get(0).isTextual());
        assertEquals("alert.created", Json.tree(body.get("events").get(0).asText()).get("type").asText());

        String authorization = receivedAuthorization.get();
        assertTrue(authorization.startsWith("AWS4-HMAC-SHA256 Credential=test-key/"), authorization);
        assertTrue(authorization.contains("/eu-central-1/appsync/aws4_request"), authorization);
    }

    @Test
    void rejectionByTheServiceIsReported() {
        status.set(403);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> client.publish("/alerts/org-1", Map.of("type", "alert.created")));
        assertTrue(error.getMessage().contains("403"), error.getMessage());
    }
}
