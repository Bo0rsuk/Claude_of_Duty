package com.claudeofduty.core.parser;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Reads event times in the formats a parser configuration may name. */
final class TimeParser {

    static final String ISO_8601 = "iso8601";
    static final String EPOCH_MILLIS = "epoch_millis";

    private static final Map<String, DateTimeFormatter> FORMATTERS = new ConcurrentHashMap<>();

    private TimeParser() {
    }

    /** @return empty when the value is missing or not in the given format */
    static Optional<Instant> parse(Object value, String format) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            if (format == null || ISO_8601.equals(format)) {
                return Optional.of(Instant.parse(value.toString()));
            }
            if (EPOCH_MILLIS.equals(format)) {
                long millis = value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
                return Optional.of(Instant.ofEpochMilli(millis));
            }
            return Optional.of(parsePattern(value.toString(), format));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** A value without an offset is taken as UTC. */
    private static Instant parsePattern(String text, String pattern) {
        DateTimeFormatter formatter =
                FORMATTERS.computeIfAbsent(pattern, key -> DateTimeFormatter.ofPattern(key, Locale.ENGLISH));
        TemporalAccessor parsed = formatter.parseBest(text, ZonedDateTime::from, LocalDateTime::from);
        return parsed instanceof ZonedDateTime zoned
                ? zoned.toInstant()
                : ((LocalDateTime) parsed).toInstant(ZoneOffset.UTC);
    }
}
