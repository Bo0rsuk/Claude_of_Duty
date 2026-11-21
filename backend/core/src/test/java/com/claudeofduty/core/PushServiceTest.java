package com.claudeofduty.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.claudeofduty.core.api.ApiException;
import com.claudeofduty.core.api.Caller;
import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.PushSubscription;
import com.claudeofduty.core.port.PushSubscriptions;
import com.claudeofduty.core.push.Vapid;
import com.claudeofduty.core.service.PushService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PushServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static final Caller ANNA = new Caller("anna", "anna@example.pl", null);
    /** A real browser key shape: any valid P-256 public key and a 16-byte secret. */
    private static final String BROWSER_KEY = Vapid.Keys.generate().publicKey();
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";

    private final List<PushSubscription> stored = new ArrayList<>();
    private final PushService service = new PushService(new PushSubscriptions() {
        @Override
        public List<PushSubscription> of(String userId) {
            return stored.stream().filter(subscription -> subscription.userId().equals(userId)).toList();
        }

        @Override
        public void save(PushSubscription subscription) {
            delete(subscription.userId(), subscription.subscriptionId());
            stored.add(subscription);
        }

        @Override
        public void delete(String userId, String subscriptionId) {
            stored.removeIf(s -> s.userId().equals(userId) && s.subscriptionId().equals(subscriptionId));
        }
    }, () -> "server-public-key", Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void deviceIsRegisteredOncePerEndpoint() {
        PushSubscription first = service.register(ANNA, subscription("https://fcm.googleapis.com/fcm/send/device-1"));
        service.register(ANNA, subscription("https://fcm.googleapis.com/fcm/send/device-1"));
        service.register(ANNA, subscription("https://web.push.apple.com/device-2"));

        assertEquals(2, service.list(ANNA).size());
        assertEquals("anna", first.userId());
        assertEquals(32, first.subscriptionId().length());
        assertEquals(NOW, first.createdAt());
        assertEquals(BROWSER_KEY, first.keys().p256dh());

        service.remove(ANNA, first.subscriptionId());
        assertEquals(List.of("https://web.push.apple.com/device-2"),
                service.list(ANNA).stream().map(PushSubscription::endpoint).toList());
    }

    @Test
    void malformedSubscriptionsAreRejected() {
        for (String body : List.of(
                "{}",
                "{\"endpoint\": \"http://insecure.example/push\", \"keys\": {\"p256dh\": \"" + BROWSER_KEY + "\", \"auth\": \"" + AUTH + "\"}}",
                "{\"endpoint\": \"https://push.example/x\", \"keys\": {\"p256dh\": \"not-a-key\", \"auth\": \"" + AUTH + "\"}}",
                "{\"endpoint\": \"https://push.example/x\", \"keys\": {\"p256dh\": \"" + BROWSER_KEY + "\", \"auth\": \"c2hvcnQ\"}}",
                "{\"endpoint\": \"https://push.example/x\"}")) {
            ApiException error = assertThrows(ApiException.class, () -> service.register(ANNA, Json.tree(body)), body);
            assertEquals(400, error.status(), body);
        }
        assertTrue(stored.isEmpty());
    }

    @Test
    void missingServerKeyIsReportedAsNotConfigured() {
        assertEquals("server-public-key", service.publicKey());

        PushService unconfigured = new PushService(null, () -> {
            throw new IllegalStateException("ParameterNotFound");
        }, Clock.systemUTC());
        ApiException error = assertThrows(ApiException.class, unconfigured::publicKey);
        assertEquals(503, error.status());
        assertEquals("PUSH_NOT_CONFIGURED", error.code());
    }

    private static com.fasterxml.jackson.databind.JsonNode subscription(String endpoint) {
        return Json.tree("{\"endpoint\": \"" + endpoint + "\", \"expirationTime\": null, \"keys\": {\"p256dh\": \""
                + BROWSER_KEY + "\", \"auth\": \"" + AUTH + "\"}}");
    }
}
