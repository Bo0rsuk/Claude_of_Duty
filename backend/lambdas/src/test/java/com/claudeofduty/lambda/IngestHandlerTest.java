package com.claudeofduty.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.RawRecord;
import com.claudeofduty.core.model.Source;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IngestHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-20T20:59:22.401Z");
    private static final String PAYLOAD = "{\"status\":\"firing\"}";

    private final List<RawRecord> published = new ArrayList<>();

    @Test
    void acceptsPayloadWithTokenInQueryAndPutsItOnTheStreamUntouched() {
        APIGatewayV2HTTPResponse response = handlerFor(webhook(true)).handleRequest(request("secret", null, PAYLOAD), null);

        assertEquals(202, response.getStatusCode());
        assertEquals(1, published.size());
        RawRecord record = published.get(0);
        assertEquals("org-1", record.orgId());
        assertEquals("src-1", record.sourceId());
        assertEquals(NOW, record.receivedAt());
        assertEquals(PAYLOAD, record.payload());
        assertEquals(record.recordId(), Json.tree(response.getBody()).get("recordId").asText());
    }

    @Test
    void acceptsTokenInHeader() {
        assertEquals(202, handlerFor(webhook(true)).handleRequest(request(null, "secret", PAYLOAD), null).getStatusCode());
    }

    @Test
    void decodesBase64Body() {
        APIGatewayV2HTTPEvent request = request("secret", null,
                Base64.getEncoder().encodeToString(PAYLOAD.getBytes(StandardCharsets.UTF_8)));
        request.setIsBase64Encoded(true);

        handlerFor(webhook(true)).handleRequest(request, null);

        assertEquals(PAYLOAD, published.get(0).payload());
    }

    @Test
    void rejectsWrongOrMissingToken() {
        assertRejected(404, handlerFor(webhook(true)).handleRequest(request("wrong", null, PAYLOAD), null));
        assertRejected(404, handlerFor(webhook(true)).handleRequest(request(null, null, PAYLOAD), null));
    }

    @Test
    void rejectsUnknownDisabledAndNonWebhookSources() {
        assertRejected(404, new IngestHandler((orgId, sourceId) -> Optional.empty(), published::add, clock())
                .handleRequest(request("secret", null, PAYLOAD), null));
        assertRejected(404, handlerFor(webhook(false)).handleRequest(request("secret", null, PAYLOAD), null));
        assertRejected(404, handlerFor(source(Source.HTTP_PULL, true)).handleRequest(request("secret", null, PAYLOAD), null));
    }

    @Test
    void rejectsEmptyBody() {
        assertRejected(400, handlerFor(webhook(true)).handleRequest(request("secret", null, " "), null));
    }

    private void assertRejected(int status, APIGatewayV2HTTPResponse response) {
        assertEquals(status, response.getStatusCode());
        assertTrue(Json.tree(response.getBody()).has("error"));
        assertTrue(published.isEmpty());
    }

    private IngestHandler handlerFor(Source source) {
        return new IngestHandler(
                (orgId, sourceId) -> orgId.equals(source.orgId()) && sourceId.equals(source.sourceId())
                        ? Optional.of(source)
                        : Optional.empty(),
                published::add,
                clock());
    }

    private static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private static Source webhook(boolean enabled) {
        return source(Source.WEBHOOK, enabled);
    }

    private static Source source(String connectorType, boolean enabled) {
        Source.Connector connector = new Source.Connector(connectorType, null, null, null, null, null, null, null, null);
        return new Source("src-1", "org-1", "Monitoring", enabled, connector, null, "secret", NOW, NOW);
    }

    private static APIGatewayV2HTTPEvent request(String queryToken, String headerToken, String body) {
        return APIGatewayV2HTTPEvent.builder()
                .withPathParameters(Map.of("orgId", "org-1", "sourceId", "src-1"))
                .withQueryStringParameters(queryToken == null ? null : Map.of("token", queryToken))
                .withHeaders(headerToken == null ? Map.of() : Map.of("x-ingest-token", headerToken))
                .withBody(body)
                .withIsBase64Encoded(false)
                .build();
    }
}
