package com.claudeofduty.core.notify;

import com.claudeofduty.core.model.NotificationJob;
import java.util.Map;

/** Delivers a notification over one medium, e.g. SMS or email. */
public interface NotificationChannel {

    /**
     * @return details worth keeping in the alert timeline, e.g. the provider's message id
     * @throws RuntimeException when the delivery failed and should be retried
     */
    Map<String, Object> send(NotificationJob job);
}
