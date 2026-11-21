package com.claudeofduty.core.service;

import com.claudeofduty.core.api.ApiException;
import com.claudeofduty.core.api.Caller;
import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.NormalizedEvent;
import com.claudeofduty.core.model.Rule.Match;
import com.claudeofduty.core.model.Severity;
import com.claudeofduty.core.port.TextModel;
import com.claudeofduty.core.rules.EventFields;
import com.claudeofduty.core.rules.MatchValidator;
import com.claudeofduty.core.rules.Operators;
import com.claudeofduty.core.service.RulePreviewService.Preview;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a plain-language goal ("wake me when payments return 5xx") into rule
 * conditions, so nobody has to remember the condition syntax.
 *
 * The model sees the field names and example values of the source's recent
 * events. Its answer is validated like a hand-written rule and tried on those
 * same events, so the user sees at once how much the suggestion would catch.
 */
public final class RuleAssistantService {

    private static final int EXAMPLE_VALUES = 5;
    private static final int MAX_VALUE_LENGTH = 80;

    static final String INSTRUCTIONS = """
            You help on-call engineers write alert rules. Turn the engineer's goal into the condition of a rule \
            that is evaluated against every event of one data source.

            Answer with one JSON object and nothing else:
            {"mode": "all" or "any", "conditions": [{"field": string, "op": string, "value": any}], \
            "severity": "CRITICAL" or "WARNING" or "INFO", "explanation": string}

            Operators for "op":
            - "eq", "neq": the field equals or differs from value (a string, number or boolean)
            - "in": the field equals one of the values; value is an array
            - "contains": the field contains value as a substring
            - "regex": value is a Java regular expression that must be found in the field
            - "gt", "gte", "lt", "lte": numeric comparison; value is a number
            - "exists": value is true if the field must be present, false if it must be absent

            Requirements:
            - Use only the fields listed in the input, spelled exactly as listed.
            - "level" is one of DEBUG, INFO, WARN, ERROR, FATAL, UNKNOWN.
            - "mode" "all" means every condition must hold, "any" means at least one. Conditions cannot be nested, \
            and one mode applies to all of them.
            - When the goal narrows things down to one service, host or similar AND lists alternatives within it, \
            use "all": one condition for what it is narrowed down to, and one "in" condition (or one on "level") for \
            the alternatives. For example "service A returns server errors or runs out of memory" is \
            service eq A AND code in [the matching codes], not three conditions joined by "any".
            - Use "any" only when each condition on its own is enough to raise the alert.
            - Use as few conditions as express the goal. Do not add conditions the goal does not ask for.
            - "severity": CRITICAL when someone must act immediately, WARNING when it should be looked at soon, \
            INFO when it is only worth knowing.
            - "explanation": one or two sentences, in the language of the goal, saying which events the rule catches.
            """;

    public record Suggestion(Match match, Severity severity, String explanation, Preview preview, List<String> warnings) {
    }

    private final RulePreviewService previews;
    private final TextModel model;
    private final Operators operators;

    public RuleAssistantService(RulePreviewService previews, TextModel model, Operators operators) {
        this.previews = previews;
        this.model = model;
        this.operators = operators;
    }

    /** @param sampleEvent an event the user picked as an example, or null */
    public Suggestion suggest(Caller caller, String sourceId, NormalizedEvent sampleEvent, String goal) {
        if (goal == null || goal.isBlank()) {
            throw ApiException.invalid("Describe what the rule should catch in \"goal\"");
        }
        List<NormalizedEvent> recent = previews.events(caller, sourceId, RulePreviewService.PREVIEW_EVENTS);
        List<NormalizedEvent> examples = new ArrayList<>();
        if (sampleEvent != null) {
            examples.add(sampleEvent);
        }
        examples.addAll(recent);
        Map<String, Set<String>> fields = fieldsOf(examples);

        JsonNode answer = ask(input(goal.strip(), fields, sampleEvent));
        Match match = readMatch(answer);
        return new Suggestion(match, readSeverity(answer), answer.path("explanation").asText(""),
                previews.preview(recent, match), warnings(match, fields));
    }

