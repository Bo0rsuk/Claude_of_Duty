package com.claudeofduty.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.claudeofduty.core.alerting.EventProcessor;
import com.claudeofduty.core.alerting.EventProcessor.Summary;
import com.claudeofduty.core.model.Alert;
import com.claudeofduty.core.model.NormalizedEvent;
import com.claudeofduty.core.model.RawRecord;
import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.model.Severity;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.model.TimelineEntry;
import com.claudeofduty.core.parser.EventNormalizer;
import com.claudeofduty.core.parser.ParserRegistry;
import com.claudeofduty.core.port.AlertPublisher;
import com.claudeofduty.core.port.AlertStore;
import com.claudeofduty.core.port.RecentEvents;
import com.claudeofduty.core.rules.Operators;
import com.claudeofduty.core.rules.RuleMatcher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/** Runs the pipeline with the rule and the JSON API source from Docs/contracts against in-memory stores. */
class EventProcessorTest {

    private static final Instant NOW = Instant.parse("2026-11-20T21:00:00.000Z");
    private static final Instant RECEIVED_AT = Instant.parse("2026-11-20T20:59:59.900Z");

    private final Rule rule = ContractDocs.example("rules.md", "\"match\"", Rule.class);
    private final InMemoryAlertStore store = new InMemoryAlertStore();
    private final List<String> published = new ArrayList<>();
    private final List<String> notified = new ArrayList<>();
    private Source source = source(true);
    private List<Rule> rules = List.of(rule);

    private final List<NormalizedEvent> sampled = new ArrayList<>();
    private final RecentEvents recentEvents = new RecentEvents() {
        @Override
        public void saveAll(List<NormalizedEvent> events) {
            sampled.addAll(events);
        }

        @Override
        public List<NormalizedEvent> latest(String orgId, String sourceId, int limit) {
            return sampled;
        }
    };

