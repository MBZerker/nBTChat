package com.mbzerker.nbtchat;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ProfileStore {
    private static final String PROFILE_PREFS = "local_profile";
    private static final String CONTACT_PREFS = "known_contacts";
    private static final String FINGERPRINT_PREFS = "contact_fingerprints";
    private static final String IDENTITY_PREFS = "contact_identities";
    private static final String CONTACT_FLAGS_PREFS = "contact_flags";
    private static final String KEY_MUTED_PREFIX = "muted:";
    private static final String KEY_BLOCKED_PREFIX = "blocked:";
    private static final String KEY_SHARE_ALLOWED_PREFIX = "share_allowed:";
    private static final String KEY_NAME = "name";
    private static final String KEY_STATUS = "status";
    private static final String KEY_GENDER = "gender";
    private static final String KEY_PHOTO = "photo";

    private final EncryptedPrefs profilePrefs;
    private final EncryptedPrefs contactPrefs;
    private final EncryptedPrefs fingerprintPrefs;
    private final EncryptedPrefs identityPrefs;
    private final EncryptedPrefs contactFlagsPrefs;

    public ProfileStore(Context context) {
        profilePrefs = new EncryptedPrefs(context, PROFILE_PREFS);
        contactPrefs = new EncryptedPrefs(context, CONTACT_PREFS);
        fingerprintPrefs = new EncryptedPrefs(context, FINGERPRINT_PREFS);
        identityPrefs = new EncryptedPrefs(context, IDENTITY_PREFS);
        contactFlagsPrefs = new EncryptedPrefs(context, CONTACT_FLAGS_PREFS);
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
        ContactIdentity existing = loadIdentity(address);
        saveIdentity(address, deviceId, identityPublicKey, existing.bluetoothName);
    }

    public void saveIdentity(String address, String deviceId, String identityPublicKey, String bluetoothName) {
        if (address == null || address.trim().isEmpty() || deviceId == null || deviceId.trim().isEmpty()) {
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("deviceId", deviceId);
            json.put("identityPublicKey", identityPublicKey == null ? "" : identityPublicKey);
            json.put("bluetoothName", bluetoothName == null ? "" : bluetoothName.trim());
            identityPrefs.edit().putString(address, json.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    public IdentityStatus verifyOrStoreIdentity(String address, String deviceId, String identityPublicKey, String bluetoothName) {
        if (address == null || address.trim().isEmpty()
                || deviceId == null || deviceId.trim().isEmpty()
                || identityPublicKey == null || identityPublicKey.trim().isEmpty()) {
            return IdentityStatus.INVALID;
        }
        ContactIdentity existing = loadIdentity(address);
        if (existing.deviceId.isEmpty() && existing.identityPublicKey.isEmpty()) {
            saveIdentity(address, deviceId, identityPublicKey, bluetoothName);
            return IdentityStatus.NEW;
        }
        boolean sameDevice = existing.deviceId.equals(deviceId);
        boolean sameKey = existing.identityPublicKey.equals(identityPublicKey);
        if (sameDevice && sameKey) {
            if (bluetoothName != null && !bluetoothName.trim().isEmpty() && !bluetoothName.trim().equals(existing.bluetoothName)) {
                saveIdentity(address, deviceId, identityPublicKey, bluetoothName);
            }
            return IdentityStatus.MATCH;
        }
        if (!sameKey) {
            return IdentityStatus.CHANGED_KEY;
        }
        return IdentityStatus.CHANGED_DEVICE;
    }

    public void removeContact(String address) {
        if (address == null || address.trim().isEmpty()) {
            return;
        }
        contactPrefs.edit().remove(address).apply();
        fingerprintPrefs.edit().remove(address).apply();
        identityPrefs.edit().remove(address).apply();
        contactFlagsPrefs.edit()
                .remove(KEY_MUTED_PREFIX + address)
                .remove(KEY_BLOCKED_PREFIX + address)
                .remove(KEY_SHARE_ALLOWED_PREFIX + address)
                .apply();
    }

    public boolean isMuted(String address) {
        return address != null && contactFlagsPrefs.getBoolean(KEY_MUTED_PREFIX + address, false);
    }

    public void setMuted(String address, boolean muted) {
        if (address == null || address.trim().isEmpty()) {
            return;
        }
        contactFlagsPrefs.edit().putBoolean(KEY_MUTED_PREFIX + address, muted).apply();
    }

    public boolean isBlocked(String address) {
        return address != null && contactFlagsPrefs.getBoolean(KEY_BLOCKED_PREFIX + address, false);
    }

    public void setBlocked(String address, boolean blocked) {
        if (address == null || address.trim().isEmpty()) {
            return;
        }
        contactFlagsPrefs.edit().putBoolean(KEY_BLOCKED_PREFIX + address, blocked).apply();
    }

    public boolean isContactShareAllowed(String address) {
        return address != null && contactFlagsPrefs.getBoolean(KEY_SHARE_ALLOWED_PREFIX + address, false);
    }

    public void setContactShareAllowed(String address, boolean allowed) {
        if (address == null || address.trim().isEmpty()) {
            return;
        }
        contactFlagsPrefs.edit().putBoolean(KEY_SHARE_ALLOWED_PREFIX + address, allowed).apply();
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
            return new ContactIdentity(
                    json.optString("deviceId", ""),
                    json.optString("identityPublicKey", ""),
                    json.optString("bluetoothName", "")
            );
        } catch (JSONException ignored) {
            return new ContactIdentity("", "");
        }
    }

    public Map<String, ContactIdentity> loadIdentities() {
        Map<String, ContactIdentity> identities = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : identityPrefs.getAll().entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof String)) {
                continue;
            }
            try {
                JSONObject json = new JSONObject((String) value);
                ContactIdentity identity = new ContactIdentity(
                        json.optString("deviceId", ""),
                        json.optString("identityPublicKey", ""),
                        json.optString("bluetoothName", "")
                );
                if (!identity.deviceId.isEmpty()) {
                    identities.put(entry.getKey(), identity);
                }
            } catch (JSONException ignored) {
            }
        }
        return identities;
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
        public final String bluetoothName;

        ContactIdentity(String deviceId, String identityPublicKey) {
            this(deviceId, identityPublicKey, "");
        }

        ContactIdentity(String deviceId, String identityPublicKey, String bluetoothName) {
            this.deviceId = deviceId;
            this.identityPublicKey = identityPublicKey;
            this.bluetoothName = bluetoothName == null ? "" : bluetoothName;
        }
    }

    public enum IdentityStatus {
        NEW,
        MATCH,
        CHANGED_KEY,
        CHANGED_DEVICE,
        INVALID
    }
}
