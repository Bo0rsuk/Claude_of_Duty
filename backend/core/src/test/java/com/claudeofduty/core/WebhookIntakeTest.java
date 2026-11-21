package com.claudeofduty.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.claudeofduty.core.ingest.WebhookIntake;
import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.RawRecord;
import com.claudeofduty.core.model.Source;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WebhookIntakeTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    private Source source = source(Source.WEBHOOK, true);
    private final WebhookIntake intake = new WebhookIntake((orgId, sourceId) ->
            orgId.equals("org-1") && sourceId.equals("src-1") ? Optional.of(source) : Optional.empty());

    @Test
    void envelopeWithTheRightTokenBecomesARecord() {
        String payload = "{\"status\":\"firing\",\"note\":\"it's \\\"quoted\\\"\"}";
        RawRecord record = intake.open(Json.tree(envelope("org-1", "src-1", "secret", Json.write(payload)))).orElseThrow();

        assertEquals("request-1", record.recordId());
        assertEquals("org-1", record.orgId());
        assertEquals("src-1", record.sourceId());
        assertEquals(Instant.ofEpochMilli(1795226959549L), record.receivedAt());
        assertEquals(payload, record.payload());
    }

    @Test
    void envelopesFromSendersWithoutTheTokenAreDropped() {
        String payload = Json.write("{}");
        assertFalse(intake.open(Json.tree(envelope("org-1", "src-1", "wrong", payload))).isPresent());
        assertFalse(intake.open(Json.tree(envelope("org-1", "src-1", "", payload))).isPresent());
        assertFalse(intake.open(Json.tree(envelope("org-1", "other", "secret", payload))).isPresent());
        assertFalse(intake.open(Json.tree(envelope("org-1", "src-1", "secret", "\" \""))).isPresent());

        source = source(Source.WEBHOOK, false);
        assertFalse(intake.open(Json.tree(envelope("org-1", "src-1", "secret", payload))).isPresent());
        source = source(Source.HTTP_PULL, true);
        assertFalse(intake.open(Json.tree(envelope("org-1", "src-1", "secret", payload))).isPresent());
    }

    @Test
    void envelopesAreToldApartFromRecordsWrittenByTheWorker() {
        assertTrue(WebhookIntake.isEnvelope(Json.tree(envelope("org-1", "src-1", "secret", "\"x\""))));
        assertFalse(WebhookIntake.isEnvelope(Json.tree(Json.write(new RawRecord("r", "org-1", "src-1", NOW, "x")))));
    }

    private static String envelope(String orgId, String sourceId, String token, String payloadJson) {
        return "{\"recordId\":\"request-1\",\"orgId\":\"" + orgId + "\",\"sourceId\":\"" + sourceId + "\",\"token\":\""
                + token + "\",\"receivedAtMillis\":1795226959549,\"payload\":" + payloadJson + "}";
    }

    private static Source source(String type, boolean enabled) {
        Source.Connector connector = new Source.Connector(type, null, null, null, null, null, null, null, null);
        return new Source("src-1", "org-1", "Monitoring", enabled, connector, null, "secret", NOW, NOW);
    }
}
