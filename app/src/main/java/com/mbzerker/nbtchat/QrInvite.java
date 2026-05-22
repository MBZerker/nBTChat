package com.mbzerker.nbtchat;

import android.util.Base64;

import org.json.JSONObject;

public final class QrInvite {
    public static final String PREFIX = "NBTCHAT1:";

    private QrInvite() {
    }

    public static String create(String address, String bluetoothName, String deviceId, String publicKey, UserProfile profile) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "nbtchat-invite");
            json.put("v", 1);
            json.put("address", clean(address));
            json.put("bluetoothName", clean(bluetoothName));
            json.put("deviceId", clean(deviceId));
            json.put("publicKey", clean(publicKey));
            json.put("profile", profile == null ? UserProfile.empty().toJson() : profile.toJson());
            return PREFIX + Base64.encodeToString(json.toString().getBytes("UTF-8"), Base64.URL_SAFE | Base64.NO_WRAP);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static Invite parse(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            JSONObject json;
            if (value.startsWith(PREFIX)) {
                byte[] bytes = Base64.decode(value.substring(PREFIX.length()), Base64.URL_SAFE | Base64.NO_WRAP);
                json = new JSONObject(new String(bytes, "UTF-8"));
            } else if (value.startsWith("{")) {
                json = new JSONObject(value);
            } else {
                return null;
            }
            if (!"nbtchat-invite".equals(json.optString("type", ""))) {
                return null;
            }
            return new Invite(
                    clean(json.optString("address", "")),
                    clean(json.optString("bluetoothName", "")),
                    clean(json.optString("deviceId", "")),
                    clean(json.optString("publicKey", "")),
                    UserProfile.fromJson(json.optJSONObject("profile"))
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean validBluetoothAddress(String address) {
        if (address == null) {
            return false;
        }
        String value = address.trim();
        return value.matches("(?i)([0-9A-F]{2}:){5}[0-9A-F]{2}") && !"02:00:00:00:00:00".equals(value);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Invite {
        public final String address;
        public final String bluetoothName;
        public final String deviceId;
        public final String publicKey;
        public final UserProfile profile;

        Invite(String address, String bluetoothName, String deviceId, String publicKey, UserProfile profile) {
            this.address = address;
            this.bluetoothName = bluetoothName;
            this.deviceId = deviceId;
            this.publicKey = publicKey;
            this.profile = profile == null ? UserProfile.empty() : profile;
        }
    }
}
