package com.claudeofduty.core.model;

import java.time.Instant;

/** One unparsed payload, as connectors put it on the stream. */
public record RawRecord(String recordId, String orgId, String sourceId, Instant receivedAt, String payload) {
}
