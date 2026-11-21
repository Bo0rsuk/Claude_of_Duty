package com.claudeofduty.core.model;

import java.time.Instant;

/**
 * One browser or installed web app that agreed to receive push notifications
 * for a user. A user has one subscription per device.
 */
public record PushSubscription(String userId, String subscriptionId, String endpoint, Keys keys, Instant createdAt) {

    /** The browser's encryption keys, base64url, exactly as PushSubscription.toJSON() gives them. */
    public record Keys(String p256dh, String auth) {
    }
}
