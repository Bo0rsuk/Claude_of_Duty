package com.claudeofduty.lambda.channels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.NotificationJob;
import com.claudeofduty.core.model.PushSubscription;
import com.claudeofduty.core.model.Severity;
import com.claudeofduty.core.notify.PermanentDeliveryException;
import com.claudeofduty.core.port.PushSubscriptions;
import com.claudeofduty.core.push.P256;
import com.claudeofduty.core.push.Vapid;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Sends through a local server standing in for a browser's push service, then decrypts like the browser would. */
class WebPushChannelTest {

    private static final byte[] AUTH_SECRET = P256.fromBase64Url("BTBZMqHH6r4Tts7J_aSIgg");

    private final KeyPair browser = P256.generate();
    private final Vapid.Keys serverKeys = Vapid.Keys.generate();
    private final List<PushSubscription> devices = new ArrayList<>();
    private final Map<String, Map<String, String>> headersByPath = new HashMap<>();
    private final Map<String, byte[]> bodiesByPath = new HashMap<>();
    private final Map<String, Integer> statusByPath = new HashMap<>();
    private HttpServer server;
    private WebPushChannel channel;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            Map<String, String> headers = new HashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.get(0)));
            headersByPath.put(path, headers);
            bodiesByPath.put(path, exchange.getRequestBody().readAllBytes());
            exchange.sendResponseHeaders(statusByPath.getOrDefault(path, 201), -1);
            exchange.close();
        });
        server.start();
        channel = new WebPushChannel(new PushSubscriptions() {
            @Override
            public List<PushSubscription> of(String userId) {
                return devices.stream().filter(device -> device.userId().equals(userId)).toList();
            }

            @Override
            public void save(PushSubscription subscription) {
                devices.add(subscription);
            }

            @Override
            public void delete(String userId, String subscriptionId) {
                devices.removeIf(device -> device.subscriptionId().equals(subscriptionId));
            }
        }, () -> serverKeys, "mailto:alerts@example.pl", "https://app.example.pl", Clock.systemUTC());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void deviceReceivesAnEncryptedMessageItCanRead() throws Exception {
        register("phone");

        Map<String, Object> details = channel.send(job(Severity.CRITICAL));

        assertEquals(Map.of("devices", 1), details);
        Map<String, String> headers = headersByPath.get("/phone");
        assertEquals("aes128gcm", headers.get("content-encoding"));
        assertEquals("3600", headers.get("ttl"));
        assertEquals("high", headers.get("urgency"));
        assertTrue(headers.get("authorization").startsWith("vapid t="));
        assertTrue(headers.get("authorization").endsWith(", k=" + serverKeys.publicKey()));

        JsonNode message = Json.tree(decryptLikeTheBrowser(bodiesByPath.get("/phone")));
        assertEquals("payment-api: DB_TIMEOUT", message.get("title").asText());
        assertEquals("Timeout on payment-api-1", message.get("body").asText());
        assertEquals("alert-1", message.get("alertId").asText());
        assertEquals("CRITICAL", message.get("severity").asText());
        assertEquals("https://app.example.pl/alerts/alert-1", message.get("url").asText());
    }

    @Test
    void oneReachableDeviceIsEnoughAndUnsubscribedOnesAreForgotten() {
        register("phone");
        register("old-laptop");
        register("tablet");
        statusByPath.put("/old-laptop", 410);
        statusByPath.put("/tablet", 500);

        assertEquals(Map.of("devices", 1), channel.send(job(Severity.WARNING)));

        assertEquals(List.of("phone", "tablet"), devices.stream().map(PushSubscription::subscriptionId).toList());
        assertEquals("normal", headersByPath.get("/phone").get("urgency"));
    }

    @Test
    void failuresAreSplitIntoRetryableAndPermanent() {
        assertThrows(PermanentDeliveryException.class, () -> channel.send(job(Severity.INFO)));

        register("phone");
        statusByPath.put("/phone", 503);
        IllegalStateException retryable = assertThrows(IllegalStateException.class, () -> channel.send(job(Severity.INFO)));
        assertTrue(retryable.getMessage().contains("HTTP 503"));
        assertEquals(1, devices.size());

        statusByPath.put("/phone", 404);
        assertThrows(PermanentDeliveryException.class, () -> channel.send(job(Severity.INFO)));
        assertTrue(devices.isEmpty());
    }

    private void register(String device) {
        devices.add(new PushSubscription("anna", device,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/" + device,
                new PushSubscription.Keys(P256.toBase64Url(P256.encode((ECPublicKey) browser.getPublic())),
                        P256.toBase64Url(AUTH_SECRET)),
                Instant.now()));
    }

    private static NotificationJob job(Severity severity) {
        return new NotificationJob("n-1", "org-1", "alert-1", "push", severity,
                new NotificationJob.Recipient("anna", "anna", "Anna"), "payment-api: DB_TIMEOUT",
                "Timeout on payment-api-1", 1, Instant.now());
    }

    /** The receiving side of RFC 8291, with the browser's private key. */
    private String decryptLikeTheBrowser(byte[] body) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buffer.get(salt);
        assertEquals(4096, buffer.getInt());
        byte[] senderPublic = new byte[buffer.get()];
        buffer.get(senderPublic);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init((ECPrivateKey) browser.getPrivate());
        agreement.doPhase(P256.publicKey(senderPublic), true);
        byte[] browserPublic = P256.encode((ECPublicKey) browser.getPublic());
        byte[] inputKey = hkdf(AUTH_SECRET, agreement.generateSecret(),
                concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), browserPublic, senderPublic), 32);
        byte[] key = hkdf(salt, inputKey, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(salt, inputKey, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        assertEquals(2, padded[padded.length - 1]);
        return new String(padded, 0, padded.length - 1, StandardCharsets.UTF_8);
    }

    private static byte[] hkdf(byte[] salt, byte[] inputKey, byte[] info, int length) throws Exception {
        byte[] pseudoRandomKey = hmac(salt, inputKey);
        return Arrays.copyOf(hmac(pseudoRandomKey, concat(info, new byte[] {1})), length);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        ByteBuffer buffer = ByteBuffer.allocate(Arrays.stream(parts).mapToInt(part -> part.length).sum());
        Arrays.stream(parts).forEach(buffer::put);
        return buffer.array();
    }
}
