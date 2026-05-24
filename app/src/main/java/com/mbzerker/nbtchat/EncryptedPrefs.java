package com.mbzerker.nbtchat;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class EncryptedPrefs {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "nbtchat_local_data_key";
    private static final String PREFIX = "enc:v1:";
    private static final int TAG_BITS = 128;

    private final SharedPreferences prefs;

    public EncryptedPrefs(Context context, String name) {
        prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE);
        migratePlainValues();
    }

    public boolean contains(String key) {
        return prefs.contains(key);
    }

    public String getString(String key, String fallback) {
        Object value = prefs.getAll().get(key);
        if (value == null) {
            return fallback;
        }
        String decoded = valueToString(key, value);
        return decoded == null ? fallback : decoded;
    }

    public int getInt(String key, int fallback) {
        String value = getString(key, null);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public long getLong(String key, long fallback) {
        String value = getString(key, null);
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public boolean getBoolean(String key, boolean fallback) {
        String value = getString(key, null);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    public Map<String, ?> getAll() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String value = valueToString(entry.getKey(), entry.getValue());
            if (value != null) {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    public Editor edit() {
        return new Editor(prefs.edit());
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    private void migratePlainValues() {
        SharedPreferences.Editor editor = null;
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String && ((String) value).startsWith(PREFIX)) {
                continue;
            }
            String plain = value == null ? "" : String.valueOf(value);
            String encrypted = encrypt(plain);
            if (encrypted == null) {
                continue;
            }
            if (editor == null) {
                editor = prefs.edit();
            }
            editor.putString(entry.getKey(), encrypted);
        }
        if (editor != null) {
            editor.apply();
        }
    }

    private String valueToString(String key, Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            String plain = String.valueOf(value);
            storeEncrypted(key, plain);
            return plain;
        }
        String raw = (String) value;
        if (!raw.startsWith(PREFIX)) {
            storeEncrypted(key, raw);
            return raw;
        }
        String decrypted = decrypt(raw.substring(PREFIX.length()));
        return decrypted == null ? "" : decrypted;
    }

    private void storeEncrypted(String key, String plain) {
        String encrypted = encrypt(plain == null ? "" : plain);
        if (encrypted != null) {
            prefs.edit().putString(key, encrypted).apply();
        }
    }

    private String encrypt(String plain) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey());
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal((plain == null ? "" : plain).getBytes(StandardCharsets.UTF_8));
            return PREFIX
                    + Base64.encodeToString(iv, Base64.NO_WRAP)
                    + ":"
                    + Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String decrypt(String raw) {
        try {
            int separator = raw.indexOf(':');
            if (separator <= 0) {
                return null;
            }
            byte[] iv = Base64.decode(raw.substring(0, separator), Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(raw.substring(separator + 1), Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    private SecretKey secretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    public final class Editor {
        private final SharedPreferences.Editor editor;

        private Editor(SharedPreferences.Editor editor) {
            this.editor = editor;
        }

        public Editor putString(String key, String value) {
            String encrypted = encrypt(value == null ? "" : value);
            if (encrypted != null) {
                editor.putString(key, encrypted);
            }
            return this;
        }

        public Editor putInt(String key, int value) {
            return putString(key, String.valueOf(value));
        }

        public Editor putLong(String key, long value) {
            return putString(key, String.valueOf(value));
        }

        public Editor putBoolean(String key, boolean value) {
            return putString(key, String.valueOf(value));
        }

        public Editor remove(String key) {
            editor.remove(key);
            return this;
        }

        public void apply() {
            editor.apply();
        }
    }
}
