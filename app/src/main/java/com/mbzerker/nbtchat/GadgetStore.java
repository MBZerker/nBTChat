package com.mbzerker.nbtchat;

import android.content.Context;
import android.content.SharedPreferences;

public final class GadgetStore {
    public static final String TABLE_100_ID = "table100";
    private static final String PREFS = "official_gadgets";
    private static final String KEY_TABLE_100_UNTIL = "table100_until";
    private static final String KEY_TABLE_100_TEXT = "table100_text";
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
        prefs.edit().putLong(KEY_TABLE_100_UNTIL, System.currentTimeMillis() + WEEK_MS).apply();
    }

    public String table100Text() {
        return prefs.getString(KEY_TABLE_100_TEXT, "");
    }

    public void saveTable100Text(String text) {
        prefs.edit().putString(KEY_TABLE_100_TEXT, text == null ? "" : text.trim()).apply();
    }
}
