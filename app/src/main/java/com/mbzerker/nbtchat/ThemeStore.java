package com.mbzerker.nbtchat;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

public final class ThemeStore {
    private static final String PREFS = "appearance";
    private static final String KEY_DARK = "dark";

    private final Context context;
    private final SharedPreferences prefs;

    public ThemeStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isDarkMode() {
        if (prefs.contains(KEY_DARK)) {
            return prefs.getBoolean(KEY_DARK, false);
        }
        int mask = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mask == Configuration.UI_MODE_NIGHT_YES;
    }

    public void setDarkMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_DARK, enabled).apply();
    }
}
