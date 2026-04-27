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
import com.adoreapps.ai.ads.event.AdType;
import com.adoreapps.ai.ads.event.FirebaseAnalyticsEvents;
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
            FirebaseAnalyticsEvents.getInstance().logShowBlocked(activity, placementKey,
                    AdType.INTERSTITIAL, "no_placement");
            adFinished.onAdFinished();
            return;
        }
        if (PurchaseManager.getInstance().isPurchased()) {
            FirebaseAnalyticsEvents.getInstance().logShowBlocked(activity, placementKey,
                    AdType.INTERSTITIAL, "premium");
            adFinished.onAdFinished();
            return;
        }
        if (!ConsentManager.getInstance(activity).canRequestAds()) {
            FirebaseAnalyticsEvents.getInstance().logShowBlocked(activity, placementKey,
                    AdType.INTERSTITIAL, "no_consent");
            adFinished.onAdFinished();
            return;
        }
        if (isInCooldown(activity)) {
            FirebaseAnalyticsEvents.getInstance().logShowBlocked(activity, placementKey,
                    AdType.INTERSTITIAL, "cooldown");
            adFinished.onAdFinished();
            return;
        }
        if (activity.isFinishing() || activity.isDestroyed()) {
            FirebaseAnalyticsEvents.getInstance().logShowBlocked(activity, placementKey,
                    AdType.INTERSTITIAL, "activity_destroyed");
            adFinished.onAdFinished();
            return;
        }

        // 1. Try preload cache first
        InterstitialAd preloaded = AdPreloadManager.getInstance()
                .pollInterstitial(activity.getApplicationContext(), placementKey);
        if (preloaded != null) {
            FirebaseAnalyticsEvents.getInstance().logCacheHit(activity, placementKey,
                    preloaded.getAdUnitId(), AdType.INTERSTITIAL);
            showTracked(activity, placementKey, preloaded, () -> adFinished.onAdFinished());
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

        // Fire request event
        if (!ids.isEmpty()) {
            FirebaseAnalyticsEvents.getInstance().logRequest(activity, placementKey,
                    ids.get(0), AdType.INTERSTITIAL);
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
        final long loadStartTime = System.currentTimeMillis();
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
                        long latency = System.currentTimeMillis() - loadStartTime;
                        if (loadingAdsDialog.isTimedOut()) return;
                        loadingAdsDialog.dismiss();
                        InterstitialAd ad = interstitialAd.getInterstitialAd();
                        FirebaseAnalyticsEvents.getInstance().logLoadSuccess(activity, placementKey,
                                ad != null ? ad.getAdUnitId() : null, AdType.INTERSTITIAL, latency);
                        showTracked(activity, placementKey, ad, showNextScreen);
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
                        long latency = System.currentTimeMillis() - loadStartTime;
                        int errCode = i != null && i.getLoadAdError() != null ? i.getLoadAdError().getCode() : -1;
                        String errMsg = i != null ? i.getMessage() : "unknown";
                        FirebaseAnalyticsEvents.getInstance().logLoadFailed(activity, placementKey,
                                interstitialAdIds.isEmpty() ? null : interstitialAdIds.get(0),
                                AdType.INTERSTITIAL, errCode, errMsg, latency);
                        if (loadingAdsDialog.isTimedOut()) return;

                        // Try default fallback pool first
                        InterstitialAd defaultAd = DefaultAdPool.getInstance()
                                .consumeDefaultInterstitialAd(activity);
                        if (defaultAd != null) {
                            loadingAdsDialog.dismiss();
                            FirebaseAnalyticsEvents.getInstance().logFallbackUsed(activity, placementKey,
                                    defaultAd.getAdUnitId(), AdType.INTERSTITIAL, "default_pool");
                            showTracked(activity, placementKey, defaultAd, showNextScreen);
                            return;
                        }

                        // All interstitial options exhausted — try backup native if configured
                        PlacementConfig pc = placementConfigMap.get(placementKey);
                        if (pc != null && pc.hasBackupNative()) {
                            loadingAdsDialog.dismiss();
                            FirebaseAnalyticsEvents.getInstance().logFallbackUsed(activity, placementKey,
                                    null, AdType.INTERSTITIAL, "native_backup");
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

    /**
     * Show interstitial with placement-aware analytics events.
     * Fires: ad_show, ad_show_failed, ad_click, ad_dismissed.
     */
    private void showTracked(Activity activity, String placementKey, InterstitialAd interstitialAd,
                              Runnable showNextScreen) {
        if (interstitialAd == null) {
            FirebaseAnalyticsEvents.getInstance().logShowFailed(activity, placementKey,
                    null, AdType.INTERSTITIAL, -1, "ad_is_null");
            if (showNextScreen != null) showNextScreen.run();
            return;
        }
        final String adUnitId = interstitialAd.getAdUnitId();
        AdsMobileAdsManager.getInstance().showInterstitial(
                activity,
                interstitialAd,
                new AdCallback() {
                    @Override
                    public void onAdShowedFullScreenContent() {
                        super.onAdShowedFullScreenContent();
                        FirebaseAnalyticsEvents.getInstance().logShow(activity, placementKey,
                                adUnitId, AdType.INTERSTITIAL);
                    }

                    @Override
                    public void onAdClicked() {
                        super.onAdClicked();
                        FirebaseAnalyticsEvents.getInstance().logClick(activity, placementKey,
                                adUnitId, AdType.INTERSTITIAL);
                    }

                    @Override
                    public void onNextScreen() {
                        super.onNextScreen();
                        FirebaseAnalyticsEvents.getInstance().logDismissed(activity, placementKey,
                                adUnitId, AdType.INTERSTITIAL);
                        startCooldown(activity);
                        if (showNextScreen != null) showNextScreen.run();
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(ApAdError apAdError) {
                        super.onAdFailedToShowFullScreenContent(apAdError);
                        int code = apAdError != null && apAdError.getLoadAdError() != null
                                ? apAdError.getLoadAdError().getCode() : -1;
                        String msg = apAdError != null ? apAdError.getMessage() : "unknown";
                        FirebaseAnalyticsEvents.getInstance().logShowFailed(activity, placementKey,
                                adUnitId, AdType.INTERSTITIAL, code, msg);
                        if (showNextScreen != null) showNextScreen.run();
                    }
                }
        );
    }
}
