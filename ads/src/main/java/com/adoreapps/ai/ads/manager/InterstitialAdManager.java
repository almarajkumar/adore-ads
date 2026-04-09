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

    // Placement enable/disable flags (instance, not static)
    private volatile boolean showInterstitialAd = true;
    private boolean showInterOnb1 = false;
    private boolean showInterOnb2 = false;
    private boolean showInterMenu1 = false;
    private boolean showInterMenu2 = false;
    private boolean showInterList1 = false;
    private boolean showInterList2 = false;
    private boolean showInterPicture1 = false;
    private boolean showInterPicture2 = false;
    private boolean showInterStore1 = true;
    private boolean showInterStore2 = true;
    private boolean showInterGallery1 = true;
    private boolean showInterGallery2 = true;
    private boolean showInterSave1 = true;
    private boolean showInterSave2 = true;
    private boolean showInterHome1 = true;
    private boolean showInterHome2 = true;
    private boolean showInterShareBack = true;
    private boolean showInterShareHome = true;

    private long cooldownSeconds = AdConstants.DEFAULT_INTERSTITIAL_COOLDOWN_SECONDS;

    // Placement map for config-based loading by key
    private final Map<String, AdUnitsConfig> interstitialAdMap = new ConcurrentHashMap<>();

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
        }
    }

    /**
     * Remove an interstitial ad placement.
     */
    public void removePlacement(String key) {
        if (key != null) interstitialAdMap.remove(key);
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
        loadAdWithPriorityIds(activity, new ArrayList<>(config.adUnitIds), true, adFinished);
    }

    /**
     * Populate interstitial placements from AdoreAdsConfig.
     */
    public void populateFromConfig(com.adoreapps.ai.ads.AdoreAdsConfig adoreConfig) {
        interstitialAdMap.clear();
        for (Map.Entry<String, PlacementConfig> entry : adoreConfig.getInterstitialPlacements().entrySet()) {
            PlacementConfig pc = entry.getValue();
            if (pc.isEnabled() && !pc.getAdUnitIds().isEmpty()) {
                interstitialAdMap.put(entry.getKey(), new AdUnitsConfig(
                        pc.getAdUnitIds(),
                        pc.getViewEventName(),
                        pc.getClickEventName()
                ));
            }
        }
    }

    // =========================================================
    // SETTERS for placement flags (called from remote config etc.)
    // =========================================================

    public void setShowInterstitialAd(boolean show) { this.showInterstitialAd = show; }
    public void setShowInterOnb1(boolean show) { this.showInterOnb1 = show; }
    public void setShowInterOnb2(boolean show) { this.showInterOnb2 = show; }
    public void setShowInterMenu1(boolean show) { this.showInterMenu1 = show; }
    public void setShowInterMenu2(boolean show) { this.showInterMenu2 = show; }
    public void setShowInterList1(boolean show) { this.showInterList1 = show; }
    public void setShowInterList2(boolean show) { this.showInterList2 = show; }
    public void setShowInterPicture1(boolean show) { this.showInterPicture1 = show; }
    public void setShowInterPicture2(boolean show) { this.showInterPicture2 = show; }
    public void setShowInterStore1(boolean show) { this.showInterStore1 = show; }
    public void setShowInterStore2(boolean show) { this.showInterStore2 = show; }
    public void setShowInterGallery1(boolean show) { this.showInterGallery1 = show; }
    public void setShowInterGallery2(boolean show) { this.showInterGallery2 = show; }
    public void setShowInterSave1(boolean show) { this.showInterSave1 = show; }
    public void setShowInterSave2(boolean show) { this.showInterSave2 = show; }
    public void setShowInterHome1(boolean show) { this.showInterHome1 = show; }
    public void setShowInterHome2(boolean show) { this.showInterHome2 = show; }
    public void setShowInterShareBack(boolean show) { this.showInterShareBack = show; }
    public void setShowInterShareHome(boolean show) { this.showInterShareHome = show; }
    public void setCooldownSeconds(long seconds) { this.cooldownSeconds = seconds; }

    // =========================================================
    // GETTERS for placement flags
    // =========================================================

    public boolean isShowInterstitialAd() { return showInterstitialAd; }
    public boolean isShowInterOnb1() { return showInterOnb1; }
    public boolean isShowInterOnb2() { return showInterOnb2; }
    public boolean isShowInterMenu1() { return showInterMenu1; }
    public boolean isShowInterMenu2() { return showInterMenu2; }
    public boolean isShowInterList1() { return showInterList1; }
    public boolean isShowInterList2() { return showInterList2; }
    public boolean isShowInterPicture1() { return showInterPicture1; }
    public boolean isShowInterPicture2() { return showInterPicture2; }
    public boolean isShowInterStore1() { return showInterStore1; }
    public boolean isShowInterStore2() { return showInterStore2; }
    public boolean isShowInterGallery1() { return showInterGallery1; }
    public boolean isShowInterGallery2() { return showInterGallery2; }
    public boolean isShowInterSave1() { return showInterSave1; }
    public boolean isShowInterSave2() { return showInterSave2; }
    public boolean isShowInterHome1() { return showInterHome1; }
    public boolean isShowInterHome2() { return showInterHome2; }
    public boolean isShowInterShareBack() { return showInterShareBack; }
    public boolean isShowInterShareHome() { return showInterShareHome; }

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
        loadingAdsDialog.showWithTimeout(() -> adFinished.onAdFinished());

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
        loadingAdsDialog.showWithTimeout(() -> adFinished.onAdFinished());

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
