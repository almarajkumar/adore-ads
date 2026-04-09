package com.adoreapps.ai.ads.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;


public class RemoteConfigManager {
    private static RemoteConfigManager instance;
    private final FirebaseRemoteConfig remoteConfig;
    private boolean isReady = false;

    private RemoteConfigManager() {
        remoteConfig = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0)
                .build();
        remoteConfig.setConfigSettingsAsync(configSettings);
    }

    public static RemoteConfigManager getInstance() {
        if (instance == null) {

            instance = new RemoteConfigManager();
        }
        return instance;
    }

    public void init(@NonNull Context context, @NonNull Runnable onReadyCallback) {
        isReady = false;
        remoteConfig.fetchAndActivate().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                isReady = true;
                onReadyCallback.run();
                return;
            }

            isReady = true;

            onReadyCallback.run();

        });
    }



    public boolean isRemoteConfigReady() {
        return isReady;
    }

    public FirebaseRemoteConfig getRemoteConfig() {
        return remoteConfig;
    }
}
