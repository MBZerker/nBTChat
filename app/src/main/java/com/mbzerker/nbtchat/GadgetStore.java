package com.mbzerker.nbtchat;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class GadgetStore {
    public static final String TABLE_100_ID = "table100";
    public static final String TABLE_100_TITLE = "Cartela de eventos";
    public static final String TABLE_100_FOOTER = "Produto destinado exclusivamente para organizacao de eventos familiares, recreativos e chas beneficentes.";
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
    private static final String KEY_TABLE_100_PENDING_URL = "table100_pending_url";
    private static final String KEY_TABLE_100_PENDING_DEVICE = "table100_pending_device";
    private static final String KEY_TABLE_100_PENDING_AT = "table100_pending_at";
    private static final String KEY_TABLE_100_RESERVATIONS = "table100_reservations";
    private static final String KEY_TABLE_100_RESERVATION_HOURS = "table100_reservation_hours";
    private static final long FIFTEEN_DAYS_MS = 15L * 24L * 60L * 60L * 1000L;
    private static final long PENDING_PAYMENT_TTL_MS = 24L * 60L * 60L * 1000L;
    private static final int DEFAULT_RESERVATION_HOURS = 24;

    private final EncryptedPrefs prefs;

    public GadgetStore(Context context) {
        prefs = new EncryptedPrefs(context, PREFS);
    }

    public boolean hasTable100() {
        return table100Until() > System.currentTimeMillis();
    }

    public long table100Until() {
        return prefs.getLong(KEY_TABLE_100_UNTIL, 0L);
    }

    public void buyTable100() {
        activateTable100Until(System.currentTimeMillis() + FIFTEEN_DAYS_MS);
    }

    public void activateTable100Until(long expiresAt) {
        if (expiresAt <= System.currentTimeMillis()) {
            return;
        }
        String instance = prefs.getString(KEY_TABLE_100_INSTANCE, "");
        EncryptedPrefs.Editor editor = prefs.edit()
                .putLong(KEY_TABLE_100_UNTIL, expiresAt);
        if (instance == null || instance.trim().isEmpty()) {
            editor.putString(KEY_TABLE_100_INSTANCE, "cartela-" + Long.toHexString(System.currentTimeMillis()));
        }
        editor.apply();
    }

    public void savePendingTable100Payment(String checkoutUrl, String deviceId) {
        if (checkoutUrl == null || checkoutUrl.trim().isEmpty() || deviceId == null || deviceId.trim().isEmpty()) {
            return;
        }
        prefs.edit()
                .putString(KEY_TABLE_100_PENDING_URL, checkoutUrl.trim())
                .putString(KEY_TABLE_100_PENDING_DEVICE, deviceId.trim())
                .putLong(KEY_TABLE_100_PENDING_AT, System.currentTimeMillis())
                .apply();
    }

    public boolean hasPendingTable100Payment() {
        long startedAt = prefs.getLong(KEY_TABLE_100_PENDING_AT, 0L);
        return !pendingTable100CheckoutUrl().isEmpty()
                && startedAt > 0L
                && System.currentTimeMillis() - startedAt < PENDING_PAYMENT_TTL_MS;
    }

    public String pendingTable100CheckoutUrl() {
        String url = prefs.getString(KEY_TABLE_100_PENDING_URL, "");
        return url == null ? "" : url.trim();
    }

    public String pendingTable100DeviceId() {
        String deviceId = prefs.getString(KEY_TABLE_100_PENDING_DEVICE, "");
        return deviceId == null ? "" : deviceId.trim();
    }

    public void clearPendingTable100Payment() {
        prefs.edit()
                .putString(KEY_TABLE_100_PENDING_URL, "")
                .putString(KEY_TABLE_100_PENDING_DEVICE, "")
                .putLong(KEY_TABLE_100_PENDING_AT, 0L)
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

    public boolean table100ReservationsEnabled() {
        return prefs.getBoolean(KEY_TABLE_100_RESERVATIONS, false);
    }

    public int table100ReservationHours() {
        int hours = (int) prefs.getLong(KEY_TABLE_100_RESERVATION_HOURS, DEFAULT_RESERVATION_HOURS);
        return Math.max(1, Math.min(168, hours));
    }

    public void saveTable100ReservationSettings(boolean enabled, int hours) {
        int cleanHours = Math.max(1, Math.min(168, hours));
        prefs.edit()
                .putBoolean(KEY_TABLE_100_RESERVATIONS, enabled)
                .putLong(KEY_TABLE_100_RESERVATION_HOURS, cleanHours)
                .apply();
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
        String tableId = table100InstanceId();
        return new Table100Payload(tableId, table100OwnerMessage(), table100CopyText(), table100OwnerContact(),
                "", lockedNumbers(tableId), table100ReservationsEnabled(), table100ReservationHours());
    }

    public void mergeOnlineCartela(StorePaymentClient.CartelaState state) {
        if (state == null || state.tableId.isEmpty()) {
            return;
        }
        if (state.tableId.equals(currentTable100InstanceId())) {
            saveTable100ReservationSettings(state.allowReservations, state.reservationHours);
        }
        removeChoicesForTable(state.tableId);
        for (StorePaymentClient.CartelaChoice choice : state.choices) {
            saveChoice(state.tableId, choice.chooserDeviceId, choice.number, choice.chooserName,
                    choice.confirmed, choice.reserved, choice.reservationExpiresAt);
        }
    }

    private String currentTable100InstanceId() {
        String id = prefs.getString(KEY_TABLE_100_INSTANCE, "");
        return id == null ? "" : id;
    }

    public List<Integer> lockedNumbers(String tableId) {
        List<Integer> numbers = new ArrayList<>();
        for (Table100Choice choice : loadChoices(tableId)) {
            if (choice.number >= 1 && choice.number <= 100 && !numbers.contains(choice.number)) {
                numbers.add(choice.number);
            }
        }
        return numbers;
    }

    public void saveChoice(String tableId, String address, int number, String name, boolean confirmed) {
        saveChoice(tableId, address, number, name, confirmed, false, 0L);
    }

    public void saveChoice(String tableId, String address, int number, String name, boolean confirmed, boolean reserved, long reservationExpiresAt) {
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
                    item.put("reserved", reserved && !confirmed);
                    item.put("reservationExpiresAt", confirmed ? 0L : reservationExpiresAt);
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
                item.put("reserved", reserved && !confirmed);
                item.put("reservationExpiresAt", confirmed ? 0L : reservationExpiresAt);
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
                    if (confirmed) {
                        item.put("reserved", false);
                        item.put("reservationExpiresAt", 0L);
                    }
                    prefs.edit().putString(KEY_TABLE_100_CHOICES, items.toString()).apply();
                    return;
                }
            }
        } catch (JSONException ignored) {
        }
    }

    public void removeChoice(String tableId, String address, int number) {
        if (tableId == null || address == null) {
            return;
        }
        try {
            JSONArray items = rawChoices();
            JSONArray kept = new JSONArray();
            boolean changed = false;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                if (tableId.equals(item.optString("tableId", ""))
                        && address.equals(item.optString("address", ""))
                        && number == item.optInt("number", -1)) {
                    changed = true;
                    continue;
                }
                kept.put(item);
            }
            if (changed) {
                prefs.edit().putString(KEY_TABLE_100_CHOICES, kept.toString()).apply();
            }
        } catch (JSONException ignored) {
        }
    }

    private void removeChoicesForTable(String tableId) {
        if (tableId == null) {
            return;
        }
        try {
            JSONArray items = rawChoices();
            JSONArray kept = new JSONArray();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                if (!tableId.equals(item.optString("tableId", ""))) {
                    kept.put(item);
                }
            }
            prefs.edit().putString(KEY_TABLE_100_CHOICES, kept.toString()).apply();
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

    public Table100Choice choiceForNumber(String tableId, int number) {
        for (Table100Choice choice : loadChoices(tableId)) {
            if (choice.number == number) {
                return choice;
            }
        }
        return null;
    }

    public List<Table100Choice> choicesForAddress(String tableId, String address) {
        List<Table100Choice> choices = new ArrayList<>();
        if (address == null) {
            return choices;
        }
        for (Table100Choice choice : loadChoices(tableId)) {
            if (address.equals(choice.address)) {
                choices.add(choice);
            }
        }
        return choices;
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
                            item.optBoolean("confirmed", false),
                            item.optBoolean("reserved", false),
                            item.optLong("reservationExpiresAt", 0L)
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
        public final String ownerDeviceId;
        public final List<Integer> lockedNumbers;
        public final boolean allowReservations;
        public final int reservationHours;

        Table100Payload(String tableId, String ownerMessage, String copyText, String ownerContact) {
            this(tableId, ownerMessage, copyText, ownerContact, "", new ArrayList<>());
        }

        Table100Payload(String tableId, String ownerMessage, String copyText, String ownerContact, List<Integer> lockedNumbers) {
            this(tableId, ownerMessage, copyText, ownerContact, "", lockedNumbers);
        }

        Table100Payload(String tableId, String ownerMessage, String copyText, String ownerContact, String ownerDeviceId, List<Integer> lockedNumbers) {
            this(tableId, ownerMessage, copyText, ownerContact, ownerDeviceId, lockedNumbers, false, DEFAULT_RESERVATION_HOURS);
        }

        Table100Payload(String tableId, String ownerMessage, String copyText, String ownerContact,
                        String ownerDeviceId, List<Integer> lockedNumbers, boolean allowReservations, int reservationHours) {
            this.tableId = tableId == null ? "" : tableId;
            this.ownerMessage = ownerMessage == null ? "" : ownerMessage;
            this.copyText = copyText == null ? "" : copyText;
            this.ownerContact = ownerContact == null ? "" : ownerContact;
            this.ownerDeviceId = ownerDeviceId == null ? "" : ownerDeviceId;
            this.lockedNumbers = cleanLockedNumbers(lockedNumbers);
            this.allowReservations = allowReservations;
            this.reservationHours = Math.max(1, Math.min(168, reservationHours));
        }

        public String toMessageBody() {
            try {
                JSONObject json = new JSONObject();
                json.put("gadget", TABLE_100_ID);
                json.put("tableId", tableId);
                json.put("ownerMessage", ownerMessage);
                json.put("copyText", copyText);
                json.put("ownerContact", ownerContact);
                json.put("ownerDeviceId", ownerDeviceId);
                json.put("lockedNumbers", numbersToJson(lockedNumbers));
                json.put("allowReservations", allowReservations);
                json.put("reservationHours", reservationHours);
                return json.toString();
            } catch (JSONException ignored) {
                return copyText;
            }
        }

        public boolean hasLockedNumber(int number) {
            return lockedNumbers.contains(number);
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
                            json.optString("ownerContact", ""),
                            json.optString("ownerDeviceId", ""),
                            numbersFromJson(json.optJSONArray("lockedNumbers")),
                            json.optBoolean("allowReservations", false),
                            json.optInt("reservationHours", DEFAULT_RESERVATION_HOURS)
                    );
                }
            } catch (JSONException ignored) {
            }
            return new Table100Payload("", "", raw.trim(), "");
        }

        private static List<Integer> cleanLockedNumbers(List<Integer> numbers) {
            List<Integer> clean = new ArrayList<>();
            if (numbers == null) {
                return clean;
            }
            for (Integer value : numbers) {
                if (value != null && value >= 1 && value <= 100 && !clean.contains(value)) {
                    clean.add(value);
                }
            }
            return clean;
        }

        private static JSONArray numbersToJson(List<Integer> numbers) {
            JSONArray array = new JSONArray();
            for (Integer number : cleanLockedNumbers(numbers)) {
                array.put(number);
            }
            return array;
        }

        private static List<Integer> numbersFromJson(JSONArray array) {
            List<Integer> numbers = new ArrayList<>();
            if (array == null) {
                return numbers;
            }
            for (int i = 0; i < array.length(); i++) {
                int number = array.optInt(i, 0);
                if (number >= 1 && number <= 100 && !numbers.contains(number)) {
                    numbers.add(number);
                }
            }
            return numbers;
        }
    }

    public static final class Table100Choice {
        public final String address;
        public final String name;
        public final int number;
        public final boolean confirmed;
        public final boolean reserved;
        public final long reservationExpiresAt;

        Table100Choice(String address, String name, int number, boolean confirmed) {
            this(address, name, number, confirmed, false, 0L);
        }

        Table100Choice(String address, String name, int number, boolean confirmed, boolean reserved, long reservationExpiresAt) {
            this.address = address == null ? "" : address;
            this.name = name == null ? "" : name;
            this.number = number;
            this.confirmed = confirmed;
            this.reserved = reserved && !confirmed;
            this.reservationExpiresAt = confirmed ? 0L : reservationExpiresAt;
        }
    }
}
