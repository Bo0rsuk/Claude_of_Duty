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

/** Checks that the demo data in demo/seed turns the mock's webhook payloads into the expected alerts. */
class DemoSeedTest {

    private static final Path SEED = Path.of("..", "..", "demo", "seed");

    @Test
    void mockWebhookPayloadsBecomeAlertsUnderTheSeededRules() throws IOException {
        Source source = load("sources.json", Source.class).get(0);
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

        processor.process(List.of(webhook("DB_TIMEOUT", "error"), webhook("SLOW_QUERY", "warn"), webhook("DB_TIMEOUT", "error")));

        assertEquals(List.of("monitoring: DB_TIMEOUT x2 CRITICAL", "monitoring: SLOW_QUERY x1 WARNING"),
                created.stream().map(alert -> alert.title() + " x" + alert.count() + " " + alert.severity()).toList());
        assertEquals("Something happened on monitoring-1", created.get(0).body());
    }

    private static RawRecord webhook(String alertName, String severity) {
        String payload = """
                {"version": "4", "status": "firing", "receiver": "claude-of-duty", "alerts": [{"status": "firing",
                  "labels": {"alertname": "%s", "severity": "%s", "service": "monitoring", "instance": "monitoring-1"},
                  "annotations": {"summary": "Something happened"}, "startsAt": "2026-11-20T20:59:34Z"}]}"""
                .formatted(alertName, severity);
        return new RawRecord("record-" + alertName, "demo", "mock-webhook", Instant.now(), payload);
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
