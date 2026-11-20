package com.claudeofduty.core.model;

import java.time.Instant;

/** One delivery to one recipient over one channel, as queued for the notifier. */
public record NotificationJob(
        String notificationId,
        String orgId,
        String alertId,
        String channel,
        Recipient recipient,
        String subject,
        String body,
        int attempt,
        Instant receivedAt) {

    /** {@code address} is a phone number, an email address, or the user id for the "ui" channel. */
    public record Recipient(String address, String userId, String name) {
    }
}
