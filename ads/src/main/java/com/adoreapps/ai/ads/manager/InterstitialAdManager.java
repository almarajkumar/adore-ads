package com.adoreapps.ai.ads.manager;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import com.adoreapps.ai.ads.AdCallback;
import com.adoreapps.ai.ads.core.AdsMobileAdsManager;
import com.adoreapps.ai.ads.wrapper.ApAdError;
import com.adoreapps.ai.ads.wrapper.ApInterstitialAd;
import com.adoreapps.ai.ads.consent.ConsentManager;
import com.adoreapps.ai.ads.billing.PurchaseManager;
import com.adoreapps.ai.ads.PlacementConfig;
import com.adoreapps.ai.ads.interfaces.AdFinished;
import com.adoreapps.ai.ads.dialog.LoadingAdsDialog;
import com.adoreapps.ai.ads.settings.AdConstants;
import com.adoreapps.ai.ads.settings.AdSettingsStore;
import com.adoreapps.ai.ads.settings.AdUnitsConfig;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InterstitialAdManager {

    private static volatile InterstitialAdManager instance;

    private InterstitialAdManager() {}

    public static InterstitialAdManager getInstance() {
        if (instance == null) {
            synchronized (InterstitialAdManager.class) {
                if (instance == null) {
                    instance = new InterstitialAdManager();
                }
            }
        }
        return instance;
    }

    // Placement enable/disable flags — stored in a map for easy remote config control
    private final Map<String, Boolean> placementFlags = new ConcurrentHashMap<>();

    private long cooldownSeconds = AdConstants.DEFAULT_INTERSTITIAL_COOLDOWN_SECONDS;
    private long interstitialTimeoutMs = AdConstants.DEFAULT_INTERSTITIAL_TIMEOUT_MS;

    // Placement map for config-based loading by key
    private final Map<String, AdUnitsConfig> interstitialAdMap = new ConcurrentHashMap<>();

    // Full PlacementConfig (loadMode, backup native, countdown)
    private final Map<String, PlacementConfig> placementConfigMap = new ConcurrentHashMap<>();

    // =========================================================
    // RUNTIME PLACEMENT MANAGEMENT
    // =========================================================

    /**
     * Add or replace a single interstitial ad placement at runtime.
     */
    public void addPlacement(String key, PlacementConfig config) {
        if (key == null || config == null) return;
        if (config.isEnabled() && !config.getAdUnitIds().isEmpty()) {
            interstitialAdMap.put(key, new AdUnitsConfig(
                    config.getAdUnitIds(),
                    config.getViewEventName(),
                    config.getClickEventName()
            ));
            placementConfigMap.put(key, config);
        }
    }

    /**
     * Remove an interstitial ad placement.
     */
    public void removePlacement(String key) {
        if (key != null) {
            interstitialAdMap.remove(key);
            placementConfigMap.remove(key);
        }
    }

    /**
     * Check if a placement is registered.
     */
    public boolean hasPlacement(String key) {
        return interstitialAdMap.containsKey(key);
    }

    /**
     * Load and show an interstitial ad by placement key (registered via addPlacement or config).
     */
    public void loadAndShowByPlacement(Activity activity, String placementKey, AdFinished adFinished) {
        AdUnitsConfig config = interstitialAdMap.get(placementKey);
        if (config == null || config.adUnitIds.isEmpty()) {
            adFinished.onAdFinished();
            return;
        }
        if (PurchaseManager.getInstance().isPurchased()) {
            adFinished.onAdFinished();
            return;
        }
        if (!ConsentManager.getInstance(activity).canRequestAds()) {
            adFinished.onAdFinished();
            return;
        }
        if (isInCooldown(activity)) {
            adFinished.onAdFinished();
            return;
        }
        if (activity.isFinishing() || activity.isDestroyed()) {
            adFinished.onAdFinished();
            return;
        }

        // 1. Try preload cache first
        InterstitialAd preloaded = AdPreloadManager.getInstance()
                .pollInterstitial(activity.getApplicationContext(), placementKey);
        if (preloaded != null) {
            show(activity, preloaded, () -> adFinished.onAdFinished());
            return;
        }

        // 2. Load fresh — respect LoadMode
        PlacementConfig pc = placementConfigMap.get(placementKey);
        ArrayList<String> ids = new ArrayList<>(config.adUnitIds);
        if (pc != null && pc.getLoadMode() == com.adoreapps.ai.ads.settings.LoadMode.SINGLE && !ids.isEmpty()) {
            // Single mode — only try first ad unit
            ids = new ArrayList<>();
            ids.add(config.adUnitIds.get(0));
        }

        LoadingAdsDialog loadingAdsDialog = new LoadingAdsDialog(activity);
        loadingAdsDialog.showWithTimeout(interstitialTimeoutMs, () -> adFinished.onAdFinished());
        loadAdRecursiveWithBackup(activity, placementKey, ids, adFinished, loadingAdsDialog);
    }

    /**
     * Populate interstitial placements from AdoreAdsConfig.
     */
    public void populateFromConfig(com.adoreapps.ai.ads.AdoreAdsConfig adoreConfig) {
        interstitialAdMap.clear();
        placementConfigMap.clear();
        for (Map.Entry<String, PlacementConfig> entry : adoreConfig.getInterstitialPlacements().entrySet()) {
            PlacementConfig pc = entry.getValue();
            if (pc.isEnabled() && !pc.getAdUnitIds().isEmpty()) {
                interstitialAdMap.put(entry.getKey(), new AdUnitsConfig(
                        pc.getAdUnitIds(),
                        pc.getViewEventName(),
                        pc.getClickEventName()
                ));
                placementConfigMap.put(entry.getKey(), pc);
            }
        }
    }

    // =========================================================
    // PLACEMENT FLAGS (Map-based — easy to add new flags from remote config)
    // =========================================================

    /**
     * Set a placement flag by key. Use for remote config or runtime toggles.
     * Examples: setPlacementFlag("show_inter_onb_1", false)
     */
    public void setPlacementFlag(String key, boolean enabled) {
        placementFlags.put(key, enabled);
    }

    /**
     * Check a placement flag. Returns the default value if not set.
     */
    public boolean getPlacementFlag(String key, boolean defaultValue) {
        Boolean value = placementFlags.get(key);
        return value != null ? value : defaultValue;
    }

    public void setCooldownSeconds(long seconds) { this.cooldownSeconds = seconds; }
    public void setInterstitialTimeoutMs(long ms) { this.interstitialTimeoutMs = ms; }
    public long getInterstitialTimeoutMs() { return interstitialTimeoutMs; }

    // =========================================================
    // LOAD & SHOW
    // =========================================================

    public void loadAndShowInterstitialAd(final Activity activity,
                                          AdFinished adFinished,
                                          String interstitialHighAdId,
                                          String interstitialLowAdId,
                                          boolean isHighEnabled,
                                          boolean isLowEnabled) {
        if (PurchaseManager.getInstance().isPurchased()) {
            adFinished.onAdFinished();
            return;
        }
        if (!ConsentManager.getInstance(activity).canRequestAds()) {
            adFinished.onAdFinished();
            return;
        }
        if (!isHighEnabled && !isLowEnabled) {
            adFinished.onAdFinished();
            return;
        }
        if (isInCooldown(activity)) {
            adFinished.onAdFinished();
            return;
        }
        if (activity.isFinishing() || activity.isDestroyed()) {
            adFinished.onAdFinished();
            return;
        }

        LoadingAdsDialog loadingAdsDialog = new LoadingAdsDialog(activity);
        loadingAdsDialog.showWithTimeout(interstitialTimeoutMs, () -> adFinished.onAdFinished());

        try {
            ArrayList<String> interAdsID = new ArrayList<>();
            if (isHighEnabled) {
                interAdsID.add(interstitialHighAdId);
            }
            if (isLowEnabled) {
                interAdsID.add(interstitialLowAdId);
            }
            loadAdRecursive(activity, interAdsID, adFinished, loadingAdsDialog);
        } catch (Exception e) {
            loadingAdsDialog.dismiss();
            adFinished.onAdFinished();
        }
    }

    public void loadAdWithPriorityIds(Activity activity,
                                      ArrayList<String> interstitialAdIds,
                                      boolean isEnabled,
                                      AdFinished adFinished) {
        if (PurchaseManager.getInstance().isPurchased()) {
            adFinished.onAdFinished();
            return;
        }
        if (!ConsentManager.getInstance(activity).canRequestAds()) {
            adFinished.onAdFinished();
            return;
        }
        if (!isEnabled || interstitialAdIds == null || interstitialAdIds.isEmpty() || isInCooldown(activity)) {
            adFinished.onAdFinished();
            return;
        }

        LoadingAdsDialog loadingAdsDialog = new LoadingAdsDialog(activity);
        loadingAdsDialog.showWithTimeout(interstitialTimeoutMs, () -> adFinished.onAdFinished());

        loadAdRecursive(activity, interstitialAdIds, adFinished, loadingAdsDialog);
    }

    private void loadAdRecursive(Activity activity,
                                 ArrayList<String> interstitialAdIds,
                                 AdFinished adFinished,
                                 LoadingAdsDialog loadingAdsDialog) {

        Runnable showNextScreen = () -> {
            if (!loadingAdsDialog.isTimedOut()) {
                adFinished.onAdFinished();
            }
            loadingAdsDialog.dismiss();
        };

        AdsMobileAdsManager.getInstance().loadAlternateInterstitialAds(
                activity,
                interstitialAdIds,
                new AdCallback() {
                    @Override
                    public void onResultInterstitialAd(ApInterstitialAd interstitialAd) {
                        super.onResultInterstitialAd(interstitialAd);
                        if (loadingAdsDialog.isTimedOut()) return;
                        loadingAdsDialog.dismiss();
                        show(activity, interstitialAd.getInterstitialAd(), showNextScreen);
                    }

                    @Override
                    public void onNextScreen() {
                        super.onNextScreen();
                        if (loadingAdsDialog.isTimedOut()) return;
                        showNextScreen.run();
                    }

                    @Override
                    public void onAdFailedToLoad(ApAdError i) {
                        super.onAdFailedToLoad(i);
                        if (loadingAdsDialog.isTimedOut()) return;
                        InterstitialAd defaultAd = DefaultAdPool.getInstance().consumeDefaultInterstitialAd(activity);
                        if (defaultAd != null) {
                            loadingAdsDialog.dismiss();
                            show(activity, defaultAd, showNextScreen);
                        } else {
                            showNextScreen.run();
                        }
                    }
                }
        );
    }

    /**
     * Load interstitial with full-screen native fallback when all ad unit IDs
     * and DefaultAdPool fail. Uses placement's backupNativePlacementKey.
     */
    private void loadAdRecursiveWithBackup(Activity activity, String placementKey,
                                             ArrayList<String> interstitialAdIds,
                                             AdFinished adFinished,
                                             LoadingAdsDialog loadingAdsDialog) {
        Runnable showNextScreen = () -> {
            if (!loadingAdsDialog.isTimedOut()) {
                adFinished.onAdFinished();
            }
            loadingAdsDialog.dismiss();
        };

        AdsMobileAdsManager.getInstance().loadAlternateInterstitialAds(
                activity,
                interstitialAdIds,
                new AdCallback() {
                    @Override
                    public void onResultInterstitialAd(ApInterstitialAd interstitialAd) {
                        super.onResultInterstitialAd(interstitialAd);
                        if (loadingAdsDialog.isTimedOut()) return;
                        loadingAdsDialog.dismiss();
                        show(activity, interstitialAd.getInterstitialAd(), showNextScreen);
                    }

                    @Override
                    public void onNextScreen() {
                        super.onNextScreen();
                        if (loadingAdsDialog.isTimedOut()) return;
                        showNextScreen.run();
                    }

                    @Override
                    public void onAdFailedToLoad(ApAdError i) {
                        super.onAdFailedToLoad(i);
                        if (loadingAdsDialog.isTimedOut()) return;

                        // Try default fallback pool first
                        InterstitialAd defaultAd = DefaultAdPool.getInstance()
                                .consumeDefaultInterstitialAd(activity);
                        if (defaultAd != null) {
                            loadingAdsDialog.dismiss();
                            show(activity, defaultAd, showNextScreen);
                            return;
                        }

                        // All interstitial options exhausted — try backup native if configured
                        PlacementConfig pc = placementConfigMap.get(placementKey);
                        if (pc != null && pc.hasBackupNative()) {
                            loadingAdsDialog.dismiss();
                            // Launch full-screen native as fallback
                            com.adoreapps.ai.ads.dialog.FullScreenNativeAdActivity.show(
                                    activity,
                                    pc.getBackupNativePlacementKey(),
                                    pc.getBackupCountdownSeconds(),
                                    true
                            );
                            // Apply cooldown as if interstitial was shown
                            startCooldown(activity);
                            adFinished.onAdFinished();
                        } else {
                            showNextScreen.run();
                        }
                    }
                }
        );
    }

    /**
     * Check if interstitial is in cooldown (persisted across process death).
     */
    private boolean isInCooldown(Activity activity) {
        if (activity == null) return false;
        long cooldownEnd = AdSettingsStore.getInstance(activity)
                .getLong(AdConstants.PREF_INTERSTITIAL_COOLDOWN_END, 0);
        return System.currentTimeMillis() < cooldownEnd;
    }

    private void startCooldown(Activity activity) {
        if (activity == null) return;
        long cooldownEnd = System.currentTimeMillis() + (cooldownSeconds * 1000);
        AdSettingsStore.getInstance(activity)
                .setLong(AdConstants.PREF_INTERSTITIAL_COOLDOWN_END, cooldownEnd);
    }

    public void show(Activity activity, InterstitialAd interstitialAd, Runnable showNextScreen) {
        if (interstitialAd != null) {
            AdsMobileAdsManager.getInstance().showInterstitial(
                    activity,
                    interstitialAd,
                    new AdCallback() {
                        @Override
                        public void onNextScreen() {
                            super.onNextScreen();
                            startCooldown(activity);
                            if (showNextScreen != null) showNextScreen.run();
                        }

                        @Override
                        public void onAdFailedToShowFullScreenContent(ApAdError apAdError) {
                            super.onAdFailedToShowFullScreenContent(apAdError);
                            if (showNextScreen != null) showNextScreen.run();
                        }
                    }
            );
        } else {
            if (showNextScreen != null) showNextScreen.run();
        }
    }
}
