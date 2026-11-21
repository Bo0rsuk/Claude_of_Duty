package com.claudeofduty.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.claudeofduty.core.alerting.EventProcessor;
import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.Alert;
import com.claudeofduty.core.model.RawRecord;
import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.model.TimelineEntry;
import com.claudeofduty.core.parser.EventNormalizer;
import com.claudeofduty.core.parser.ParserRegistry;
import com.claudeofduty.core.port.AlertStore;
import com.claudeofduty.core.port.RecentEvents;
import com.claudeofduty.core.rules.Operators;
import com.claudeofduty.core.rules.RuleMatcher;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Checks that the demo data in demo/seed turns the mock's JSON API events into the expected alerts. */
class DemoSeedTest {

    private static final Path SEED = Path.of("..", "..", "demo", "seed");

    @Test
    void mockApiEventsBecomeOneCriticalAlertPerFailingService() throws IOException {
        Source source = load("sources.json", Source.class).stream()
                .filter(candidate -> candidate.sourceId().equals("mock-json-api")).findFirst().orElseThrow();
        List<Rule> rules = load("rules.json", Rule.class);
        List<Alert> created = new ArrayList<>();
        EventProcessor processor = new EventProcessor(
                new EventProcessor.Ports(
                        (orgId, sourceId) -> Optional.of(source),
                        (orgId, sourceId) -> rules.stream().filter(rule -> rule.sourceId().equals(sourceId)).toList(),
                        new CollectingStore(created),
                        (type, alert) -> { },
                        (alert, rule, alertSource) -> { },
                        RecentEvents.NONE),
                new EventNormalizer(ParserRegistry.withDefaults()),
                new RuleMatcher(Operators.withDefaults()),
                "http://localhost:5173",
                Clock.systemUTC());

        processor.process(List.of(
                event("1", "ERROR", "DB_TIMEOUT", "payment-api"),
                event("2", "INFO", "REQUEST_OK", "payment-api"),
                event("3", "FATAL", "OUT_OF_MEMORY", "payment-api"),
                event("4", "ERROR", "UPSTREAM_5XX", "checkout-api")));

        // Failures are grouped by service whatever their code, and ordinary traffic raises nothing.
        assertEquals(List.of("payment-api is failing x2 CRITICAL", "checkout-api is failing x1 CRITICAL"),
                created.stream().map(alert -> alert.title() + " x" + alert.count() + " " + alert.severity()).toList());
        assertEquals("DB_TIMEOUT: Something happened on payment-api-1", created.get(0).body());
    }

    @Test
    void seedHoldsARuleForEverySeverity() throws IOException {
        assertEquals(List.of("CRITICAL", "INFO", "WARNING"),
                load("rules.json", Rule.class).stream().map(rule -> rule.severity().name()).distinct().sorted().toList());
    }

    private static RawRecord event(String id, String level, String code, String service) {
        String payload = """
                {"id": "%s", "timestamp": "2026-11-20T20:59:34.000Z", "service": "%s", "host": "%s-1", "level": "%s",
                  "code": "%s", "message": "Something happened", "http": {"status": 503, "latency_ms": 900}}"""
                .formatted(id, service, service, level, code);
        return new RawRecord("record-" + id, "demo", "mock-json-api", Instant.now(), payload);
    }

    private static <T> List<T> load(String file, Class<T> type) throws IOException {
        List<T> items = new ArrayList<>();
        for (JsonNode element : Json.tree(Files.readString(SEED.resolve(file)))) {
            items.add(Json.read(element.toString(), type));
        }
        return items;
    }

    /** Never finds an existing alert, so every group of a batch is created. */
    private record CollectingStore(List<Alert> created) implements AlertStore {

        @Override
        public String claim(String orgId, String fingerprint, String candidateAlertId) {
            return candidateAlertId;
        }

        @Override
        public Alert addEvents(Alert alert) {
            created.add(alert);
            return alert;
        }

        @Override
        public void appendTimeline(TimelineEntry entry) {
        }
    }
}