    private JsonNode ask(String input) {
        String text;
        try {
            text = model.completeJson(INSTRUCTIONS, input);
        } catch (RuntimeException e) {
            throw new ApiException(502, "AI_UNAVAILABLE", "The assistant could not be reached: " + e.getMessage());
        }
        try {
            JsonNode answer = Json.tree(stripCodeFence(text));
            if (!answer.isObject()) {
                throw invalidAnswer("it is not a JSON object");
            }
            return answer;
        } catch (UncheckedIOException e) {
            throw invalidAnswer("it is not valid JSON");
        }
    }

    private Match readMatch(JsonNode answer) {
        try {
            ObjectNode shape = Json.object();
            shape.set("mode", answer.path("mode"));
            shape.set("conditions", answer.path("conditions"));
            Match match = Json.convert(shape, Match.class);
            if (match.mode() == null || match.conditions() == null || match.conditions().isEmpty()) {
                throw invalidAnswer("it has no conditions");
            }
            MatchValidator.validate(match, operators);
            return match;
        } catch (IllegalArgumentException e) {
            throw invalidAnswer(String.valueOf(e.getMessage()).lines().findFirst().orElse("it has the wrong shape"));
        }
    }

    private static Severity readSeverity(JsonNode answer) {
        try {
            return Severity.valueOf(answer.path("severity").asText());
        } catch (IllegalArgumentException e) {
            return Severity.WARNING;
        }
    }

    /** What the model is given: the goal and what the source's events actually look like. */
    static String input(String goal, Map<String, Set<String>> fields, NormalizedEvent sampleEvent) {
        StringBuilder input = new StringBuilder("Goal: ").append(goal).append("\n\n");
        if (fields.isEmpty()) {
            input.append("No events of this source have been seen yet. Only \"level\" and \"message\" are known to exist.\n");
        } else {
            input.append("Fields seen in recent events of this source, with example values:\n");
            fields.forEach((name, values) -> input.append("- ").append(name).append(": ")
                    .append(String.join(" | ", values)).append('\n'));
        }
        if (sampleEvent != null) {
            input.append("\nThe engineer picked this event as an example of what to catch:\n")
                    .append(Json.write(variablesOf(sampleEvent))).append('\n');
        }
        return input.toString();
    }

    /** Field names in the order first seen, each with a few distinct example values. */
    static Map<String, Set<String>> fieldsOf(List<NormalizedEvent> events) {
        Map<String, Set<String>> fields = new LinkedHashMap<>();
        for (NormalizedEvent event : events) {
            variablesOf(event).forEach((name, value) -> {
                Set<String> values = fields.computeIfAbsent(name, key -> new LinkedHashSet<>());
                if (value != null && values.size() < EXAMPLE_VALUES) {
                    String text = value.toString();
                    values.add(text.length() > MAX_VALUE_LENGTH ? text.substring(0, MAX_VALUE_LENGTH) + "..." : text);
                }
            });
        }
        return fields;
    }

    private static Map<String, Object> variablesOf(NormalizedEvent event) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put(EventFields.LEVEL, EventFields.value(event, EventFields.LEVEL));
        variables.put(EventFields.MESSAGE, event.message());
        if (event.fields() != null) {
            variables.putAll(event.fields());
        }
        return variables;
    }

    /** A condition on a field no recent event had is suspicious, but may be right for a rare event. */
    private static List<String> warnings(Match match, Map<String, Set<String>> fields) {
        if (fields.isEmpty()) {
            return List.of();
        }
        return match.conditions().stream()
                .map(condition -> condition.field())
                .filter(field -> !fields.containsKey(field))
                .distinct()
                .map(field -> "Field \"" + field + "\" does not appear in the recent events of this source")
                .toList();
    }

    private static String stripCodeFence(String text) {
        String trimmed = text.strip();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(trimmed.indexOf('\n') + 1);
            int end = trimmed.lastIndexOf("```");
            trimmed = end >= 0 ? trimmed.substring(0, end) : trimmed;
        }
        return trimmed.strip();
    }

    private static ApiException invalidAnswer(String reason) {
        return new ApiException(502, "AI_INVALID_ANSWER", "The assistant's answer cannot be used: " + reason);
    }
}
