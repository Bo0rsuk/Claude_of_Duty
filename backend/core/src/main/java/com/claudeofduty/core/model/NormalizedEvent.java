package com.claudeofduty.core.model;

import java.time.Instant;
import java.util.Map;

/** The common shape every source format is parsed into. Rules are evaluated against this. */
public record NormalizedEvent(
        String eventId,
        String orgId,
        String sourceId,
        Instant eventTime,
        Instant receivedAt,
        Level level,
        String message,
        Map<String, Object> fields,
        String raw) {
}
