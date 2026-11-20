package com.claudeofduty.core.parser;

import com.claudeofduty.core.model.Level;
import com.claudeofduty.core.model.NormalizedEvent;
import com.claudeofduty.core.model.RawRecord;
import com.claudeofduty.core.model.Source.ParserConfig;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies a source's parser configuration to a raw record. A payload that
 * cannot be parsed is not lost: it becomes one event with no fields, whose
 * message is the raw payload.
 */
public final class EventNormalizer {

    private final ParserRegistry registry;
    private final Map<ParserConfig, Parser> parsers = new ConcurrentHashMap<>();

    public EventNormalizer(ParserRegistry registry) {
        this.registry = registry;
    }

    public List<NormalizedEvent> normalize(RawRecord record, ParserConfig config) {
        List<Map<String, Object>> parsed = parse(record.payload(), config);
        List<NormalizedEvent> events = new ArrayList<>(parsed.size());
        for (int index = 0; index < parsed.size(); index++) {
            // Derived from the record id, so a retried batch yields the same event ids.
            String eventId = record.recordId() + "-" + index;
            events.add(toEvent(eventId, record, new LinkedHashMap<>(parsed.get(index)), config));
        }
        return events;
    }

    private List<Map<String, Object>> parse(String payload, ParserConfig config) {
        if (config == null || config.format() == null) {
            return List.of(Map.of());
        }
        try {
            return parsers.computeIfAbsent(config, registry::create).parse(payload);
        } catch (UnparsablePayloadException | IllegalArgumentException e) {
            return List.of(Map.of());
        }
    }

    private static NormalizedEvent toEvent(
            String eventId, RawRecord record, Map<String, Object> fields, ParserConfig config) {
        Object time = take(fields, config == null ? null : config.eventTime());
        Object level = take(fields, config == null ? null : config.level());
        Object message = take(fields, config == null ? null : config.message());

        Instant eventTime = TimeParser.parse(time, config == null ? null : config.timeFormat())
                .orElse(record.receivedAt());
        return new NormalizedEvent(
                eventId,
                record.orgId(),
                record.sourceId(),
                eventTime,
                record.receivedAt(),
                Level.parse(level == null ? null : level.toString()),
                message == null ? record.payload() : message.toString(),
                fields,
                record.payload());
    }

    private static Object take(Map<String, Object> fields, String key) {
        return key == null ? null : fields.remove(key);
    }
}
