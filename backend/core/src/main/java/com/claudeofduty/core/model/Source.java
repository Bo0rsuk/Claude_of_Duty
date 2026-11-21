package com.claudeofduty.core.model;

import java.time.Instant;

/** A data source: how payloads arrive (connector) and how they are turned into events (parser). */
public record Source(
        String sourceId,
        String orgId,
        String name,
        boolean enabled,
        Connector connector,
        ParserConfig parser,
        String ingestToken,
        Instant createdAt,
        Instant updatedAt) {

    public static final String WEBHOOK = "webhook";
    public static final String HTTP_PULL = "http-pull";
    public static final String KAFKA = "kafka";

    /** Fields are type-specific; the ones a connector type does not use are null. */
    public record Connector(
            String type,
            String url,
            Integer intervalSeconds,
            String itemsPath,
            String cursorParam,
            String cursorFrom,
            String bootstrapServers,
            String topic,
            String groupId) {
    }

    public record ParserConfig(
            String format,
            String pattern,
            String split,
            String eventTime,
            String timeFormat,
            String level,
            String message) {
    }
}
