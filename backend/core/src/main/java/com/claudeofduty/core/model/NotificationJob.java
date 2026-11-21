package com.claudeofduty.core.model;

import java.time.Instant;

/** One delivery to one recipient over one channel, as queued for the notifier. */
public record NotificationJob(
        String notificationId,
        String orgId,
        String alertId,
        String channel,
        Severity severity,
        Recipient recipient,
        String subject,
        String body,
        int attempt,
        Instant receivedAt) {

    /** {@code address} is a phone number, an email address, or the user id for the "ui" channel. */
    public record Recipient(String address, String userId, String name) {
    }

    /** {@code attempt} counts delivery rounds: 1 for the first, higher after a trip through the dead-letter queue. */
    public NotificationJob withAttempt(int nextAttempt) {
        return new NotificationJob(notificationId, orgId, alertId, channel, severity, recipient, subject, body,
                nextAttempt, receivedAt);
    }
}
