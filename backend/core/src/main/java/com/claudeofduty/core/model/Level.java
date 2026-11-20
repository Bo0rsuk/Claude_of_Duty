package com.claudeofduty.core.model;

import java.util.Locale;

/** What a source reported about an event. Not to be confused with {@link Severity}, which a rule assigns. */
public enum Level {
    DEBUG, INFO, WARN, ERROR, FATAL, UNKNOWN;

    /** Normalizes the spellings used by different sources. Anything unrecognized is {@code UNKNOWN}. */
    public static Level parse(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "DEBUG" -> DEBUG;
            case "INFO" -> INFO;
            case "WARN", "WARNING" -> WARN;
            case "ERROR", "ERR" -> ERROR;
            case "FATAL", "CRITICAL" -> FATAL;
            default -> UNKNOWN;
        };
    }
}
