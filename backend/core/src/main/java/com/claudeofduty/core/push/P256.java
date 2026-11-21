package com.claudeofduty.core.push;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Keys on the P-256 curve in the raw encodings Web Push uses: a public key is
 * the 65-byte uncompressed point (0x04, X, Y), a private key is the 32-byte scalar.
 */
public final class P256 {

    public static final int PUBLIC_KEY_LENGTH = 65;
    private static final int COORDINATE_LENGTH = 32;
    private static final ECParameterSpec CURVE = curve();

    private P256() {
    }

    public static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @throws IllegalArgumentException when the bytes are not an uncompressed P-256 point */
    public static ECPublicKey publicKey(byte[] uncompressed) {
        if (uncompressed.length != PUBLIC_KEY_LENGTH || uncompressed[0] != 0x04) {
            throw new IllegalArgumentException("Not an uncompressed P-256 public key");
        }
        ECPoint point = new ECPoint(
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 1 + COORDINATE_LENGTH)),
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 1 + COORDINATE_LENGTH, PUBLIC_KEY_LENGTH)));
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, CURVE));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Not a valid P-256 public key", e);
        }
    }

    public static ECPrivateKey privateKey(byte[] scalar) {
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), CURVE));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Not a valid P-256 private key", e);
        }
    }

    public static byte[] encode(ECPublicKey key) {
        byte[] encoded = new byte[PUBLIC_KEY_LENGTH];
        encoded[0] = 0x04;
        copyRightAligned(key.getW().getAffineX(), encoded, 1);
        copyRightAligned(key.getW().getAffineY(), encoded, 1 + COORDINATE_LENGTH);
        return encoded;
    }

    public static byte[] encode(ECPrivateKey key) {
        byte[] encoded = new byte[COORDINATE_LENGTH];
        copyRightAligned(key.getS(), encoded, 0);
        return encoded;
    }

    public static byte[] fromBase64Url(String text) {
        return Base64.getUrlDecoder().decode(text);
    }

    public static String toBase64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** BigInteger drops leading zeros and may add a sign byte; coordinates are always exactly 32 bytes. */
    private static void copyRightAligned(BigInteger value, byte[] target, int offset) {
        byte[] bytes = value.toByteArray();
        int length = Math.min(bytes.length, COORDINATE_LENGTH);
        System.arraycopy(bytes, bytes.length - length, target, offset + COORDINATE_LENGTH - length, length);
    }

    private static ECParameterSpec curve() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
