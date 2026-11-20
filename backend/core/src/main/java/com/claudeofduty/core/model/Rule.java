package com.claudeofduty.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Decides which events of a source become alerts, who is notified and how the alert looks. */
public record Rule(
        String ruleId,
        String orgId,
        String sourceId,
        String name,
        boolean enabled,
        Match match,
        Severity severity,
        List<Route> routing,
        Presentation presentation,
        List<String> groupBy,
        Throttle throttle,
        String runbookId,
        Instant createdAt,
        Instant updatedAt,
        String updatedBy) {

    public record Match(MatchMode mode, List<Condition> conditions) {
    }

    public enum MatchMode {
        @JsonProperty("all") ALL,
        @JsonProperty("any") ANY
    }

    /** {@code value} is a string, number, boolean or list, depending on {@code op}. */
    public record Condition(String field, String op, Object value) {
    }

    public record Route(String channel, Target target) {
    }

    /** {@code type} is "group" or "user" (with {@code id}), or "address" (with {@code value}). */
    public record Target(String type, String id, String value) {
    }

    public record Presentation(
            String title,
            String body,
            List<String> fields,
            Map<String, ChannelTemplate> channels) {
    }

    public record ChannelTemplate(String subject, String body) {
    }

    public record Throttle(Limit perRule, Limit perRecipient) {
    }

    public record Limit(int max, int windowSeconds) {
    }
}
