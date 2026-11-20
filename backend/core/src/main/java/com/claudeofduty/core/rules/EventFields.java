package com.claudeofduty.core.rules;

import com.claudeofduty.core.model.NormalizedEvent;

/** Resolves the names that conditions and templates use to address parts of an event. */
public final class EventFields {

    public static final String LEVEL = "level";
    public static final String MESSAGE = "message";

    private EventFields() {
    }

    /** @return the value, or null when the event has no such field */
    public static Object value(NormalizedEvent event, String name) {
        return switch (name) {
            case LEVEL -> event.level() == null ? null : event.level().name();
            case MESSAGE -> event.message();
            default -> event.fields() == null ? null : event.fields().get(name);
        };
    }
}
