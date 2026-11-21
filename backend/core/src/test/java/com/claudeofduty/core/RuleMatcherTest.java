package com.claudeofduty.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.claudeofduty.core.model.Level;
import com.claudeofduty.core.model.NormalizedEvent;
import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.model.Rule.Condition;
import com.claudeofduty.core.model.Rule.Match;
import com.claudeofduty.core.model.Rule.MatchMode;
import com.claudeofduty.core.rules.Operators;
import com.claudeofduty.core.rules.RuleMatcher;
import com.claudeofduty.core.template.Templates;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleMatcherTest {

    private static final Instant NOW = Instant.parse("2026-11-20T21:00:00Z");
    private static final NormalizedEvent EVENT = new NormalizedEvent("evt-1", "org-1", "src-1", NOW, NOW, Level.ERROR,
            "Database connection timeout after 3000 ms",
            Map.of("service", "payment-api", "code", "DB_TIMEOUT", "http.status", 503, "status", "503",
                    "requestTime", "3.216", "retried", true),
            "raw");

    private final RuleMatcher matcher = new RuleMatcher(Operators.withDefaults());

    @Test
    void contractRuleMatchesTheEventItDescribes() {
        Rule rule = ContractDocs.example("rules.md", "\"match\"", Rule.class);
        NormalizedEvent slow = new NormalizedEvent("evt-2", "org-1", "src-1", NOW, NOW, Level.ERROR, "Timeout",
                Map.of("code", "DB_TIMEOUT", "http.latency_ms", 3012), "raw");
        NormalizedEvent fast = new NormalizedEvent("evt-3", "org-1", "src-1", NOW, NOW, Level.ERROR, "Timeout",
                Map.of("code", "DB_TIMEOUT", "http.latency_ms", 200), "raw");

        assertTrue(matcher.matches(rule, slow));
        assertFalse(matcher.matches(rule, fast));
    }

    @Test
    void equalityComparesTextFormsSoNumbersAndStringsAreInterchangeable() {
        assertTrue(holds("http.status", "eq", 503));
        assertTrue(holds("http.status", "eq", "503"));
        assertTrue(holds("status", "eq", 503));
        assertTrue(holds("http.status", "eq", 503.0));
        assertFalse(holds("http.status", "eq", 500));
        assertTrue(holds("http.status", "neq", 500));
        assertTrue(holds("retried", "eq", true));
        assertFalse(holds("service", "eq", "Payment-API"));
    }

    @Test
    void levelAndMessageAreAddressedByName() {
        assertTrue(holds("level", "eq", "ERROR"));
        assertTrue(holds("level", "in", List.of("ERROR", "FATAL")));
        assertFalse(holds("level", "in", List.of("INFO", "WARN")));
        assertTrue(holds("message", "contains", "timeout"));
        assertTrue(holds("message", "regex", "after \\d+ ms$"));
        assertFalse(holds("message", "regex", "^after"));
    }

    @Test
    void numericOperatorsConvertBothSides() {
        assertTrue(holds("http.status", "gte", 500));
        assertTrue(holds("status", "gt", 499));
        assertTrue(holds("requestTime", "gt", 3));
        assertTrue(holds("requestTime", "lte", "3.216"));
        assertFalse(holds("requestTime", "lt", 3));
        assertFalse(holds("service", "gt", 1));
    }

    @Test
    void missingFieldFailsEverythingExceptNeqAndExistsFalse() {
        for (String op : List.of("eq", "in", "contains", "regex", "gt", "gte", "lt", "lte")) {
            assertFalse(holds("absent", op, op.equals("in") ? List.of("x") : "1"), op);
        }
        assertTrue(holds("absent", "neq", "x"));
        assertFalse(holds("absent", "exists", null));
        assertFalse(holds("absent", "exists", true));
        assertTrue(holds("absent", "exists", false));
        assertTrue(holds("service", "exists", null));
        assertFalse(holds("service", "exists", false));
    }

    @Test
    void unknownOperatorAndBrokenRegexNeverHold() {
        assertFalse(holds("service", "soundsLike", "payment"));
        assertFalse(holds("service", "regex", "(unclosed"));
    }

    @Test
    void modeDecidesWhetherAllOrAnyConditionMustHold() {
        List<Condition> conditions = List.of(
                new Condition("level", "eq", "ERROR"), new Condition("service", "eq", "checkout-api"));

        assertFalse(matcher.matches(rule(MatchMode.ALL, conditions), EVENT));
        assertTrue(matcher.matches(rule(MatchMode.ANY, conditions), EVENT));
        assertTrue(matcher.matches(rule(MatchMode.ALL, List.of()), EVENT));
    }

    @Test
    void templatesRenderFieldsAndLeaveUnknownNamesEmpty() {
        Map<String, Object> variables = Map.of("service", "payment-api", "http.status", 503, "count", 500L);

        assertEquals("payment-api returned 503 x500",
                Templates.render("{{service}} returned {{ http.status }} x{{count}}", variables));
        assertEquals("[] done", Templates.render("[{{missing}}] done", variables));
        assertEquals("cost $1 \\ ok", Templates.render("cost {{price}} \\ ok", Map.of("price", "$1")));
        assertEquals("", Templates.render(null, variables));
    }

    private boolean holds(String field, String op, Object value) {
        return matcher.matches(rule(MatchMode.ALL, List.of(new Condition(field, op, value))), EVENT);
    }

    private static Rule rule(MatchMode mode, List<Condition> conditions) {
        return new Rule("rule-1", "org-1", "src-1", "Test rule", true, new Match(mode, conditions),
                null, null, null, null, null, null, null, null, null);
    }
}
