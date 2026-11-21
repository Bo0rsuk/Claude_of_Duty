package com.claudeofduty.core.push;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.claudeofduty.core.json.Json;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class WebPushTest {

    /** The example from RFC 8291, section 5 and appendix A. */
    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    private static final String SENDER_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String SENDER_PUBLIC =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String BROWSER_PUBLIC =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String EXPECTED_CIPHERTEXT =
            "8pfeW0KbunFT06SuDKoJH9Ql87S1QUrd" + "irN6GcG7sFz1y1sqLgVi1VhjVkHsUoEs" + "bI_0LpXMuGvnzQ";

    @Test
    void encryptionReproducesTheExampleFromTheStandard() {
        byte[] body = WebPushEncryption.encrypt(
                PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                P256.fromBase64Url(BROWSER_PUBLIC),
                P256.fromBase64Url(AUTH_SECRET),
                P256.privateKey(P256.fromBase64Url(SENDER_PRIVATE)),
                P256.fromBase64Url(SENDER_PUBLIC),
                P256.fromBase64Url(SALT));

        // The RFC: "The salt, record size of 4096, and application server public key produce an 86-octet header."
        ByteBuffer header = ByteBuffer.wrap(body, 0, 86);
        byte[] salt = new byte[16];
        header.get(salt);
        assertArrayEquals(P256.fromBase64Url(SALT), salt);
        assertEquals(4096, header.getInt());
        assertEquals(65, header.get());
        byte[] keyId = new byte[65];
        header.get(keyId);
        assertArrayEquals(P256.fromBase64Url(SENDER_PUBLIC), keyId);

        assertEquals(EXPECTED_CIPHERTEXT, P256.toBase64Url(Arrays.copyOfRange(body, 86, body.length)));
        assertEquals(144, body.length);
    }

    @Test
    void everyMessageUsesAFreshKeyAndSalt() {
        byte[] plaintext = "alert".getBytes(StandardCharsets.UTF_8);
        byte[] browserKey = P256.fromBase64Url(BROWSER_PUBLIC);
        byte[] authSecret = P256.fromBase64Url(AUTH_SECRET);

        byte[] first = WebPushEncryption.encrypt(plaintext, browserKey, authSecret);
        byte[] second = WebPushEncryption.encrypt(plaintext, browserKey, authSecret);

        assertEquals(86 + plaintext.length + 1 + 16, first.length);
        assertNotEquals(P256.toBase64Url(first), P256.toBase64Url(second));
    }

    @Test
    void malformedBrowserKeyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> WebPushEncryption.encrypt(
                new byte[] {1}, new byte[] {4, 1, 2, 3}, P256.fromBase64Url(AUTH_SECRET)));
    }

    @Test
    void keysSurviveTheRawEncodingUsedInConfiguration() {
        Vapid.Keys keys = Vapid.Keys.generate();

        byte[] publicKey = P256.fromBase64Url(keys.publicKey());
        assertEquals(65, publicKey.length);
        assertEquals(4, publicKey[0]);
        assertEquals(32, P256.fromBase64Url(keys.privateKey()).length);
        assertArrayEquals(publicKey, P256.encode(P256.publicKey(publicKey)));
        // The RFC's own key pair: the encoded form of the decoded key is the original.
        assertEquals(SENDER_PRIVATE, P256.toBase64Url(P256.encode(P256.privateKey(P256.fromBase64Url(SENDER_PRIVATE)))));
    }

    @Test
    void vapidTokenIsSignedForThePushServiceOfTheSubscription() throws Exception {
        Vapid.Keys keys = Vapid.Keys.generate();
        Instant now = Instant.parse("2026-11-21T10:00:00Z");

        String header = Vapid.authorization(keys, "mailto:alerts@example.pl",
                URI.create("https://fcm.googleapis.com/fcm/send/abc123"), now);

        assertTrue(header.startsWith("vapid t="), header);
        assertTrue(header.endsWith(", k=" + keys.publicKey()), header);
        String token = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);

        JsonNode joseHeader = Json.tree(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
        assertEquals("ES256", joseHeader.get("alg").asText());
        JsonNode claims = Json.tree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        assertEquals("https://fcm.googleapis.com", claims.get("aud").asText());
        assertEquals("mailto:alerts@example.pl", claims.get("sub").asText());
        assertEquals(now.getEpochSecond() + 12 * 3600, claims.get("exp").asLong());

        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        assertEquals(64, signature.length);
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(P256.publicKey(P256.fromBase64Url(keys.publicKey())));
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(verifier.verify(signature));
    }
}
