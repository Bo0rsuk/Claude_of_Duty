package com.claudeofduty.lambda.channels;

import com.claudeofduty.core.model.NotificationJob;
import com.claudeofduty.core.notify.NotificationChannel;
import com.claudeofduty.aws.AppSyncEventsClient;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/** Pop-up in the web application, pushed to the user's channel /notifications/{orgId}/{userId}. */
public final class UiChannel implements NotificationChannel {

    private final AppSyncEventsClient events;
    private final Clock clock;

    public UiChannel(AppSyncEventsClient events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Override
    public Map<String, Object> send(NotificationJob job) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "notification");
        message.put("alertId", job.alertId());
        message.put("severity", job.severity());
        message.put("title", job.subject());
        message.put("body", job.body());
        message.put("publishedAt", clock.instant());
        events.publish("/notifications/" + job.orgId() + "/" + job.recipient().address(), message);
        return Map.of();
    }
}
