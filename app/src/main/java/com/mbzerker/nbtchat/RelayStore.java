package com.mbzerker.nbtchat;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RelayStore {
    private static final String RELAY_PREFS = "relay_queue";
    private static final String SEEN_PREFS = "relay_seen";

    private final EncryptedPrefs relayPrefs;
    private final EncryptedPrefs seenPrefs;

    public RelayStore(Context context) {
        relayPrefs = new EncryptedPrefs(context, RELAY_PREFS);
        seenPrefs = new EncryptedPrefs(context, SEEN_PREFS);
    }

    public synchronized boolean rememberSeen(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        if (seenPrefs.contains(id)) {
            return false;
        }
        seenPrefs.edit().putLong(id, System.currentTimeMillis()).apply();
        return true;
    }

    public synchronized void store(JSONObject envelope) {
        String destination = envelope.optString("destinationDeviceId", "");
        String id = envelope.optString("id", "");
        if (destination.isEmpty() || id.isEmpty()) {
            return;
        }
        try {
            JSONArray queue = rawQueue(destination);
            for (int i = 0; i < queue.length(); i++) {
                if (id.equals(queue.getJSONObject(i).optString("id", ""))) {
                    return;
                }
            }
            queue.put(envelope);
            relayPrefs.edit().putString(destination, queue.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    public synchronized List<JSONObject> takeFor(String destinationDeviceId) {
        List<JSONObject> result = new ArrayList<>();
        if (destinationDeviceId == null || destinationDeviceId.trim().isEmpty()) {
            return result;
        }
        try {
            JSONArray queue = rawQueue(destinationDeviceId);
            for (int i = 0; i < queue.length(); i++) {
                result.add(queue.getJSONObject(i));
            }
            relayPrefs.edit().remove(destinationDeviceId).apply();
        } catch (JSONException ignored) {
        }
        return result;
    }

    private JSONArray rawQueue(String destinationDeviceId) throws JSONException {
        String raw = relayPrefs.getString(destinationDeviceId, "[]");
        return new JSONArray(raw == null || raw.trim().isEmpty() ? "[]" : raw);
    }
}
