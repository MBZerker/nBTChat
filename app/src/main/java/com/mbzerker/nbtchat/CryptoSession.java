package com.mbzerker.nbtchat;

import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class CryptoSession {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey aesKey;
    private final String fingerprint;
    private final SecureRandom random = new SecureRandom();

    private CryptoSession(SecretKey aesKey, String fingerprint) {
        this.aesKey = aesKey;
        this.fingerprint = fingerprint;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public JSONObject encrypt(JSONObject plainJson) throws Exception {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
        byte[] encrypted = cipher.doFinal(plainJson.toString().getBytes(StandardCharsets.UTF_8));

        JSONObject frame = new JSONObject();
        frame.put("type", "encrypted");
        frame.put("iv", encode(iv));
        frame.put("payload", encode(encrypted));
        return frame;
    }

    public JSONObject decrypt(JSONObject encryptedFrame) throws Exception {
        byte[] iv = decode(encryptedFrame.getString("iv"));
        byte[] payload = decode(encryptedFrame.getString("payload"));

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
        byte[] plain = cipher.doFinal(payload);
        return new JSONObject(new String(plain, StandardCharsets.UTF_8));
    }

    public static Handshake createHandshake() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();
        return new Handshake(keyPair, encode(keyPair.getPublic().getEncoded()));
    }

    public static CryptoSession derive(Handshake local, String remotePublicKeyBase64) throws Exception {
        byte[] remoteBytes = decode(remotePublicKeyBase64);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        PublicKey remotePublicKey = keyFactory.generatePublic(new X509EncodedKeySpec(remoteBytes));

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(local.keyPair.getPrivate());
        agreement.doPhase(remotePublicKey, true);
        byte[] sharedSecret = agreement.generateSecret();

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(sharedSecret);
        byte[] keyBytes = digest.digest();
        SecretKey key = new SecretKeySpec(Arrays.copyOf(keyBytes, 32), "AES");
        String fingerprint = shortFingerprint(local.publicKeyBase64, remotePublicKeyBase64);
        return new CryptoSession(key, fingerprint);
    }

    public static JSONObject sealFor(String recipientPublicKeyBase64, JSONObject plainJson) throws Exception {
        Handshake ephemeral = createHandshake();
        PublicKey recipientPublicKey = decodePublicKey(recipientPublicKeyBase64);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(ephemeral.keyPair.getPrivate());
        agreement.doPhase(recipientPublicKey, true);
        byte[] sharedSecret = agreement.generateSecret();

        byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(sharedSecret);
        SecretKey key = new SecretKeySpec(Arrays.copyOf(keyBytes, 32), "AES");
        byte[] iv = new byte[IV_BYTES];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] encrypted = cipher.doFinal(plainJson.toString().getBytes(StandardCharsets.UTF_8));

        JSONObject sealed = new JSONObject();
        sealed.put("ephemeralPublicKey", ephemeral.getPublicKeyBase64());
        sealed.put("iv", encode(iv));
        sealed.put("payload", encode(encrypted));
        return sealed;
    }

    public static JSONObject openSealed(java.security.PrivateKey recipientPrivateKey, JSONObject sealed) throws Exception {
        PublicKey ephemeralPublicKey = decodePublicKey(sealed.getString("ephemeralPublicKey"));

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(recipientPrivateKey);
        agreement.doPhase(ephemeralPublicKey, true);
        byte[] sharedSecret = agreement.generateSecret();

        byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(sharedSecret);
        SecretKey key = new SecretKeySpec(Arrays.copyOf(keyBytes, 32), "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, decode(sealed.getString("iv"))));
        byte[] plain = cipher.doFinal(decode(sealed.getString("payload")));
        return new JSONObject(new String(plain, StandardCharsets.UTF_8));
    }

    private static PublicKey decodePublicKey(String publicKeyBase64) throws Exception {
        byte[] remoteBytes = decode(publicKeyBase64);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        return keyFactory.generatePublic(new X509EncodedKeySpec(remoteBytes));
    }

    private static String shortFingerprint(String localPublic, String remotePublic) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String first = localPublic.compareTo(remotePublic) <= 0 ? localPublic : remotePublic;
        String second = localPublic.compareTo(remotePublic) <= 0 ? remotePublic : localPublic;
        byte[] hash = digest.digest((first + ":" + second).getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i > 0 && i % 2 == 0) {
                builder.append(' ');
            }
            builder.append(String.format("%02X", hash[i]));
        }
        return builder.toString();
    }

    private static String encode(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private static byte[] decode(String value) {
        return Base64.decode(value, Base64.NO_WRAP);
    }

    public static final class Handshake {
        private final KeyPair keyPair;
        private final String publicKeyBase64;

        private Handshake(KeyPair keyPair, String publicKeyBase64) {
            this.keyPair = keyPair;
            this.publicKeyBase64 = publicKeyBase64;
        }

        public String getPublicKeyBase64() {
            return publicKeyBase64;
        }
    }
}
