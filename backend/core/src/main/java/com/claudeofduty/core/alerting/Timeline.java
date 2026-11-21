package com.claudeofduty.core.alerting;

import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.TimelineEntry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Creates timeline entries with ids that sort in the order the entries happened. */
public final class Timeline {

    private Timeline() {
    }

    public static TimelineEntry entry(String alertId, TimelineEntry.Type type, TimelineEntry.Actor actor,
            Map<String, Object> detail, Instant now) {
        String entryId = Json.timestamp(now) + "#" + UUID.randomUUID().toString().substring(0, 4);
        return new TimelineEntry(alertId, entryId, now, type, actor, detail);
    }
}
