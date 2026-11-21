package com.claudeofduty.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.claudeofduty.core.model.Level;
import com.claudeofduty.core.model.NormalizedEvent;
import com.claudeofduty.core.model.RawRecord;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.model.Source.ParserConfig;
import com.claudeofduty.core.parser.EventNormalizer;
import com.claudeofduty.core.parser.ParserRegistry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Parses real payloads of the mock data sources with the parser configurations
 * published in Docs/contracts/sources-and-events.md.
 */
class MockFormatsTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-11-20T21:00:00.000Z");
    private static final List<Source> PRESETS = ContractDocs.mockPresets();

    private final EventNormalizer normalizer = new EventNormalizer(ParserRegistry.withDefaults());

    @Test
    void jsonApi() {
        NormalizedEvent event = single(0, """
                {"seq":142,"id":"12e97979","timestamp":"2026-11-20T20:59:22.373Z","service":"payment-api",
                 "host":"payment-api-1","level":"ERROR","code":"DB_TIMEOUT",
                 "message":"Database connection timeout after 3000 ms",
                 "http":{"method":"POST","path":"/v1/payments","status":503,"latency_ms":3012},
                 "trace_id":"e3c696798b55410f"}""");

        assertEquals(Instant.parse("2026-11-20T20:59:22.373Z"), event.eventTime());
        assertEquals(RECEIVED_AT, event.receivedAt());
        assertEquals(Level.ERROR, event.level());
        assertEquals("Database connection timeout after 3000 ms", event.message());
        assertEquals("payment-api", event.fields().get("service"));
        assertEquals(503, event.fields().get("http.status"));
        assertEquals(3012, event.fields().get("http.latency_ms"));
        assertEquals("POST", event.fields().get("http.method"));
        assertFalse(event.fields().containsKey("timestamp"));
        assertFalse(event.fields().containsKey("level"));
        assertFalse(event.fields().containsKey("message"));
    }

    @Test
    void applicationLog() {
        NormalizedEvent event = single(1, "2026-11-20 20:59:22,373 WARN  [inventory-service] [inventory-service-1] "
                + "[trace=caa27ec2cabe4001] RETRY - Retrying call to inventory-service, attempt 2 of 3");

        assertEquals(Instant.parse("2026-11-20T20:59:22.373Z"), event.eventTime());
        assertEquals(Level.WARN, event.level());
        assertEquals("Retrying call to inventory-service, attempt 2 of 3", event.message());
        assertEquals("inventory-service", event.fields().get("service"));
        assertEquals("inventory-service-1", event.fields().get("host"));
        assertEquals("caa27ec2cabe4001", event.fields().get("traceId"));
        assertEquals("RETRY", event.fields().get("code"));
    }

    @Test
    void accessLog() {
        String line = "10.0.9.56 - - [20/Nov/2026:20:59:22 +0000] \"GET /v1/users/me HTTP/1.1\" 503 3173 "
                + "\"-\" \"okhttp/4.12.0\" 3.216";
        NormalizedEvent event = single(2, line);

        assertEquals(Instant.parse("2026-11-20T20:59:22Z"), event.eventTime());
        assertEquals(Level.UNKNOWN, event.level());
        assertEquals(line, event.message());
        assertEquals("10.0.9.56", event.fields().get("clientIp"));
        assertEquals("GET", event.fields().get("method"));
        assertEquals("/v1/users/me", event.fields().get("path"));
        assertEquals("503", event.fields().get("status"));
        assertEquals("3.216", event.fields().get("requestTime"));
    }

    @Test
    void kafka() {
        NormalizedEvent event = single(3, """
                {"ts": 1795208347939, "source": "k8s/prod-eu", "kind": "RETRY", "severity": "warn",
                 "summary": "Retrying call to inventory-service, attempt 2 of 3",
                 "labels": {"service": "k8s-scheduler", "pod": "k8s-scheduler-1", "namespace": "prod"},
                 "value": 2177}""");

        assertEquals(Instant.ofEpochMilli(1795208347939L), event.eventTime());
        assertEquals(Level.WARN, event.level());
        assertEquals("Retrying call to inventory-service, attempt 2 of 3", event.message());
        assertEquals("RETRY", event.fields().get("kind"));
        assertEquals("k8s-scheduler", event.fields().get("labels.service"));
        assertEquals(2177, event.fields().get("value"));
    }

    @Test
    void webhookIsSplitIntoOneEventPerAlert() {
        List<NormalizedEvent> events = normalize(4, """
                {"version": "4", "status": "firing", "receiver": "claude-of-duty", "alerts": [
                  {"status": "firing",
                   "labels": {"alertname": "SLOW_QUERY", "severity": "warn", "service": "monitoring", "instance": "monitoring-1"},
                   "annotations": {"summary": "Query took 2984 ms, threshold is 500 ms"},
                   "startsAt": "2026-11-20T20:59:34Z"},
                  {"status": "firing",
                   "labels": {"alertname": "DB_TIMEOUT", "severity": "error", "service": "monitoring", "instance": "monitoring-2"},
                   "annotations": {"summary": "Database connection timeout after 3000 ms"},
                   "startsAt": "2026-11-20T20:59:35Z"}]}""");

        assertEquals(2, events.size());
        assertEquals(List.of("record-1-0", "record-1-1"), events.stream().map(NormalizedEvent::eventId).toList());

        NormalizedEvent first = events.get(0);
        assertEquals(Instant.parse("2026-11-20T20:59:34Z"), first.eventTime());
        assertEquals(Level.WARN, first.level());
        assertEquals("Query took 2984 ms, threshold is 500 ms", first.message());
        assertEquals("SLOW_QUERY", first.fields().get("labels.alertname"));
        assertEquals("claude-of-duty", first.fields().get("receiver"));
        assertFalse(first.fields().containsKey("alerts"));

        assertEquals(Level.ERROR, events.get(1).level());
        assertEquals("monitoring-2", events.get(1).fields().get("labels.instance"));

        // Each event keeps its own element as raw text, not the payload it shared with the other alert.
        assertTrue(first.raw().contains("SLOW_QUERY"));
        assertFalse(first.raw().contains("DB_TIMEOUT"));
    }

    @Test
    void unparsablePayloadBecomesAnEventWithTheRawTextAsMessage() {
        for (int preset = 0; preset < PRESETS.size(); preset++) {
            NormalizedEvent event = single(preset, "this is neither JSON nor a known log line");

            assertEquals("this is neither JSON nor a known log line", event.message());
            assertEquals(Level.UNKNOWN, event.level());
            assertEquals(RECEIVED_AT, event.eventTime());
            assertTrue(event.fields().isEmpty());
        }
    }

    @Test
    void keyValueLine() {
        RawRecord record = new RawRecord("record-1", "org-1", "src-1", RECEIVED_AT,
                "ts=2026-11-20T20:59:22.373Z service=settlement level=error attempts=3 msg=\"queue is \\\"full\\\"\" empty=");
        ParserConfig config = new ParserConfig("kv", null, null, "ts", "iso8601", "level", "msg");

        NormalizedEvent event = normalizer.normalize(record, config).get(0);

        assertEquals(Instant.parse("2026-11-20T20:59:22.373Z"), event.eventTime());
        assertEquals(Level.ERROR, event.level());
        assertEquals("queue is \"full\"", event.message());
        assertEquals("settlement", event.fields().get("service"));
        assertEquals("3", event.fields().get("attempts"));
        assertEquals("", event.fields().get("empty"));
        assertEquals("no pairs here", normalizer.normalize(
                new RawRecord("r", "org-1", "src-1", RECEIVED_AT, "no pairs here"), config).get(0).message());
    }

    @Test
    void unparsableEventTimeFallsBackToTheTimeOfArrival() {
        NormalizedEvent event = single(0, "{\"timestamp\": \"yesterday\", \"level\": \"INFO\", \"message\": \"ok\"}");

        assertEquals(RECEIVED_AT, event.eventTime());
    }

    private NormalizedEvent single(int preset, String payload) {
        List<NormalizedEvent> events = normalize(preset, payload);
        assertEquals(1, events.size());
        return events.get(0);
    }

    private List<NormalizedEvent> normalize(int preset, String payload) {
        RawRecord record = new RawRecord("record-1", "org-1", "src-1", RECEIVED_AT, payload);
        return normalizer.normalize(record, PRESETS.get(preset).parser());
    }
}
