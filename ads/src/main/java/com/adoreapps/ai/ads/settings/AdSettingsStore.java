package com.adoreapps.ai.ads.settings;

import android.content.Context;
import android.content.SharedPreferences;

public class AdSettingsStore {
    private static final String PREF_NAME = "adore_pc_ads_config";
    private final SharedPreferences prefs;

    private static volatile AdSettingsStore instance;

    private AdSettingsStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static AdSettingsStore getInstance(Context context) {
        if (instance == null) {
            synchronized (AdSettingsStore.class) {
                if (instance == null) {
                    instance = new AdSettingsStore(context);
                }
            }
        }
        return instance;
    }

    public void setBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    public boolean getBoolean(String key) {
        return prefs.getBoolean(key, false);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }

    public void setString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    public String getString(String key, String defaultValue) {
        return prefs.getString(key, defaultValue);
    }

    public void setLong(String key, long value) {
        prefs.edit().putLong(key, value).apply();
    }

    public long getLong(String key, long defaultValue) {
        return prefs.getLong(key, defaultValue);
    }

    public static long getRefreshInterval() {
        if (instance == null) return AdConstants.DEFAULT_REFRESH_INTERVAL_SECONDS;
        return instance.getLong("native_refresh_interval", AdConstants.DEFAULT_REFRESH_INTERVAL_SECONDS);
    }

    public void remove(String key) {
        prefs.edit().remove(key).apply();
    }
}
