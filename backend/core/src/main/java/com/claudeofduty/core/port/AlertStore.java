package com.claudeofduty.core.port;

import com.claudeofduty.core.model.Alert;
import com.claudeofduty.core.model.TimelineEntry;

public interface AlertStore {

    /**
     * Atomically finds the active alert for a fingerprint, registering
     * {@code candidateAlertId} when there is none. This is what makes many
     * identical events end up in one alert, even across concurrent batches.
     *
     * @return the id of the active alert, which equals {@code candidateAlertId} when it was just registered
     */
    String claim(String orgId, String fingerprint, String candidateAlertId);

    /**
     * Adds events to an alert, creating the alert if it does not exist yet.
     *
     * Two processors can reach this for the same alert at the same moment: the one
     * that claimed the fingerprint and another that found the claim. So this must be
     * one atomic write that neither overwrites what the other stored nor loses a count:
     * {@code alert.count()} is added to the stored count, and the other fields are
     * written only if the alert is new.
     *
     * @return the alert as stored after the write
     */
    Alert addEvents(Alert alert);

    void appendTimeline(TimelineEntry entry);
}
