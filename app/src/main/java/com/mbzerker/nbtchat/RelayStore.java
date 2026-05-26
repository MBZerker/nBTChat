package com.mbzerker.nbtchat;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RelayStore {
    private static final String RELAY_PREFS = "relay_queue";
    private static final String SEEN_PREFS = "relay_seen";
    private static final String DIAG_PREFS = "relay_diagnostics";
    private static final String KEY_EVENTS = "events";

    private final EncryptedPrefs relayPrefs;
    private final EncryptedPrefs seenPrefs;
    private final EncryptedPrefs diagPrefs;

    public RelayStore(Context context) {
        relayPrefs = new EncryptedPrefs(context, RELAY_PREFS);
        seenPrefs = new EncryptedPrefs(context, SEEN_PREFS);
        diagPrefs = new EncryptedPrefs(context, DIAG_PREFS);
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
            log("Pacote selado guardado para " + shortId(destination) + " ttl=" + envelope.optInt("ttl", 0));
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
            if (!result.isEmpty()) {
                log("Pacote selado entregue para " + shortId(destinationDeviceId) + " total=" + result.size());
            }
        } catch (JSONException ignored) {
        }
        return result;
    }

    public synchronized int queuedCount() {
        int count = 0;
        for (Map.Entry<String, ?> entry : relayPrefs.getAll().entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof String)) {
                continue;
            }
            try {
                count += new JSONArray((String) value).length();
            } catch (JSONException ignored) {
            }
        }
        return count;
    }

    public synchronized int destinationCount() {
        return relayPrefs.getAll().size();
    }

    public synchronized List<String> diagnostics() {
        List<String> events = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(diagPrefs.getString(KEY_EVENTS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                events.add(array.optString(i, ""));
            }
        } catch (JSONException ignored) {
        }
        return events;
    }

    public synchronized void log(String message) {
        try {
            JSONArray array = new JSONArray(diagPrefs.getString(KEY_EVENTS, "[]"));
            array.put(System.currentTimeMillis() + " - " + (message == null ? "" : message));
            while (array.length() > 80) {
                JSONArray trimmed = new JSONArray();
                for (int i = 1; i < array.length(); i++) {
                    trimmed.put(array.get(i));
                }
                array = trimmed;
            }
            diagPrefs.edit().putString(KEY_EVENTS, array.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    private JSONArray rawQueue(String destinationDeviceId) throws JSONException {
        String raw = relayPrefs.getString(destinationDeviceId, "[]");
        return new JSONArray(raw == null || raw.trim().isEmpty() ? "[]" : raw);
    }

    private String shortId(String value) {
        if (value == null || value.length() <= 8) {
            return value == null ? "" : value;
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }
}
