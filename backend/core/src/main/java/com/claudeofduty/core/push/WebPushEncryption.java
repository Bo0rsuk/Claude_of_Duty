package com.claudeofduty.core.push;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts a push message for one browser subscription, as RFC 8291
 * ("Message Encryption for Web Push") defines. Only that browser can read the
 * result; the push service that relays it cannot.
 */
public final class WebPushEncryption {

    private static final int SALT_LENGTH = 16;
    private static final int RECORD_SIZE = 4096;
    private static final int KEY_LENGTH = 16;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;
    /** Marks the end of the last (here: only) record. */
    private static final byte LAST_RECORD_DELIMITER = 0x02;
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushEncryption() {
    }

    /**
     * @param browserPublicKey the subscription's "p256dh" key, decoded
     * @param authSecret       the subscription's "auth" secret, decoded
     * @return the request body to send with Content-Encoding: aes128gcm
     */
    public static byte[] encrypt(byte[] plaintext, byte[] browserPublicKey, byte[] authSecret) {
        // A fresh key pair and salt per message, as the RFC requires.
        KeyPair sender = P256.generate();
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, browserPublicKey, authSecret, (ECPrivateKey) sender.getPrivate(),
                P256.encode((ECPublicKey) sender.getPublic()), salt);
    }

    static byte[] encrypt(byte[] plaintext, byte[] browserPublicKey, byte[] authSecret,
            ECPrivateKey senderPrivateKey, byte[] senderPublicKey, byte[] salt) {
        try {
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(senderPrivateKey);
            agreement.doPhase(P256.publicKey(browserPublicKey), true);
            byte[] sharedSecret = agreement.generateSecret();

            // Section 3.4: combine the shared secret with the subscription's authentication secret.
            byte[] keyInfo = concat(ascii("WebPush: info"), new byte[] {0}, browserPublicKey, senderPublicKey);
            byte[] inputKey = hkdf(authSecret, sharedSecret, keyInfo, 32);

            // Section 3.4 and RFC 8188: derive the content encryption key and the nonce from the salt.
            byte[] contentKey = hkdf(salt, inputKey, concat(ascii("Content-Encoding: aes128gcm"), new byte[] {0}), KEY_LENGTH);
            byte[] nonce = hkdf(salt, inputKey, concat(ascii("Content-Encoding: nonce"), new byte[] {0}), NONCE_LENGTH);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(contentKey, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[] {LAST_RECORD_DELIMITER}));

            // Header: salt, record size, length of the key id, key id (the sender's public key).
            return ByteBuffer.allocate(SALT_LENGTH + 4 + 1 + senderPublicKey.length + ciphertext.length)
                    .put(salt)
                    .putInt(RECORD_SIZE)
                    .put((byte) senderPublicKey.length)
                    .put(senderPublicKey)
                    .put(ciphertext)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt the push message", e);
        }
    }

    /** HKDF with SHA-256 for outputs of up to 32 bytes, which is a single HMAC round. */
    private static byte[] hkdf(byte[] salt, byte[] inputKey, byte[] info, int length) throws GeneralSecurityException {
        byte[] pseudoRandomKey = hmac(salt, inputKey);
        return Arrays.copyOf(hmac(pseudoRandomKey, concat(info, new byte[] {1})), length);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(length);
        for (byte[] part : parts) {
            buffer.put(part);
        }
        return buffer.array();
    }
}
