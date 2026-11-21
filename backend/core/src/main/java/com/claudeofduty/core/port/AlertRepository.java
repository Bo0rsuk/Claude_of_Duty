package com.claudeofduty.core.port;

import com.claudeofduty.core.api.Page;
import com.claudeofduty.core.model.Alert;
import com.claudeofduty.core.model.TimelineEntry;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** What the API needs from alert storage. The processor's side is {@link AlertStore}. */
public interface AlertRepository {

    /** Alerts of an organization, newest first. {@code cursor} is the {@code nextCursor} of the previous page. */
    Page<Alert> newest(String orgId, int limit, String cursor);

    Optional<Alert> find(String orgId, String alertId);

    /**
     * Sets the given fields of an alert, named as in the contract.
     *
     * @return the updated alert, or empty when it does not exist
     */
    Optional<Alert> update(String orgId, String alertId, Map<String, Object> changes);

    /** Entries of an alert, oldest first. */
    List<TimelineEntry> timeline(String alertId);

    void appendTimeline(TimelineEntry entry);

    /** Forgets that the fingerprint has an active alert, so the next matching event opens a new one. */
    void release(String fingerprint);
}
