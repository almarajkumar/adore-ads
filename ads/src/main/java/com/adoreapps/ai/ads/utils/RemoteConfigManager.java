package com.adoreapps.ai.ads.utils;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.XmlRes;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

/**
 * Singleton wrapper around Firebase Remote Config.
 * Provides typed getters and integrates with the Adore Ads library
 * for dynamic ad configuration from the Firebase Console.
 */
public class RemoteConfigManager {

    private static final String TAG = "RemoteConfigManager";
    private static volatile RemoteConfigManager instance;
    private final FirebaseRemoteConfig remoteConfig;
    private boolean isReady = false;

    /**
     * Callback for remote config fetch completion.
     */
    public interface OnRemoteConfigReadyListener {
        void onReady(boolean success);
    }

    private RemoteConfigManager() {
        remoteConfig = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0)
                .build();
        remoteConfig.setConfigSettingsAsync(configSettings);
    }

    public static RemoteConfigManager getInstance() {
        if (instance == null) {
            synchronized (RemoteConfigManager.class) {
                if (instance == null) {
                    instance = new RemoteConfigManager();
                }
            }
        }
        return instance;
    }

    /**
     * Set XML defaults resource (e.g. R.xml.remote_config_defaults).
     * Call before init() to ensure defaults are available on first launch.
     */
    public void setDefaultsResourceId(@XmlRes int xmlResId) {
        if (xmlResId != 0) {
            remoteConfig.setDefaultsAsync(xmlResId);
        }
    }

    /**
     * Fetch and activate remote config, then invoke callback.
     * On failure, activated cache (or defaults) are still usable.
     */
    public void init(@NonNull OnRemoteConfigReadyListener listener) {
        isReady = false;
        remoteConfig.fetchAndActivate().addOnCompleteListener(task -> {
            isReady = true;
            boolean success = task.isSuccessful();
            if (!success) {
                Log.w(TAG, "Remote config fetch failed, using cached/defaults");
            } else {
                Log.i(TAG, "Remote config fetched and activated");
            }
            listener.onReady(success);
        });
    }

    /**
     * Legacy init with Runnable callback (backward compatible).
     */
    public void init(@NonNull Runnable onReadyCallback) {
        init(success -> onReadyCallback.run());
    }

    // =========================================================
    // STATE
    // =========================================================

    public boolean isRemoteConfigReady() {
        return isReady;
    }

    /**
     * Direct access to FirebaseRemoteConfig for app-specific keys.
     */
    public FirebaseRemoteConfig getRemoteConfig() {
        return remoteConfig;
    }

    // =========================================================
    // TYPED GETTERS (convenience wrappers)
    // =========================================================

    public String getString(String key, String defaultValue) {
        String value = remoteConfig.getString(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        // FirebaseRemoteConfig.getBoolean returns false for missing keys,
        // so check if the key was actually set in remote config
        if (remoteConfig.getInfo().getLastFetchStatus() == FirebaseRemoteConfig.LAST_FETCH_STATUS_SUCCESS
                || remoteConfig.getValue(key).getSource() != FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT) {
            return remoteConfig.getBoolean(key);
        }
        return defaultValue;
    }

    public long getLong(String key, long defaultValue) {
        long value = remoteConfig.getLong(key);
        // Remote config returns 0 for missing keys; use default if 0 and not explicitly set
        if (value == 0 && remoteConfig.getValue(key).getSource() == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) {
            return defaultValue;
        }
        return value;
    }

    public double getDouble(String key, double defaultValue) {
        double value = remoteConfig.getDouble(key);
        if (value == 0.0 && remoteConfig.getValue(key).getSource() == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) {
            return defaultValue;
        }
        return value;
    }

    /**
     * Get a JSON string value (for parsing RemoteAdUnit arrays).
     */
    public String getJson(String key) {
        return remoteConfig.getString(key);
    }
}
