package com.claudeofduty.core.service;

import com.claudeofduty.core.api.ApiException;
import com.claudeofduty.core.api.Caller;
import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.PushSubscription;
import com.claudeofduty.core.port.PushSubscriptions;
import com.claudeofduty.core.push.P256;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;

/** Registers the devices on which a user wants push notifications. */
public final class PushService {

    private static final int AUTH_SECRET_LENGTH = 16;

    private final PushSubscriptions subscriptions;
    private final Supplier<String> publicKey;
    private final Clock clock;

    /** @param publicKey the server's VAPID public key, which a browser needs in order to subscribe */
    public PushService(PushSubscriptions subscriptions, Supplier<String> publicKey, Clock clock) {
        this.subscriptions = subscriptions;
        this.publicKey = publicKey;
        this.clock = clock;
    }

    public String publicKey() {
        try {
            return publicKey.get();
        } catch (RuntimeException e) {
            throw new ApiException(503, "PUSH_NOT_CONFIGURED", "Push notifications are not set up on this server");
        }
    }

    public List<PushSubscription> list(Caller caller) {
        return subscriptions.of(caller.userId());
    }

    /**
     * @param body what the browser's PushSubscription.toJSON() returns: endpoint and keys
     * @return the stored subscription; registering the same device again replaces its entry
     */
    public PushSubscription register(Caller caller, JsonNode body) {
        PushSubscription request;
        try {
            request = Json.convert(body, PushSubscription.class);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        if (request == null || request.endpoint() == null || request.keys() == null
                || !isHttps(request.endpoint()) || !validKeys(request.keys())) {
            throw invalid();
        }
        PushSubscription subscription = new PushSubscription(
                caller.userId(), id(request.endpoint()), request.endpoint(), request.keys(), clock.instant());
        subscriptions.save(subscription);
        return subscription;
    }

    public void remove(Caller caller, String subscriptionId) {
        subscriptions.delete(caller.userId(), subscriptionId);
    }

    /** Derived from the endpoint, so one device always maps to the same entry. */
    private static String id(String endpoint) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isHttps(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            return "https".equals(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean validKeys(PushSubscription.Keys keys) {
        try {
            P256.publicKey(P256.fromBase64Url(keys.p256dh()));
            return P256.fromBase64Url(keys.auth()).length == AUTH_SECRET_LENGTH;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static ApiException invalid() {
        return ApiException.invalid(
                "Send the browser's push subscription: { endpoint, keys: { p256dh, auth } } with an https endpoint");
    }
}
