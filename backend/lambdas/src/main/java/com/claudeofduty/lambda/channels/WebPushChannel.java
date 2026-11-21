package com.claudeofduty.lambda.channels;

import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.NotificationJob;
import com.claudeofduty.core.model.PushSubscription;
import com.claudeofduty.core.model.Severity;
import com.claudeofduty.core.notify.NotificationChannel;
import com.claudeofduty.core.notify.PermanentDeliveryException;
import com.claudeofduty.core.port.PushSubscriptions;
import com.claudeofduty.core.push.P256;
import com.claudeofduty.core.push.Vapid;
import com.claudeofduty.core.push.WebPushEncryption;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Push notifications to browsers and installed web apps, through the push
 * service each browser subscribed with (Web Push, RFC 8030). The message
 * reaches the device even when the application is closed.
 */
public final class WebPushChannel implements NotificationChannel {

    /** How long a push service keeps the message for a device that is offline. */
    private static final String TTL_SECONDS = "3600";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final PushSubscriptions subscriptions;
    private final Supplier<Vapid.Keys> keys;
    private final String subject;
    private final String appUrl;
    private final HttpClient http;
    private final Clock clock;

    /**
     * @param keys    the server's VAPID keys, fetched when a message is sent
     * @param subject a "mailto:" or "https:" contact that push services require
     */
    public WebPushChannel(PushSubscriptions subscriptions, Supplier<Vapid.Keys> keys, String subject, String appUrl,
            Clock clock) {
        this.subscriptions = subscriptions;
        this.keys = keys;
        this.subject = subject;
        this.appUrl = appUrl;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.clock = clock;
    }

    /** Sends to every device of the user. One reachable device is enough for the delivery to count. */
    @Override
    public Map<String, Object> send(NotificationJob job) {
        List<PushSubscription> devices = subscriptions.of(job.recipient().address());
        if (devices.isEmpty()) {
            throw new PermanentDeliveryException("The user has no device registered for push notifications");
        }
        byte[] message = Json.write(message(job)).getBytes(StandardCharsets.UTF_8);
        int delivered = 0;
        List<String> problems = new ArrayList<>();
        for (PushSubscription device : devices) {
            int status = post(device, message, job.severity());
            if (status >= 200 && status < 300) {
                delivered++;
            } else if (status == 404 || status == 410) {
                // The browser unsubscribed or the app was uninstalled: forget the device.
                subscriptions.delete(device.userId(), device.subscriptionId());
            } else {
                problems.add("HTTP " + status);
            }
        }
        if (delivered > 0) {
            return Map.of("devices", delivered);
        }
        if (problems.isEmpty()) {
            throw new PermanentDeliveryException("All of the user's devices have unsubscribed from push notifications");
        }
        throw new IllegalStateException("Push services refused the message: " + String.join(", ", problems));
    }

    /** What the application's service worker receives and turns into a system notification. */
    private Map<String, Object> message(NotificationJob job) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("title", job.subject());
        message.put("body", job.body());
        message.put("alertId", job.alertId());
        message.put("severity", job.severity());
        message.put("url", appUrl + "/alerts/" + job.alertId());
        return message;
    }

    /** @return the push service's HTTP status, or 0 when it could not be reached */
    private int post(PushSubscription device, byte[] message, Severity severity) {
        try {
            URI endpoint = URI.create(device.endpoint());
            byte[] body = WebPushEncryption.encrypt(message,
                    P256.fromBase64Url(device.keys().p256dh()), P256.fromBase64Url(device.keys().auth()));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/octet-stream")
                    .header("Content-Encoding", "aes128gcm")
                    .header("TTL", TTL_SECONDS)
                    // Lets the push service wake a sleeping phone for critical alerts.
                    .header("Urgency", severity == Severity.CRITICAL ? "high" : "normal")
                    .header("Authorization", Vapid.authorization(keys.get(), subject, endpoint, clock.instant()))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Push to " + device.subscriptionId() + " failed: " + e);
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        }
    }
}
