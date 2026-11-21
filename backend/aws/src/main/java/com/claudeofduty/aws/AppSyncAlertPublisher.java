package com.claudeofduty.aws;

import com.claudeofduty.core.model.Alert;
import com.claudeofduty.core.port.AlertPublisher;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/** Sends alert changes to the organization's live feed channel, /alerts/{orgId}. */
public final class AppSyncAlertPublisher implements AlertPublisher {

    private final AppSyncEventsClient events;
    private final Clock clock;

    public AppSyncAlertPublisher(AppSyncEventsClient events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    /**
     * The alert is already stored when this runs, so a failed publish is logged
     * and swallowed: failing the batch would process its events a second time.
     */
    @Override
    public void publish(String type, Alert alert) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", type);
        message.put("alert", alert.withoutSampleEvent());
        message.put("publishedAt", clock.instant());
        try {
            events.publish("/alerts/" + alert.orgId(), message);
        } catch (RuntimeException e) {
            System.err.println("Could not publish " + type + " for alert " + alert.alertId() + ": " + e);
        }
    }
}
