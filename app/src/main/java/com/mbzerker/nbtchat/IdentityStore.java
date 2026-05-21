package com.mbzerker.nbtchat;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public final class IdentityStore {
    private static final String PREFS = "local_identity";
    private static final String KEY_DEVICE_ID = "deviceId";
    private static final String KEY_PUBLIC = "public";
    private static final String KEY_PRIVATE = "private";

    private final SharedPreferences prefs;

    public IdentityStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureIdentity();
    }

    public String getDeviceId() {
        return prefs.getString(KEY_DEVICE_ID, "");
    }

    public String getPublicKeyBase64() {
        return prefs.getString(KEY_PUBLIC, "");
    }

    public PrivateKey getPrivateKey() throws Exception {
        byte[] raw = Base64.decode(prefs.getString(KEY_PRIVATE, ""), Base64.NO_WRAP);
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(raw));
    }

    private void ensureIdentity() {
        if (!prefs.getString(KEY_DEVICE_ID, "").isEmpty()
                && !prefs.getString(KEY_PUBLIC, "").isEmpty()
                && !prefs.getString(KEY_PRIVATE, "").isEmpty()) {
            return;
        }
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = generator.generateKeyPair();
            prefs.edit()
                    .putString(KEY_DEVICE_ID, randomDeviceId())
                    .putString(KEY_PUBLIC, Base64.encodeToString(pair.getPublic().getEncoded(), Base64.NO_WRAP))
                    .putString(KEY_PRIVATE, Base64.encodeToString(pair.getPrivate().getEncoded(), Base64.NO_WRAP))
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private String randomDeviceId() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
                .replace("=", "")
                .replace("+", "")
                .replace("/", "");
    }
}
