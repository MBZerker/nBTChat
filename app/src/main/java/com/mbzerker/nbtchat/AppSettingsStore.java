package com.mbzerker.nbtchat;

import android.content.Context;

public final class AppSettingsStore {
    private static final String PREFS = "app_settings";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    private static final String KEY_SCAN_FIRST_SEEN_AT = "scan_first_seen_at";
    private static final String KEY_SCAN_LAST_PROMPT_AT = "scan_last_prompt_at";
    private static final String KEY_SCAN_DISMISSED = "scan_dismissed";
    private static final String KEY_LAST_CRITICAL_UPDATE = "last_critical_update";
    private static final String KEY_NOTIFICATION_SOUND_URI = "notification_sound_uri";
    private static final String KEY_NOTIFICATION_SOUND_NAME = "notification_sound_name";
    private static final String KEY_VOICE_OUTPUT = "voice_output";
    private static final String KEY_USER_PRESENCE = "user_presence";
    private static final String KEY_CONTACT_SHARING_ENABLED = "contact_sharing_enabled";
    private static final String KEY_READ_RECEIPTS_ENABLED = "read_receipts_enabled";
    private static final String KEY_TERMS_VERSION = "terms_version";
    public static final String VOICE_OUTPUT_PHONE = "phone";
    public static final String VOICE_OUTPUT_BLUETOOTH = "bluetooth";
    public static final String PRESENCE_ONLINE = "online";
    public static final String PRESENCE_BUSY = "busy";
    public static final String PRESENCE_INVISIBLE = "invisible";
    private static final long SCAN_PROMPT_INTERVAL_MS = 12L * 60L * 60L * 1000L;
    private static final long SCAN_PROMPT_WINDOW_MS = 3L * 24L * 60L * 60L * 1000L;

    private final EncryptedPrefs prefs;

    public AppSettingsStore(Context context) {
        prefs = new EncryptedPrefs(context, PREFS);
    }

    public boolean notificationsEnabled() {
        return prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true);
    }

    public void setNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply();
    }

    public String notificationSoundUri() {
        return prefs.getString(KEY_NOTIFICATION_SOUND_URI, "");
    }

    public String notificationSoundName() {
        return prefs.getString(KEY_NOTIFICATION_SOUND_NAME, "");
    }

    public void setNotificationSound(String uri, String name) {
        prefs.edit()
                .putString(KEY_NOTIFICATION_SOUND_URI, uri == null ? "" : uri.trim())
                .putString(KEY_NOTIFICATION_SOUND_NAME, name == null ? "" : name.trim())
                .apply();
    }

    public void clearNotificationSound() {
        prefs.edit()
                .remove(KEY_NOTIFICATION_SOUND_URI)
                .remove(KEY_NOTIFICATION_SOUND_NAME)
                .apply();
    }

    public String voiceOutput() {
        return prefs.getString(KEY_VOICE_OUTPUT, VOICE_OUTPUT_PHONE);
    }

    public boolean playVoiceOnPhone() {
        return VOICE_OUTPUT_PHONE.equals(voiceOutput());
    }

    public void setVoiceOutput(String output) {
        String value = VOICE_OUTPUT_BLUETOOTH.equals(output) ? VOICE_OUTPUT_BLUETOOTH : VOICE_OUTPUT_PHONE;
        prefs.edit().putString(KEY_VOICE_OUTPUT, value).apply();
    }

    public String userPresence() {
        return prefs.getString(KEY_USER_PRESENCE, PRESENCE_ONLINE);
    }

    public void setUserPresence(String presence) {
        String value = PRESENCE_BUSY.equals(presence) || PRESENCE_INVISIBLE.equals(presence)
                ? presence
                : PRESENCE_ONLINE;
        prefs.edit().putString(KEY_USER_PRESENCE, value).apply();
    }

    public boolean contactSharingEnabled() {
        return prefs.getBoolean(KEY_CONTACT_SHARING_ENABLED, false);
    }

    public void setContactSharingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_CONTACT_SHARING_ENABLED, enabled).apply();
    }

    public boolean readReceiptsEnabled() {
        return prefs.getBoolean(KEY_READ_RECEIPTS_ENABLED, true);
    }

    public void setReadReceiptsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_READ_RECEIPTS_ENABLED, enabled).apply();
    }

    public int termsVersion() {
        return (int) prefs.getLong(KEY_TERMS_VERSION, 0L);
    }

    public boolean termsAccepted(int currentVersion) {
        return termsVersion() >= currentVersion;
    }

    public void setTermsAcceptedVersion(int version) {
        prefs.edit().putLong(KEY_TERMS_VERSION, Math.max(0, version)).apply();
    }

    public boolean shouldPromptNearbyScan(int contactCount) {
        if (contactCount >= 10 || prefs.getBoolean(KEY_SCAN_DISMISSED, false)) {
            return false;
        }
        long now = System.currentTimeMillis();
        long firstSeenAt = prefs.getLong(KEY_SCAN_FIRST_SEEN_AT, 0L);
        if (firstSeenAt <= 0L) {
            firstSeenAt = now;
            prefs.edit().putLong(KEY_SCAN_FIRST_SEEN_AT, firstSeenAt).apply();
        }
        if (now - firstSeenAt > SCAN_PROMPT_WINDOW_MS) {
            prefs.edit().putBoolean(KEY_SCAN_DISMISSED, true).apply();
            return false;
        }
        long lastPromptAt = prefs.getLong(KEY_SCAN_LAST_PROMPT_AT, 0L);
        if (now - lastPromptAt < SCAN_PROMPT_INTERVAL_MS) {
            return false;
        }
        prefs.edit().putLong(KEY_SCAN_LAST_PROMPT_AT, now).apply();
        return true;
    }

    public boolean shouldNotifyCriticalUpdate(String versionName) {
        if (versionName == null || versionName.trim().isEmpty()) {
            return false;
        }
        String last = prefs.getString(KEY_LAST_CRITICAL_UPDATE, "");
        if (versionName.equals(last)) {
            return false;
        }
        prefs.edit().putString(KEY_LAST_CRITICAL_UPDATE, versionName).apply();
        return true;
    }
}
