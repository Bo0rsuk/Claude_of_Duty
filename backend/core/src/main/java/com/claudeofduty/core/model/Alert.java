package com.claudeofduty.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** A group of matching events presented to people as one incident. */
public record Alert(
        String alertId,
        String orgId,
        String ruleId,
        String ruleName,
        String sourceId,
        String fingerprint,
        Severity severity,
        Status status,
        String title,
        String body,
        Map<String, Object> fields,
        long count,
        Instant firstSeenAt,
        Instant lastSeenAt,
        NormalizedEvent sampleEvent,
        String runbookId,
        String assigneeId,
        String acknowledgedBy,
        Instant acknowledgedAt,
        String resolvedBy,
        Instant resolvedAt,
        Timing timing,
        /** Positions of the runbook checklist steps that were ticked off for this alert, counted from 0. */
        List<Integer> checkedSteps) {

    /** OPEN and ACKNOWLEDGED alerts absorb further matching events; RESOLVED is final. */
    public enum Status {
        OPEN, ACKNOWLEDGED, RESOLVED
    }

    /** Latency stamps of the alert's first event. */
    public record Timing(Instant receivedAt, Instant processedAt) {
    }

    /** The form sent to the live feed, which does not need the full first event. */
    public Alert withoutSampleEvent() {
        return new Alert(alertId, orgId, ruleId, ruleName, sourceId, fingerprint, severity, status, title, body,
                fields, count, firstSeenAt, lastSeenAt, null, runbookId, assigneeId, acknowledgedBy, acknowledgedAt,
                resolvedBy, resolvedAt, timing, checkedSteps);
    }
}
