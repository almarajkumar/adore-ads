package com.adoreapps.ai.ads.utils;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.XmlRes;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

/**
 * Singleton wrapper around Firebase Remote Config.
 *
 * Uses the "activate-first" strategy from Funswap optimization:
 * 1. activate() — instantly applies cached/default values (~0ms)
 * 2. fetch() — downloads fresh values in background for next session
 *
 * This avoids the 10-30s blocking delay of fetchAndActivate().
 */
public class RemoteConfigManager {

    private static final String TAG = "RemoteConfigManager";
    private static volatile RemoteConfigManager instance;
    private final FirebaseRemoteConfig remoteConfig;
    private boolean isReady = false;

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

    public void setDefaultsResourceId(@XmlRes int xmlResId) {
        if (xmlResId != 0) {
            remoteConfig.setDefaultsAsync(xmlResId);
        }
    }

    /**
     * Fast init: activate cached values instantly, then fetch fresh in background.
     *
     * Step 1: activate() — applies previously fetched values (or defaults). Instant.
     * Step 2: callback fires — app can proceed immediately with cached config.
     * Step 3: fetch() runs in background — fresh values available for next activate().
     *
     * On first-ever launch (no cache), defaults from setDefaultsResourceId() are used.
     */
    public void init(@NonNull OnRemoteConfigReadyListener listener) {
        isReady = false;

        // Step 1: Activate cached/default values instantly
        remoteConfig.activate().addOnCompleteListener(activateTask -> {
            isReady = true;
            Log.i(TAG, "Remote config activated (cached/defaults)");
            listener.onReady(true);

            // Step 2: Fetch fresh values in background for next session
            remoteConfig.fetch().addOnCompleteListener(fetchTask -> {
                if (fetchTask.isSuccessful()) {
                    Log.i(TAG, "Remote config fetched in background (available next activate)");
                } else {
                    Log.w(TAG, "Background remote config fetch failed");
                }
            });
        });
    }

    /**
     * Full blocking fetch+activate. Use only when you need fresh values immediately
     * (e.g., user manually triggered "refresh config").
     */
    public void fetchAndActivateNow(@NonNull OnRemoteConfigReadyListener listener) {
        isReady = false;
        remoteConfig.fetchAndActivate().addOnCompleteListener(task -> {
            isReady = true;
            boolean success = task.isSuccessful();
            if (!success) {
                Log.w(TAG, "fetchAndActivate failed, using cached/defaults");
            } else {
                Log.i(TAG, "fetchAndActivate succeeded");
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

    public FirebaseRemoteConfig getRemoteConfig() {
        return remoteConfig;
    }

    // =========================================================
    // TYPED GETTERS
    // =========================================================

    public String getString(String key, String defaultValue) {
        String value = remoteConfig.getString(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        // Only trust the remote value if the key was explicitly set in Firebase
        // (or via XML defaults). If source is STATIC, the key doesn't exist.
        int source = remoteConfig.getValue(key).getSource();
        if (source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE
                || source == FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT) {
            return remoteConfig.getBoolean(key);
        }
        return defaultValue;
    }

    public long getLong(String key, long defaultValue) {
        long value = remoteConfig.getLong(key);
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

    public String getJson(String key) {
        return remoteConfig.getString(key);
    }
}
