package com.claudeofduty.core.model;

import java.time.Instant;
import java.util.Map;

/** One line of an alert's history. */
public record TimelineEntry(
        String alertId,
        String entryId,
        Instant at,
        Type type,
        Actor actor,
        Map<String, Object> detail) {

    public enum Type {
        CREATED,
        NOTIFICATION_SENT,
        NOTIFICATION_FAILED,
        NOTIFICATION_SUPPRESSED,
        NOTIFICATIONS_RESENT,
        ACKNOWLEDGED,
        ASSIGNED,
        RESOLVED,
        NOTE
    }

    /** {@code type} is "system" or "user"; {@code id} and {@code name} are set for users only. */
    public record Actor(String type, String id, String name) {

        public static Actor system() {
            return new Actor("system", null, null);
        }

        public static Actor user(String id, String name) {
            return new Actor("user", id, name);
        }
    }
}
