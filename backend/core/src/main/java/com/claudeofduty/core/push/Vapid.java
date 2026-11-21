package com.claudeofduty.core.push;

import com.claudeofduty.core.json.Json;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * VAPID (RFC 8292): proves to a push service that a message comes from the
 * server whose public key the browser subscribed with.
 */
public final class Vapid {

    private static final Duration TOKEN_LIFETIME = Duration.ofHours(12);

    /** Both keys in base64url: the form stored in configuration and handed to browsers. */
    public record Keys(String publicKey, String privateKey) {

        public static Keys generate() {
            KeyPair pair = P256.generate();
            return new Keys(
                    P256.toBase64Url(P256.encode((ECPublicKey) pair.getPublic())),
                    P256.toBase64Url(P256.encode((ECPrivateKey) pair.getPrivate())));
        }
    }

    private Vapid() {
    }

    /**
     * @param subject a "mailto:" or "https:" contact for the push service's operator
     * @return the value of the Authorization header for a request to {@code endpoint}
     */
    public static String authorization(Keys keys, String subject, URI endpoint, Instant now) {
        Map<String, Object> claims = new LinkedHashMap<>();
        // The token is valid for one push service only: scheme and host of the subscription endpoint.
        claims.put("aud", endpoint.getScheme() + "://" + endpoint.getAuthority());
        claims.put("exp", now.plus(TOKEN_LIFETIME).getEpochSecond());
        claims.put("sub", subject);

        String unsigned = encode(Json.write(Map.of("typ", "JWT", "alg", "ES256"))) + "." + encode(Json.write(claims));
        try {
            // P1363 format is the raw R || S that JWTs carry, not the DER structure Java produces by default.
            Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
            signature.initSign(P256.privateKey(P256.fromBase64Url(keys.privateKey())));
            signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));
            return "vapid t=" + unsigned + "." + P256.toBase64Url(signature.sign()) + ", k=" + keys.publicKey();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not sign the VAPID token", e);
        }
    }

    private static String encode(String json) {
        return P256.toBase64Url(json.getBytes(StandardCharsets.UTF_8));
    }
}
