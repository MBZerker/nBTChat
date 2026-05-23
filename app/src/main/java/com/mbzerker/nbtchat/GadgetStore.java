package com.mbzerker.nbtchat;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class GadgetStore {
    public static final String TABLE_100_ID = "table100";
    public static final String ACTION_GADGETS_CHANGED = "com.mbzerker.nbtchat.GADGETS_CHANGED";
    public static final String EXTRA_TABLE_ID = "tableId";
    private static final String PREFS = "official_gadgets";
    private static final String KEY_TABLE_100_UNTIL = "table100_until";
    private static final String KEY_TABLE_100_TEXT = "table100_text";
    private static final String KEY_TABLE_100_MESSAGE = "table100_message";
    private static final String KEY_TABLE_100_COPY = "table100_copy";
    private static final String KEY_TABLE_100_OWNER_CONTACT = "table100_owner_contact";
    private static final String KEY_TABLE_100_INSTANCE = "table100_instance";
    private static final String KEY_TABLE_100_CHOICES = "table100_choices";
    private static final long WEEK_MS = 7L * 24L * 60L * 60L * 1000L;

    private final SharedPreferences prefs;

    public GadgetStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean hasTable100() {
        return table100Until() > System.currentTimeMillis();
    }

    public long table100Until() {
        return prefs.getLong(KEY_TABLE_100_UNTIL, 0L);
    }

    public void buyTable100() {
        prefs.edit()
                .putLong(KEY_TABLE_100_UNTIL, System.currentTimeMillis() + WEEK_MS)
                .putString(KEY_TABLE_100_INSTANCE, "table100-" + Long.toHexString(System.currentTimeMillis()))
                .apply();
    }

    public String table100Text() {
        return table100CopyText();
    }

    public String table100OwnerMessage() {
        return prefs.getString(KEY_TABLE_100_MESSAGE, "");
    }

    public String table100CopyText() {
        String copy = prefs.getString(KEY_TABLE_100_COPY, "");
        if (copy == null || copy.trim().isEmpty()) {
            copy = prefs.getString(KEY_TABLE_100_TEXT, "");
        }
        return copy == null ? "" : copy;
    }

    public String table100OwnerContact() {
        return prefs.getString(KEY_TABLE_100_OWNER_CONTACT, "");
    }

    public String table100InstanceId() {
        String id = prefs.getString(KEY_TABLE_100_INSTANCE, "");
        if (id == null || id.trim().isEmpty()) {
            id = "table100-" + Long.toHexString(System.currentTimeMillis());
            prefs.edit().putString(KEY_TABLE_100_INSTANCE, id).apply();
        }
        return id;
    }

    public void saveTable100Text(String text) {
        saveTable100Texts(table100OwnerMessage(), text, table100OwnerContact());
    }

    public void saveTable100Texts(String ownerMessage, String copyText) {
        saveTable100Texts(ownerMessage, copyText, table100OwnerContact());
    }

    public void saveTable100Texts(String ownerMessage, String copyText, String ownerContact) {
        prefs.edit()
                .putString(KEY_TABLE_100_MESSAGE, ownerMessage == null ? "" : ownerMessage.trim())
                .putString(KEY_TABLE_100_COPY, copyText == null ? "" : copyText.trim())
                .putString(KEY_TABLE_100_OWNER_CONTACT, ownerContact == null ? "" : ownerContact.trim())
                .putString(KEY_TABLE_100_TEXT, copyText == null ? "" : copyText.trim())
                .apply();
    }

    public Table100Payload table100Payload() {
        return new Table100Payload(table100InstanceId(), table100OwnerMessage(), table100CopyText(), table100OwnerContact());
    }

    public void saveChoice(String tableId, String address, int number, String name, boolean confirmed) {
        if (tableId == null || tableId.trim().isEmpty() || address == null || address.trim().isEmpty() || number < 1 || number > 100) {
            return;
        }
        try {
            JSONArray items = rawChoices();
            boolean changed = false;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                if (tableId.equals(item.optString("tableId", ""))
                        && address.equals(item.optString("address", ""))
                        && number == item.optInt("number", -1)) {
                    item.put("name", clean(name));
                    item.put("confirmed", confirmed);
                    changed = true;
                    break;
                }
            }
            if (!changed) {
                JSONObject item = new JSONObject();
                item.put("tableId", tableId);
                item.put("address", address);
                item.put("number", number);
                item.put("name", clean(name));
                item.put("confirmed", confirmed);
                items.put(item);
            }
            prefs.edit().putString(KEY_TABLE_100_CHOICES, items.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    public void setChoiceConfirmed(String tableId, String address, int number, boolean confirmed) {
        if (tableId == null || address == null) {
            return;
        }
        try {
            JSONArray items = rawChoices();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                if (tableId.equals(item.optString("tableId", ""))
                        && address.equals(item.optString("address", ""))
                        && number == item.optInt("number", -1)) {
                    item.put("confirmed", confirmed);
                    prefs.edit().putString(KEY_TABLE_100_CHOICES, items.toString()).apply();
                    return;
                }
            }
        } catch (JSONException ignored) {
        }
    }

    public int choiceStatus(String tableId, String address, int number) {
        if (tableId == null || address == null) {
            return 0;
        }
        for (Table100Choice choice : loadChoices(tableId)) {
            if (address.equals(choice.address) && number == choice.number) {
                return choice.confirmed ? 2 : 1;
            }
        }
        return 0;
    }

    public List<Table100Choice> loadChoices(String tableId) {
        List<Table100Choice> choices = new ArrayList<>();
        if (tableId == null || tableId.trim().isEmpty()) {
            return choices;
        }
        try {
            JSONArray items = rawChoices();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                if (tableId.equals(item.optString("tableId", ""))) {
                    choices.add(new Table100Choice(
                            item.optString("address", ""),
                            item.optString("name", ""),
                            item.optInt("number", 0),
                            item.optBoolean("confirmed", false)
                    ));
                }
            }
        } catch (JSONException ignored) {
        }
        return choices;
    }

    private JSONArray rawChoices() throws JSONException {
        String raw = prefs.getString(KEY_TABLE_100_CHOICES, "[]");
        return new JSONArray(raw == null || raw.trim().isEmpty() ? "[]" : raw);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Table100Payload {
        public final String tableId;
        public final String ownerMessage;
        public final String copyText;
        public final String ownerContact;

        Table100Payload(String tableId, String ownerMessage, String copyText, String ownerContact) {
            this.tableId = tableId == null ? "" : tableId;
            this.ownerMessage = ownerMessage == null ? "" : ownerMessage;
            this.copyText = copyText == null ? "" : copyText;
            this.ownerContact = ownerContact == null ? "" : ownerContact;
        }

        public String toMessageBody() {
            try {
                JSONObject json = new JSONObject();
                json.put("gadget", TABLE_100_ID);
                json.put("tableId", tableId);
                json.put("ownerMessage", ownerMessage);
                json.put("copyText", copyText);
                json.put("ownerContact", ownerContact);
                return json.toString();
            } catch (JSONException ignored) {
                return copyText;
            }
        }

        public static Table100Payload parse(String raw) {
            if (raw == null || raw.trim().isEmpty()) {
                return new Table100Payload("", "", "", "");
            }
            try {
                JSONObject json = new JSONObject(raw);
                if (TABLE_100_ID.equals(json.optString("gadget", ""))) {
                    return new Table100Payload(
                            json.optString("tableId", ""),
                            json.optString("ownerMessage", ""),
                            json.optString("copyText", ""),
                            json.optString("ownerContact", "")
                    );
                }
            } catch (JSONException ignored) {
            }
            return new Table100Payload("", "", raw.trim(), "");
        }
    }

    public static final class Table100Choice {
        public final String address;
        public final String name;
        public final int number;
        public final boolean confirmed;

        Table100Choice(String address, String name, int number, boolean confirmed) {
            this.address = address == null ? "" : address;
            this.name = name == null ? "" : name;
            this.number = number;
            this.confirmed = confirmed;
        }
    }
}