    private final EventProcessor processor = new EventProcessor(
            new EventProcessor.Ports(
                    (orgId, sourceId) -> sourceId.equals(source.sourceId()) ? Optional.of(source) : Optional.empty(),
                    (orgId, sourceId) -> rules,
                    store,
                    (type, alert) -> published.add(type + " " + alert.alertId() + " x" + alert.count()),
                    (alert, alertRule, alertSource) -> notified.add(alert.alertId() + " via " + alertRule.ruleId()),
                    recentEvents),
            new EventNormalizer(ParserRegistry.withDefaults()),
            new RuleMatcher(Operators.withDefaults()),
            "https://app.example.pl",
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void fiveHundredIdenticalEventsBecomeOneAlert() {
        Summary summary = processor.process(Collections.nCopies(500, timeout("payment-api")));

        assertEquals(new Summary(500, 500, 500, 1, 0), summary);
        assertEquals(1, store.alerts.size());
        Alert alert = store.alerts.values().iterator().next();
        assertEquals(500, alert.count());
        assertEquals(Alert.Status.OPEN, alert.status());
        assertEquals(Severity.CRITICAL, alert.severity());
        assertEquals("Payment database timeouts", alert.ruleName());
        assertEquals("payment-api: DB_TIMEOUT", alert.title());
        assertEquals("Database connection timeout after 3000 ms on payment-api-1", alert.body());
        assertEquals(Map.of("service", "payment-api", "host", "payment-api-1", "http.status", 503, "http.latency_ms", 3012),
                alert.fields());
        assertEquals("rb-db-timeout", alert.runbookId());
        assertEquals(Instant.parse("2026-11-20T20:59:22.373Z"), alert.firstSeenAt());
        assertEquals(new Alert.Timing(RECEIVED_AT, NOW), alert.timing());
        assertTrue(alert.alertId().startsWith(NOW.toEpochMilli() + "-"));

        assertEquals(List.of("alert.created " + alert.alertId() + " x500"), published);
        assertEquals(List.of(TimelineEntry.Type.CREATED), store.timeline.stream().map(TimelineEntry::type).toList());
        assertEquals(alert.alertId(), store.timeline.get(0).alertId());
    }

    @Test
    void aFewEventsPerSecondOfEachSourceAreKeptAsARecentSample() {
        processor.process(Collections.nCopies(500, timeout("payment-api")));
        processor.process(List.of(record("src-1", event("payment-api", "INFO", "REQUEST_OK", 20))));

        // The clock is fixed, so both batches fall into the same second: five events in total, matching or not.
        assertEquals(5, sampled.size());
        assertEquals("payment-api", sampled.get(0).fields().get("service"));
    }

    @Test
    void laterBatchesAreAbsorbedByTheSameAlert() {
        processor.process(Collections.nCopies(3, timeout("payment-api")));
        Summary second = processor.process(Collections.nCopies(2, timeout("payment-api")));

        assertEquals(new Summary(2, 2, 2, 0, 1), second);
        assertEquals(1, store.alerts.size());
        Alert alert = store.alerts.values().iterator().next();
        assertEquals(5, alert.count());
        assertEquals(List.of("alert.created " + alert.alertId() + " x3", "alert.updated " + alert.alertId() + " x5"),
                published);
        // People are notified once, when the alert is created, not for every absorbed batch.
        assertEquals(List.of(alert.alertId() + " via " + rule.ruleId()), notified);
        assertEquals(1, store.timeline.size());
    }

    @Test
    void differentValuesOfGroupByFieldsOpenSeparateAlerts() {
        Summary summary = processor.process(List.of(
                timeout("payment-api"), timeout("checkout-api"), timeout("payment-api")));

        assertEquals(new Summary(3, 3, 3, 2, 0), summary);
        assertEquals(List.of("payment-api: DB_TIMEOUT", "checkout-api: DB_TIMEOUT"),
                store.alerts.values().stream().map(Alert::title).toList());
        assertEquals(List.of(2L, 1L), store.alerts.values().stream().map(Alert::count).toList());
    }

    @Test
    void groupsStoredConcurrentlyGiveTheSameResult() {
        ExecutorService threads = Executors.newFixedThreadPool(8);
        try {
            EventProcessor concurrent = new EventProcessor(
                    new EventProcessor.Ports(
                            (orgId, sourceId) -> Optional.of(source),
                            (orgId, sourceId) -> rules,
                            new ThreadSafeStore(store),
                            (type, alert) -> { },
                            (alert, alertRule, alertSource) -> { },
                            RecentEvents.NONE),
                    new EventNormalizer(ParserRegistry.withDefaults()),
                    new RuleMatcher(Operators.withDefaults()),
                    "https://app.example.pl",
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    threads);
            List<RawRecord> records = new ArrayList<>();
            for (int service = 0; service < 200; service++) {
                records.add(timeout("service-" + service));
                records.add(timeout("service-" + service));
            }

            Summary summary = concurrent.process(records);

            assertEquals(new Summary(400, 400, 400, 200, 0), summary);
            assertEquals(200, store.alerts.size());
            assertTrue(store.alerts.values().stream().allMatch(alert -> alert.count() == 2));
            assertEquals(new Summary(400, 400, 400, 0, 200), concurrent.process(records));
            assertTrue(store.alerts.values().stream().allMatch(alert -> alert.count() == 4));
        } finally {
            threads.shutdownNow();
        }
    }

    @Test
    void eventsThatMatchNoRuleCreateNothing() {
        Summary summary = processor.process(List.of(record("src-1", event("payment-api", "INFO", "REQUEST_OK", 20))));

        assertEquals(new Summary(1, 1, 0, 0, 0), summary);
        assertTrue(store.alerts.isEmpty());
        assertTrue(published.isEmpty());
    }

    @Test
    void unknownAndDisabledSourcesAreSkipped() {
        assertEquals(new Summary(1, 0, 0, 0, 0),
                processor.process(List.of(record("src-unknown", event("payment-api", "ERROR", "DB_TIMEOUT", 3012)))));

        source = source(false);
        assertEquals(new Summary(1, 0, 0, 0, 0), processor.process(List.of(timeout("payment-api"))));
        assertTrue(store.alerts.isEmpty());
    }

    @Test
    void unparsablePayloadDoesNotFailTheBatch() {
        Summary summary = processor.process(List.of(record("src-1", "<html>502 Bad Gateway</html>"), timeout("payment-api")));

        assertEquals(new Summary(2, 2, 1, 1, 0), summary);
    }

    @Test
    void ruleWithoutPresentationFallsBackToRuleNameAndMessage() {
        rules = List.of(new Rule(rule.ruleId(), rule.orgId(), rule.sourceId(), rule.name(), true, rule.match(),
                Severity.WARNING, null, null, null, null, null, null, null, null));

        processor.process(List.of(timeout("payment-api"), timeout("checkout-api")));

        assertEquals(1, store.alerts.size());
        Alert alert = store.alerts.values().iterator().next();
        assertEquals(2, alert.count());
        assertEquals("Payment database timeouts", alert.title());
        assertEquals("Database connection timeout after 3000 ms", alert.body());
        assertTrue(alert.fields().isEmpty());
        assertNull(alert.runbookId());
    }

    @Test
    void alertIsCreatedUnderAnIdThatWasClaimedButNeverWritten() {
        processor.process(List.of(timeout("payment-api")));
        String claimedId = store.alerts.keySet().iterator().next();
        store.alerts.clear();
        published.clear();

        Summary summary = processor.process(List.of(timeout("payment-api")));

        assertEquals(new Summary(1, 1, 1, 1, 0), summary);
        assertEquals(List.of(claimedId), List.copyOf(store.alerts.keySet()));
        assertEquals(List.of("alert.created " + claimedId + " x1"), published);
    }

    private static RawRecord timeout(String service) {
        return record("src-1", event(service, "ERROR", "DB_TIMEOUT", 3012));
    }

    private static String event(String service, String level, String code, int latencyMs) {
        return """
                {"timestamp":"2026-11-20T20:59:22.373Z","service":"%s","host":"%s-1","level":"%s","code":"%s",
                 "message":"Database connection timeout after 3000 ms",
                 "http":{"method":"POST","path":"/v1/payments","status":503,"latency_ms":%d}}"""
                .formatted(service, service, level, code, latencyMs);
    }

    private static RawRecord record(String sourceId, String payload) {
        return new RawRecord("record-1", "org-1", sourceId, RECEIVED_AT, payload);
    }

    private static Source source(boolean enabled) {
        Source preset = ContractDocs.mockPresets().get(0);
        return new Source("src-1", "org-1", "Payments API", enabled, preset.connector(), preset.parser(), null, NOW, NOW);
    }

    /** Serializes access to the in-memory store, the way DynamoDB's conditional writes do for the real one. */
    private record ThreadSafeStore(InMemoryAlertStore delegate) implements AlertStore {

        @Override
        public synchronized String claim(String orgId, String fingerprint, String candidateAlertId) {
            return delegate.claim(orgId, fingerprint, candidateAlertId);
        }

        @Override
        public synchronized void create(Alert alert) {
            delegate.create(alert);
        }

        @Override
        public synchronized Optional<Alert> addEvents(String orgId, String alertId, long count, Instant lastSeenAt) {
            return delegate.addEvents(orgId, alertId, count, lastSeenAt);
        }

        @Override
        public synchronized void appendTimeline(TimelineEntry entry) {
            delegate.appendTimeline(entry);
        }
    }

    private static final class InMemoryAlertStore implements AlertStore {

        private final Map<String, String> activeByFingerprint = new HashMap<>();
        private final Map<String, Alert> alerts = new java.util.LinkedHashMap<>();
        private final List<TimelineEntry> timeline = new ArrayList<>();

        @Override
        public String claim(String orgId, String fingerprint, String candidateAlertId) {
            return activeByFingerprint.computeIfAbsent(fingerprint, key -> candidateAlertId);
        }

        @Override
        public void create(Alert alert) {
            alerts.put(alert.alertId(), alert);
        }

        @Override
        public Optional<Alert> addEvents(String orgId, String alertId, long count, Instant lastSeenAt) {
            return Optional.ofNullable(alerts.computeIfPresent(alertId, (id, a) -> new Alert(
                    a.alertId(), a.orgId(), a.ruleId(), a.ruleName(), a.sourceId(), a.fingerprint(), a.severity(),
                    a.status(), a.title(), a.body(), a.fields(), a.count() + count, a.firstSeenAt(), lastSeenAt,
                    a.sampleEvent(), a.runbookId(), a.assigneeId(), a.acknowledgedBy(), a.acknowledgedAt(),
                    a.resolvedBy(), a.resolvedAt(), a.timing())));
        }

        @Override
        public void appendTimeline(TimelineEntry entry) {
            timeline.add(entry);
        }
    }
}
