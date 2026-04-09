package com.adoreapps.ai.ads.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;

public class SharePrefUtils {
    private static final String SHARE_PREF_NAME = "multi_language_active";
    @SuppressLint({"StaticFieldLeak"})
    private static SharePrefUtils instance;
    private Context context;
    private SharedPreferences mSharedPreferences;

    private SharePrefUtils() {
    }

    public static SharePrefUtils getInstance() {
        if (instance == null) {
            instance = new SharePrefUtils();
        }

        return instance;
    }

    public void init(Context context) {
        this.context = context;
        this.mSharedPreferences = context.getSharedPreferences("multi_language_active", 0);
    }

    public <T> T get(String key, Class<T> clazz) {
        Object value;

        if (clazz == String.class) {
            value = mSharedPreferences.getString(key, "");
        } else if (clazz == Boolean.class) {
            value = mSharedPreferences.getBoolean(key, false);
        } else if (clazz == Float.class) {
            value = mSharedPreferences.getFloat(key, 0.0F);
        } else if (clazz == Integer.class) {
            value = mSharedPreferences.getInt(key, 0);
        } else if (clazz == Long.class) {
            value = mSharedPreferences.getLong(key, 0L);
        } else {
            String json = mSharedPreferences.getString(key, "");
            value = new Gson().fromJson(json, clazz);
        }

        return clazz.cast(value);
    }

    public boolean getBoolean(String key, boolean def) {
        return this.mSharedPreferences.getBoolean(key, def);
    }

    public String getString(String key, String def) {
        return this.mSharedPreferences.getString(key, def);
    }

    public int getInt(String key, int def) {
        return this.mSharedPreferences.getInt(key, def);
    }

    public long getLong(String key, long def) {
        return this.mSharedPreferences.getLong(key, def);
    }

    public float getFloat(String key, float def) {
        return this.mSharedPreferences.getFloat(key, def);
    }

    public <T> void put(String key, T data) {
        SharedPreferences.Editor editor = this.mSharedPreferences.edit();
        if (data instanceof String) {
            editor.putString(key, (String)data);
        } else if (data instanceof Boolean) {
            editor.putBoolean(key, (Boolean)data);
        } else if (data instanceof Float) {
            editor.putFloat(key, (Float)data);
        } else if (data instanceof Integer) {
            editor.putInt(key, (Integer)data);
        } else if (data instanceof Long) {
            editor.putLong(key, (Long)data);
        } else {
            editor.putString(key, (new Gson()).toJson(data));
        }

        editor.apply();
    }

    public void clear() {
        this.mSharedPreferences.edit().clear().apply();
    }
}
