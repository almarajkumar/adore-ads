package com.adoreapps.ai.ads.manager;

import com.adoreapps.ai.ads.AdoreAdsConfig;
import com.adoreapps.ai.ads.PlacementConfig;
import com.adoreapps.ai.ads.settings.AdUnitsConfig;
import com.adoreapps.ai.ads.settings.AdConstants;
import com.adoreapps.ai.ads.settings.AdSettingsStore;
import com.adoreapps.ai.ads.settings.AdsConfig;
import com.adoreapps.ai.ads.settings.BannerSize;
import com.adoreapps.ai.ads.settings.CollapsibleAnchor;
import com.adoreapps.ai.ads.core.AdsMobileAdsManager;
import com.google.android.gms.ads.AdSize;

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
    private final Map<String, BannerSize> bannerSizeMap = new HashMap<>();
    private final Map<String, CollapsibleAnchor> collapsibleAnchorMap = new HashMap<>();
    private BannerSize defaultBannerSize = BannerSize.ADAPTIVE_LARGE;

    public static final String BANNER_SPLASH = "BANNER_SPLASH";
    public static final String BANNER_EDIT = "BANNER_EDIT";
    public static final String BANNER_SETTING = "BANNER_SETTING";

    public BannerAdManager() {

    }

    /**
     * Set the default banner size used for all placements that don't have
     * a per-placement size override.
     */
    public void setDefaultBannerSize(BannerSize size) {
        if (size != null) this.defaultBannerSize = size;
    }

    public BannerSize getDefaultBannerSize() {
        return defaultBannerSize;
    }

    /**
     * Set a banner size for a specific placement. Overrides the default size.
     */
    public void setBannerSize(String placement, BannerSize size) {
        if (placement != null && size != null) {
            bannerSizeMap.put(placement, size);
        }
    }

    public BannerSize getBannerSize(String placement) {
        BannerSize size = bannerSizeMap.get(placement);
        return size != null ? size : defaultBannerSize;
    }

    /**
     * Set the collapsible banner anchor for a placement. Pass
     * {@link CollapsibleAnchor#NONE} (or null) to disable.
     */
    public void setCollapsibleAnchor(String placement, CollapsibleAnchor anchor) {
        if (placement == null) return;
        if (anchor == null || anchor == CollapsibleAnchor.NONE) {
            collapsibleAnchorMap.remove(placement);
        } else {
            collapsibleAnchorMap.put(placement, anchor);
        }
    }

    public CollapsibleAnchor getCollapsibleAnchor(String placement) {
        CollapsibleAnchor a = collapsibleAnchorMap.get(placement);
        return a != null ? a : CollapsibleAnchor.NONE;
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
            setCollapsibleAnchor(key, config.getCollapsibleAnchor());
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
        collapsibleAnchorMap.clear();
        for (java.util.Map.Entry<String, PlacementConfig> entry : config.getBannerPlacements().entrySet()) {
            PlacementConfig pc = entry.getValue();
            if (pc.isEnabled() && !pc.getAdUnitIds().isEmpty()) {
                bannerAdMap.put(entry.getKey(), new AdUnitsConfig(
                        pc.getAdUnitIds(),
                        pc.getViewEventName(),
                        pc.getClickEventName()
                ));
                setCollapsibleAnchor(entry.getKey(), pc.getCollapsibleAnchor());
            }
        }
    }

    public void loadAndShowBannerAd(Activity activity, String placement, FrameLayout bannerAdView) {
        loadAndShowBannerAd(activity, placement, bannerAdView, getBannerSize(placement));
    }

    /**
     * Load and show a banner ad with a specific size, overriding the placement/default size.
     */
    public void loadAndShowBannerAd(Activity activity, String placement,
                                     FrameLayout bannerAdView, BannerSize size) {
        if (PurchaseManager.getInstance().isPurchased()) {
            com.adoreapps.ai.ads.event.FirebaseAnalyticsEvents.getInstance().logShowBlocked(
                    activity, placement, com.adoreapps.ai.ads.event.AdType.BANNER, "premium");
            bannerAdView.setVisibility(View.GONE);
            return;
        }
        if (!ConsentManager.getInstance(activity).canRequestAds()) {
            com.adoreapps.ai.ads.event.FirebaseAnalyticsEvents.getInstance().logShowBlocked(
                    activity, placement, com.adoreapps.ai.ads.event.AdType.BANNER, "no_consent");
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
            // Fire request event
            com.adoreapps.ai.ads.event.FirebaseAnalyticsEvents.getInstance().logRequest(activity,
                    placement, bannerAdIds.get(0), com.adoreapps.ai.ads.event.AdType.BANNER);

            BannerSize finalSize = size != null ? size : defaultBannerSize;
            AdSize adSize = finalSize.toAdSize(activity);
            final long loadStart = System.currentTimeMillis();
            final String firstId = bannerAdIds.get(0);
            CollapsibleAnchor anchor = getCollapsibleAnchor(placement);
            AdsMobileAdsManager.getInstance().loadAlternateBanner(
                    activity,
                    bannerAdIds,
                    bannerAdView,
                    adSize,
                    anchor.value()
            );
            // Note: AdsMobileAdsManager.loadAlternateBanner internally tracks success via
            // its own AdListener but doesn't expose load success/fail callbacks back here.
            // For full success/fail tracking, that path would need a callback param.
            // For now, request is tracked here; load_success/failed need core-level wiring.
        } else {
            com.adoreapps.ai.ads.event.FirebaseAnalyticsEvents.getInstance().logShowBlocked(activity,
                    placement, com.adoreapps.ai.ads.event.AdType.BANNER, "no_ad_units");
            bannerAdView.setVisibility(View.GONE);
        }
    }
}
