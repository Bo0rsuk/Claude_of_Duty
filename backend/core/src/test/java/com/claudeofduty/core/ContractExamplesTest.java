package com.claudeofduty.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static com.claudeofduty.core.ContractDocs.example;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.Alert;
import com.claudeofduty.core.model.Level;
import com.claudeofduty.core.model.Member;
import com.claudeofduty.core.model.NormalizedEvent;
import com.claudeofduty.core.model.NotificationJob;
import com.claudeofduty.core.model.Organization;
import com.claudeofduty.core.model.RawRecord;
import com.claudeofduty.core.model.RecipientGroup;
import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.model.Runbook;
import com.claudeofduty.core.model.Severity;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.model.TimelineEntry;
import com.claudeofduty.core.service.PreviewService;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Reads the JSON examples straight from Docs/contracts and maps them onto the
 * model, so the documents and the code cannot drift apart unnoticed.
 */
class ContractExamplesTest {

    @Test
    void sourceExampleMapsOntoTheModel() {
        Source source = example("sources-and-events.md", "\"ingestToken\"", Source.class);

        assertEquals("Payments API", source.name());
        assertTrue(source.enabled());
        assertEquals(Source.HTTP_PULL, source.connector().type());
        assertEquals(5, source.connector().intervalSeconds());
        assertEquals("body:next_after", source.connector().cursorFrom());
        assertEquals("json", source.parser().format());
        assertEquals("timestamp", source.parser().eventTime());
        assertRoundTrips(source, Source.class);
    }

    @Test
    void allFiveMockPresetsMapOntoTheModel() {
        List<Source> presets = ContractDocs.mockPresets();

        assertEquals(5, presets.size());
        assertEquals(List.of("http-pull", "http-pull", "http-pull", "kafka", "webhook"),
                presets.stream().map(preset -> preset.connector().type()).toList());
        assertEquals(List.of("json", "regex", "regex", "json", "json"),
                presets.stream().map(preset -> preset.parser().format()).toList());
        assertEquals("alerts", presets.get(4).parser().split());
        presets.stream()
                .filter(preset -> preset.parser().format().equals("regex"))
                .forEach(preset -> Pattern.compile(preset.parser().pattern()));
    }

    @Test
    void presetsServedByTheApiAreTheOnesInTheContract() {
        List<Source> documented = ContractDocs.mockPresets();
        List<Source> served = PreviewService.presets();

        assertEquals(documented.stream().map(Source::connector).toList(), served.stream().map(Source::connector).toList());
        assertEquals(documented.stream().map(Source::parser).toList(), served.stream().map(Source::parser).toList());
        assertTrue(served.stream().noneMatch(preset -> preset.name() == null || preset.name().isBlank()));
    }

    @Test
    void rawRecordAndNormalizedEventExamplesMapOntoTheModel() {
        RawRecord record = example("sources-and-events.md", "\"recordId\"", RawRecord.class);
        assertEquals(Instant.parse("2026-11-20T20:59:22.401Z"), record.receivedAt());
        assertRoundTrips(record, RawRecord.class);

        NormalizedEvent event = example("sources-and-events.md", "\"eventId\"", NormalizedEvent.class);
        assertEquals(Level.ERROR, event.level());
        assertEquals("payment-api", event.fields().get("service"));
        assertEquals(503, event.fields().get("http.status"));
        assertRoundTrips(event, NormalizedEvent.class);
    }

    @Test
    void ruleExampleMapsOntoTheModel() {
        Rule rule = example("rules.md", "\"match\"", Rule.class);

        assertEquals("Payment database timeouts", rule.name());
        assertEquals(Rule.MatchMode.ALL, rule.match().mode());
        assertEquals(3, rule.match().conditions().size());
        assertEquals(List.of("ERROR", "FATAL"), rule.match().conditions().get(0).value());
        assertEquals(1000, rule.match().conditions().get(2).value());
        assertEquals(Severity.CRITICAL, rule.severity());
        assertEquals(List.of("sms", "voice", "email", "ui"), rule.routing().stream().map(Rule.Route::channel).toList());
        assertEquals("grp-cloud-engs", rule.routing().get(0).target().id());
        assertEquals("oncall@example.pl", rule.routing().get(2).target().value());
        assertEquals("{{service}}: {{code}}", rule.presentation().title());
        assertNotNull(rule.presentation().channels().get("email").subject());
        assertEquals(List.of("service", "code"), rule.groupBy());
        assertEquals(new Rule.Limit(5, 300), rule.throttle().perRule());
        assertEquals("rb-db-timeout", rule.runbookId());
        assertRoundTrips(rule, Rule.class);
    }

    @Test
    void runbookExampleMapsOntoTheModel() {
        Runbook runbook = example("rules.md", "\"format\": \"checklist\"", Runbook.class);

        assertEquals(Runbook.CHECKLIST, runbook.format());
        assertEquals(2, runbook.items().size());
        assertRoundTrips(runbook, Runbook.class);
    }

    @Test
    void alertExamplesMapOntoTheModel() {
        Alert alert = example("alerts.md", "\"fingerprint\"", Alert.class);
        assertEquals(Alert.Status.OPEN, alert.status());
        assertEquals(500, alert.count());
        assertEquals(Instant.parse("2026-11-20T20:59:22.512Z"), alert.timing().processedAt());
        assertNull(alert.acknowledgedAt());

        TimelineEntry entry = example("alerts.md", "\"entryId\"", TimelineEntry.class);
        assertEquals(TimelineEntry.Type.NOTIFICATION_SENT, entry.type());
        assertEquals(TimelineEntry.Actor.system(), entry.actor());
        assertEquals("sms", entry.detail().get("channel"));
        assertRoundTrips(entry, TimelineEntry.class);

        NotificationJob job = example("alerts.md", "\"notificationId\"", NotificationJob.class);
        assertEquals("+48600100200", job.recipient().address());
        assertEquals(1, job.attempt());
        assertRoundTrips(job, NotificationJob.class);
    }

    @Test
    void organizationExamplesMapOntoTheModel() {
        Organization organization = example("data-model.md", "\"inviteCode\"", Organization.class);
        assertEquals("K7Q2-M9XD", organization.inviteCode());

        Member member = example("data-model.md", "\"role\"", Member.class);
        assertEquals(Member.Role.TEAM_LEAD, member.role());
        assertTrue(member.role().atLeast(Member.Role.USER));
        assertTrue(Json.write(member).contains("\"role\":\"team_lead\""));

        RecipientGroup group = example("data-model.md", "\"groupId\"", RecipientGroup.class);
        assertEquals(List.of("user", "contact"), group.members().stream().map(RecipientGroup.GroupMember::type).toList());
        assertRoundTrips(group, RecipientGroup.class);
    }

    @Test
    void timestampsAlwaysCarryMilliseconds() {
        assertEquals("2026-11-20T21:00:00.000Z", Json.timestamp(Instant.parse("2026-11-20T21:00:00Z")));
        assertEquals("\"2026-11-20T20:59:22.373Z\"", Json.write(Instant.parse("2026-11-20T20:59:22.373Z")));
    }

    @Test
    void levelSpellingsAreNormalized() {
        assertEquals(Level.WARN, Level.parse("warning"));
        assertEquals(Level.ERROR, Level.parse(" err "));
        assertEquals(Level.FATAL, Level.parse("critical"));
        assertEquals(Level.UNKNOWN, Level.parse("verbose"));
        assertEquals(Level.UNKNOWN, Level.parse(null));
    }

    private static <T> void assertRoundTrips(T value, Class<T> type) {
        assertEquals(value, Json.read(Json.write(value), type));
    }
}
