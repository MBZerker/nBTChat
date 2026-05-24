package com.mbzerker.nbtchat;

import android.content.Context;
import android.provider.Settings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public final class StoreDeviceId {
    private StoreDeviceId() {
    }

    public static String get(Context context) {
        String androidId = "";
        try {
            androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception ignored) {
        }
        if (androidId == null || androidId.trim().isEmpty()) {
            androidId = new IdentityStore(context).getDeviceId();
        }
        return "nbt-" + sha256(context.getPackageName() + ":" + androidId).substring(0, 32);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(String.format(Locale.ROOT, "%02x", b));
            }
            return builder.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value == null ? 0 : value.hashCode()) + "00000000000000000000000000000000";
        }
    }
}
