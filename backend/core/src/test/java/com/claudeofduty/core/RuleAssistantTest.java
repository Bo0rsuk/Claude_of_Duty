package com.claudeofduty.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.claudeofduty.core.api.ApiException;
import com.claudeofduty.core.api.Caller;
import com.claudeofduty.core.model.Level;
import com.claudeofduty.core.model.Member;
import com.claudeofduty.core.model.NormalizedEvent;
import com.claudeofduty.core.model.Rule.Condition;
import com.claudeofduty.core.model.Rule.Match;
import com.claudeofduty.core.model.Rule.MatchMode;
import com.claudeofduty.core.model.Severity;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.port.RecentEvents;
import com.claudeofduty.core.port.Repository;
import com.claudeofduty.core.rules.Operators;
import com.claudeofduty.core.service.RuleAssistantService;
import com.claudeofduty.core.service.RuleAssistantService.Suggestion;
import com.claudeofduty.core.service.RulePreviewService;
import com.claudeofduty.core.service.RulePreviewService.Preview;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuleAssistantTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static final Caller ANNA = new Caller("anna", "anna@example.pl",
            new Member("org-1", "anna", Member.Role.TEAM_LEAD, "Anna", "anna@example.pl", null, NOW));

    /** Ten events, newest first: three 503 errors from payment-api, seven successful requests. */
    private final List<NormalizedEvent> recent = new ArrayList<>();
    private final List<String> prompts = new ArrayList<>();
    private String modelAnswer = """
            {"mode": "all", "conditions": [
               {"field": "service", "op": "eq", "value": "payment-api"},
               {"field": "http.status", "op": "gte", "value": 500}],
             "severity": "CRITICAL", "explanation": "Łapie odpowiedzi 5xx z payment-api."}""";

    private final Operators operators = Operators.withDefaults();
    private final RulePreviewService previews = new RulePreviewService(new OneSource(), new RecentEvents() {
        @Override
        public void saveAll(List<NormalizedEvent> events) {
        }

        @Override
        public List<NormalizedEvent> latest(String orgId, String sourceId, int limit) {
            return recent.stream().limit(limit).toList();
        }
    }, operators);
    private final RuleAssistantService assistant = new RuleAssistantService(previews, (instructions, input) -> {
        prompts.add(instructions + "\n---\n" + input);
        return modelAnswer;
    }, operators);

    RuleAssistantTest() {
        for (int i = 0; i < 10; i++) {
            boolean error = i < 3;
            recent.add(new NormalizedEvent("evt-" + i, "org-1", "src-1", NOW, NOW, error ? Level.ERROR : Level.INFO,
                    error ? "Database connection timeout" : "Request completed",
                    Map.of("service", error ? "payment-api" : "checkout-api", "http.status", error ? 503 : 200), "raw"));
        }
    }

    @Test
    void previewCountsWhatARuleWouldHaveCaught() {
        Match errors = new Match(MatchMode.ALL, List.of(new Condition("level", "eq", "ERROR")));

        Preview preview = previews.preview(ANNA, "src-1", errors);

        assertEquals(3, preview.matched());
        assertEquals(10, preview.total());
        assertEquals(List.of("evt-0", "evt-1", "evt-2"), preview.samples().stream().map(NormalizedEvent::eventId).toList());
        assertEquals(10, previews.events(ANNA, "src-1", null).size());
        assertEquals(4, previews.events(ANNA, "src-1", 4).size());
    }

    @Test
    void previewRejectsUnknownSourcesAndBrokenConditions() {
        Match valid = new Match(MatchMode.ALL, List.of(new Condition("level", "eq", "ERROR")));
        Match broken = new Match(MatchMode.ALL, List.of(new Condition("level", "soundsLike", "ERROR")));

        assertEquals(404, assertThrows(ApiException.class, () -> previews.preview(ANNA, "other", valid)).status());
        ApiException invalid = assertThrows(ApiException.class, () -> previews.preview(ANNA, "src-1", broken));
        assertEquals(400, invalid.status());
        assertTrue(invalid.getMessage().contains("soundsLike"));
    }

    @Test
    void suggestionIsValidatedAndTriedOnRecentEvents() {
        Suggestion suggestion = assistant.suggest(ANNA, "src-1", null, "  Obudź mnie, gdy płatności zwracają 5xx ");

        assertEquals(MatchMode.ALL, suggestion.match().mode());
        assertEquals(List.of("service", "http.status"), suggestion.match().conditions().stream().map(Condition::field).toList());
        assertEquals(Severity.CRITICAL, suggestion.severity());
        assertEquals("Łapie odpowiedzi 5xx z payment-api.", suggestion.explanation());
        assertEquals(3, suggestion.preview().matched());
        assertEquals(10, suggestion.preview().total());
        assertTrue(suggestion.warnings().isEmpty());
    }

    @Test
    void modelSeesTheGoalAndTheRealFieldsOfTheSource() {
        NormalizedEvent picked = new NormalizedEvent("evt-x", "org-1", "src-1", NOW, NOW, Level.FATAL, "Out of memory",
                Map.of("service", "payment-api", "code", "OUT_OF_MEMORY"), "raw");

        assistant.suggest(ANNA, "src-1", picked, "Alert on out of memory");

        String prompt = prompts.get(0);
        assertTrue(prompt.contains("Goal: Alert on out of memory"), prompt);
        assertTrue(prompt.contains("- level: FATAL | ERROR | INFO"), prompt);
        assertTrue(prompt.contains("- service: payment-api | checkout-api"), prompt);
        assertTrue(prompt.contains("- http.status: 503 | 200"), prompt);
        assertTrue(prompt.contains("- code: OUT_OF_MEMORY"), prompt);
        assertTrue(prompt.contains("picked this event as an example"), prompt);
        assertTrue(prompt.contains("\"gte\""), prompt);
    }

    @Test
    void answerInACodeFenceAndWithAnUnseenFieldIsAcceptedWithAWarning() {
        modelAnswer = """
                ```json
                {"mode": "any", "conditions": [{"field": "region", "op": "in", "value": ["eu", "us"]}],
                 "severity": "URGENT", "explanation": "Regions."}
                ```""";

        Suggestion suggestion = assistant.suggest(ANNA, "src-1", null, "Alert per region");

        assertEquals(MatchMode.ANY, suggestion.match().mode());
        assertEquals(Severity.WARNING, suggestion.severity());
        assertEquals(0, suggestion.preview().matched());
        assertEquals(List.of("Field \"region\" does not appear in the recent events of this source"), suggestion.warnings());
    }

    @Test
    void unusableAnswersAndOutagesAreReportedAsAssistantErrors() {
        for (String answer : List.of("Sure! Here is your rule.", "[1, 2]", "{\"mode\": \"all\", \"conditions\": []}",
                "{\"mode\": \"all\", \"conditions\": [{\"field\": \"level\", \"op\": \"like\", \"value\": \"E\"}]}",
                "{\"mode\": \"all\", \"conditions\": [{\"field\": \"level\", \"op\": \"in\", \"value\": \"ERROR\"}]}",
                "{\"mode\": \"sometimes\", \"conditions\": [{\"field\": \"level\", \"op\": \"eq\", \"value\": \"E\"}]}")) {
            modelAnswer = answer;
            ApiException error = assertThrows(ApiException.class, () -> assistant.suggest(ANNA, "src-1", null, "x"), answer);
            assertEquals("AI_INVALID_ANSWER", error.code(), answer);
        }

        RuleAssistantService offline = new RuleAssistantService(previews, (instructions, input) -> {
            throw new IllegalStateException("HTTP 429");
        }, operators);
        ApiException outage = assertThrows(ApiException.class, () -> offline.suggest(ANNA, "src-1", null, "x"));
        assertEquals("AI_UNAVAILABLE", outage.code());
        assertEquals(502, outage.status());

        assertEquals(400, assertThrows(ApiException.class, () -> assistant.suggest(ANNA, "src-1", null, " ")).status());
    }

    private static final class OneSource implements Repository<Source> {
        @Override
        public List<Source> list(String orgId) {
            return List.of();
        }

        @Override
        public Optional<Source> find(String orgId, String id) {
            return id.equals("src-1")
                    ? Optional.of(new Source("src-1", orgId, "Payments", true, null, null, null, NOW, NOW))
                    : Optional.empty();
        }

        @Override
        public void save(Source item) {
        }

        @Override
        public void delete(String orgId, String id) {
        }
    }
}
