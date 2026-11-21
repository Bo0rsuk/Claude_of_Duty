package com.claudeofduty.core.ingest;

import com.claudeofduty.core.model.RawRecord;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.port.SourceLookup;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;

/**
 * Decides whether a payload pushed to the ingest endpoint may enter the
 * pipeline: its source must exist, be an enabled webhook source, and the
 * sender must have presented that source's token.
 *
 * There are two ways in. The ingest function checks before writing to the
 * stream. The direct route, where API Gateway writes to the stream without any
 * function, cannot check, so it puts the token in an envelope and the
 * processor checks here when it reads the record.
 */
public final class WebhookIntake {

    /** Present only in envelopes written by the direct route. */
    private static final String RECEIVED_AT_MILLIS = "receivedAtMillis";

    private final SourceLookup sources;

    public WebhookIntake(SourceLookup sources) {
        this.sources = sources;
    }

    public static boolean isEnvelope(JsonNode record) {
        return record.has(RECEIVED_AT_MILLIS);
    }

    /** @return the record to process, or empty when the sender was not entitled to send it */
    public Optional<RawRecord> open(JsonNode envelope) {
        String orgId = envelope.path("orgId").asText(null);
        String sourceId = envelope.path("sourceId").asText(null);
        String payload = envelope.path("payload").asText("");
        if (orgId == null || sourceId == null || payload.isBlank()
                || !accepts(orgId, sourceId, envelope.path("token").asText(null))) {
            return Optional.empty();
        }
        return Optional.of(new RawRecord(envelope.path("recordId").asText(), orgId, sourceId,
                Instant.ofEpochMilli(envelope.path(RECEIVED_AT_MILLIS).asLong()), payload));
    }

    public boolean accepts(String orgId, String sourceId, String token) {
        return sources.find(orgId, sourceId).filter(source -> accepts(source, token)).isPresent();
    }

    private static boolean accepts(Source source, String token) {
        return source.enabled()
                && source.connector() != null
                && Source.WEBHOOK.equals(source.connector().type())
                && source.ingestToken() != null
                && token != null
                // Compares in constant time, so response timing does not leak how much of a guess was right.
                && MessageDigest.isEqual(
                        source.ingestToken().getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }
}
