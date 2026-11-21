package com.claudeofduty.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
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
        String updatedBy,
        Delivery delivery,
        Escalation escalation) {

    /** Jackson builds rules through this, the full form. */
    @JsonCreator
    public Rule {
    }

    /** A rule that notifies on all its channels at once. */
    public Rule(String ruleId, String orgId, String sourceId, String name, boolean enabled, Match match,
            Severity severity, List<Route> routing, Presentation presentation, List<String> groupBy,
            Throttle throttle, String runbookId, Instant createdAt, Instant updatedAt, String updatedBy,
            Delivery delivery) {
        this(ruleId, orgId, sourceId, name, enabled, match, severity, routing, presentation, groupBy, throttle,
                runbookId, createdAt, updatedAt, updatedBy, delivery, null);
    }

    /**
     * Notifies channel by channel instead of all at once: in-app and push first, then email,
     * then SMS, then a phone call, each {@code stepSeconds} after the one before. Channels the
     * rule does not use are skipped. A step is dropped when the alert is no longer open by then.
     */
    public record Escalation(int stepSeconds) {
    }

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

    /**
     * The hours in which the routing applies. Outside them every recipient is notified on
     * {@code outsideWindow} instead, or not at all when it is null.
     */
    public record Delivery(Window window, String outsideWindow) {
    }

    /** Times of day as "HH:mm" in the given IANA time zone (UTC when null). {@code to} before {@code from} spans midnight. */
    public record Window(String from, String to, String timezone) {
    }
}
