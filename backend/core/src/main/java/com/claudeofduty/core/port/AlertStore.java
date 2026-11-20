package com.claudeofduty.core.port;

import com.claudeofduty.core.model.Alert;
import com.claudeofduty.core.model.TimelineEntry;
import java.time.Instant;
import java.util.Optional;

public interface AlertStore {

    /**
     * Atomically finds the active alert for a fingerprint, registering
     * {@code candidateAlertId} when there is none. This is what makes many
     * identical events end up in one alert, even across concurrent batches.
     *
     * @return the id of the active alert, which equals {@code candidateAlertId} when it was just registered
     */
    String claim(String orgId, String fingerprint, String candidateAlertId);

    void create(Alert alert);

    /**
     * Adds further matching events to an existing alert.
     *
     * @return the updated alert, or empty when no alert with that id exists
     */
    Optional<Alert> addEvents(String orgId, String alertId, long count, Instant lastSeenAt);

    void appendTimeline(TimelineEntry entry);
}
