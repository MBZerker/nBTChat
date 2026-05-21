package com.mbzerker.nbtchat;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ProfileStore {
    private static final String PROFILE_PREFS = "local_profile";
    private static final String CONTACT_PREFS = "known_contacts";
    private static final String FINGERPRINT_PREFS = "contact_fingerprints";
    private static final String IDENTITY_PREFS = "contact_identities";
    private static final String KEY_NAME = "name";
    private static final String KEY_STATUS = "status";
    private static final String KEY_GENDER = "gender";
    private static final String KEY_PHOTO = "photo";

    private final SharedPreferences profilePrefs;
    private final SharedPreferences contactPrefs;
    private final SharedPreferences fingerprintPrefs;
    private final SharedPreferences identityPrefs;

    public ProfileStore(Context context) {
        profilePrefs = context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE);
        contactPrefs = context.getSharedPreferences(CONTACT_PREFS, Context.MODE_PRIVATE);
        fingerprintPrefs = context.getSharedPreferences(FINGERPRINT_PREFS, Context.MODE_PRIVATE);
        identityPrefs = context.getSharedPreferences(IDENTITY_PREFS, Context.MODE_PRIVATE);
    }

    public boolean hasLocalProfile() {
        return loadLocalProfile().isComplete();
    }

    public UserProfile loadLocalProfile() {
        return new UserProfile(
                profilePrefs.getString(KEY_NAME, ""),
                profilePrefs.getString(KEY_STATUS, ""),
                profilePrefs.getString(KEY_GENDER, UserProfile.GENDER_OTHER),
                profilePrefs.getString(KEY_PHOTO, "")
        );
    }

    public void saveLocalProfile(UserProfile profile) {
        profilePrefs.edit()
                .putString(KEY_NAME, profile.getDisplayName())
                .putString(KEY_STATUS, profile.getStatus())
                .putString(KEY_GENDER, profile.getGender())
                .putString(KEY_PHOTO, profile.getPhotoBase64())
                .apply();
    }

    public void saveContact(String address, UserProfile profile) {
        if (address == null || address.trim().isEmpty() || profile == null || !profile.isComplete()) {
            return;
        }
        try {
            contactPrefs.edit().putString(address, profile.toJson().toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    public UserProfile loadContact(String address) {
        if (address == null) {
            return UserProfile.empty();
        }
        String raw = contactPrefs.getString(address, "");
        if (raw == null || raw.trim().isEmpty()) {
            return UserProfile.empty();
        }
        try {
            return UserProfile.fromJson(new JSONObject(raw));
        } catch (JSONException ignored) {
            return UserProfile.empty();
        }
    }

    public Map<String, UserProfile> loadContacts() {
        Map<String, UserProfile> contacts = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : contactPrefs.getAll().entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof String)) {
                continue;
            }
            try {
                UserProfile profile = UserProfile.fromJson(new JSONObject((String) value));
                if (profile.isComplete()) {
                    contacts.put(entry.getKey(), profile);
                }
            } catch (JSONException ignored) {
            }
        }
        return contacts;
    }

    public void saveFingerprint(String address, String fingerprint) {
        if (address == null || address.trim().isEmpty() || fingerprint == null || fingerprint.trim().isEmpty()) {
            return;
        }
        fingerprintPrefs.edit().putString(address, fingerprint.trim()).apply();
    }

    public String loadFingerprint(String address) {
        if (address == null || address.trim().isEmpty()) {
            return "";
        }
        return fingerprintPrefs.getString(address, "");
    }

    public void saveIdentity(String address, String deviceId, String identityPublicKey) {
        if (address == null || address.trim().isEmpty() || deviceId == null || deviceId.trim().isEmpty()) {
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("deviceId", deviceId);
            json.put("identityPublicKey", identityPublicKey == null ? "" : identityPublicKey);
            identityPrefs.edit().putString(address, json.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    public ContactIdentity loadIdentity(String address) {
        if (address == null || address.trim().isEmpty()) {
            return new ContactIdentity("", "");
        }
        String raw = identityPrefs.getString(address, "");
        if (raw == null || raw.trim().isEmpty()) {
            return new ContactIdentity("", "");
        }
        try {
            JSONObject json = new JSONObject(raw);
            return new ContactIdentity(json.optString("deviceId", ""), json.optString("identityPublicKey", ""));
        } catch (JSONException ignored) {
            return new ContactIdentity("", "");
        }
    }

    public String addressForDeviceId(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return "";
        }
        for (Map.Entry<String, ?> entry : identityPrefs.getAll().entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof String)) {
                continue;
            }
            try {
                JSONObject json = new JSONObject((String) value);
                if (deviceId.equals(json.optString("deviceId", ""))) {
                    return entry.getKey();
                }
            } catch (JSONException ignored) {
            }
        }
        return "";
    }

    public static final class ContactIdentity {
        public final String deviceId;
        public final String identityPublicKey;

        ContactIdentity(String deviceId, String identityPublicKey) {
            this.deviceId = deviceId;
            this.identityPublicKey = identityPublicKey;
        }
    }
}
