package com.adoreapps.ai.ads.manager;

import com.adoreapps.ai.ads.AdoreAdsConfig;
import com.adoreapps.ai.ads.PlacementConfig;
import com.adoreapps.ai.ads.settings.AdUnitsConfig;
import com.adoreapps.ai.ads.settings.AdConstants;
import com.adoreapps.ai.ads.settings.AdSettingsStore;
import com.adoreapps.ai.ads.settings.AdsConfig;
import com.adoreapps.ai.ads.core.AdsMobileAdsManager;

import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;

import com.adoreapps.ai.ads.core.AdsMobileAdsManager;

import com.adoreapps.ai.ads.consent.ConsentManager;
import com.adoreapps.ai.ads.billing.PurchaseManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BannerAdManager {

    private final Map<String, AdUnitsConfig> bannerAdMap = new HashMap<>();
    public static final String BANNER_SPLASH = "BANNER_SPLASH";
    public static final String BANNER_EDIT = "BANNER_EDIT";
    public static final String BANNER_SETTING = "BANNER_SETTING";

    public BannerAdManager() {

    }

    private static volatile BannerAdManager instance;
    public static synchronized BannerAdManager getInstance() {
        if (instance == null) {
            instance = new BannerAdManager();
        }
        return instance;
    }
    // =========================================================
    // RUNTIME PLACEMENT MANAGEMENT
    // =========================================================

    /**
     * Add or replace a single banner ad placement at runtime.
     */
    public void addPlacement(String key, PlacementConfig config) {
        if (key == null || config == null) return;
        if (config.isEnabled() && !config.getAdUnitIds().isEmpty()) {
            bannerAdMap.put(key, new AdUnitsConfig(
                    config.getAdUnitIds(),
                    config.getViewEventName(),
                    config.getClickEventName()
            ));
        }
    }

    /**
     * Remove a banner ad placement.
     */
    public void removePlacement(String key) {
        if (key != null) bannerAdMap.remove(key);
    }

    /**
     * Check if a placement is registered.
     */
    public boolean hasPlacement(String key) {
        return bannerAdMap.containsKey(key);
    }

    // =========================================================
    // LEGACY POPULATE (from AdSettingsStore)
    // =========================================================

    public void populateAdUnitMap(Activity activity) {
        AdSettingsStore settings = AdSettingsStore.getInstance(activity);

        // SPLASH
        List<String> splashAds = new ArrayList<>();

        if (!settings.getBoolean("splash_banner_change")) {
            if (settings.getBoolean("show_101_spl_a_banner_high_new"))
                splashAds.add(AdsConfig.banner_splash_1);
            if (settings.getBoolean("show_101_spl_a_banner_new"))
                splashAds.add(AdsConfig.banner_splash_2);
        }

        bannerAdMap.put(BANNER_SPLASH, new AdUnitsConfig(splashAds, "splash_ad_banner_view", "splash_ad_banner_click"));

        List<String> editAds = new ArrayList<>();
            if (settings.getBoolean("show_701_banner_edit"))
                editAds.add(AdsConfig.banner_edit_701);

        bannerAdMap.put(BANNER_EDIT, new AdUnitsConfig(editAds, "edit_ad_banner_view", "edit_ad_banner_click"));


        List<String> settingAds = new ArrayList<>();
        if (settings.getBoolean("show_406_banner_setting"))
            settingAds.add(AdsConfig.banner_setting_406);

        bannerAdMap.put(BANNER_SETTING, new AdUnitsConfig(settingAds, "setting_ad_banner_view", "setting_ad_banner_click"));


    }

    /**
     * Populate banner placements from AdoreAdsConfig (builder-based).
     */
    public void populateFromConfig(AdoreAdsConfig config) {
        bannerAdMap.clear();
        for (java.util.Map.Entry<String, PlacementConfig> entry : config.getBannerPlacements().entrySet()) {
            PlacementConfig pc = entry.getValue();
            if (pc.isEnabled() && !pc.getAdUnitIds().isEmpty()) {
                bannerAdMap.put(entry.getKey(), new AdUnitsConfig(
                        pc.getAdUnitIds(),
                        pc.getViewEventName(),
                        pc.getClickEventName()
                ));
            }
        }
    }

    public  void loadAndShowBannerAd(
            Activity activity,
            String placement,
            FrameLayout bannerAdView
    ) {
        if(PurchaseManager.getInstance().isPurchased()) {
            bannerAdView.setVisibility(View.GONE);
            return;
        }
        if(!ConsentManager.getInstance(activity).canRequestAds()) {
            bannerAdView.setVisibility(View.GONE);
            return;
        }
        AdUnitsConfig bannerAdConfig = bannerAdMap.get(placement);
        List<String> bannerAdIds = new ArrayList<>();
        if (bannerAdConfig != null) {
            bannerAdIds.addAll(bannerAdConfig.adUnitIds);
        }
        // Append default banner as last fallback if enabled
        if (AdSettingsStore.getInstance(activity).getBoolean("fallback_default_banner", true)) {
            String defaultId = AdsMobileAdsManager.getInstance().isUseTestAdIds()
                    ? AdConstants.TEST_BANNER_AD_ID : AdsConfig.bannerDefaultId;
            if (!bannerAdIds.contains(defaultId)) {
                bannerAdIds.add(defaultId);
            }
        }
        if (!bannerAdIds.isEmpty()) {
            AdsMobileAdsManager.getInstance().loadAlternateBanner(
                    activity,
                    bannerAdIds,
                    bannerAdView
            );
        } else {
            bannerAdView.setVisibility(View.GONE);
        }

    }
}
