package com.claudeofduty.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
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
        Instant receivedAt,
        boolean escalated) {

    /** Jackson builds jobs through this, the full form. */
    @JsonCreator
    public NotificationJob {
    }

    /** A job that is delivered right away, whatever happened to the alert since. */
    public NotificationJob(String notificationId, String orgId, String alertId, String channel, Severity severity,
            Recipient recipient, String subject, String body, int attempt, Instant receivedAt) {
        this(notificationId, orgId, alertId, channel, severity, recipient, subject, body, attempt, receivedAt, false);
    }

    /**
     * A later step of a rule's escalation: {@code escalated} jobs wait in the queue and are
     * dropped when the alert has been acknowledged or resolved in the meantime.
     */
    public NotificationJob asEscalated() {
        return new NotificationJob(notificationId, orgId, alertId, channel, severity, recipient, subject, body,
                attempt, receivedAt, true);
    }

    /** {@code address} is a phone number, an email address, or the user id for the "ui" channel. */
    public record Recipient(String address, String userId, String name) {
    }

    /** {@code attempt} counts delivery rounds: 1 for the first, higher after a trip through the dead-letter queue. */
    public NotificationJob withAttempt(int nextAttempt) {
        return new NotificationJob(notificationId, orgId, alertId, channel, severity, recipient, subject, body,
                nextAttempt, receivedAt, escalated);
    }
}
