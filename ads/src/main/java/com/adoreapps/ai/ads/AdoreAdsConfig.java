package com.adoreapps.ai.ads;

import android.content.Context;

import androidx.annotation.RawRes;

import com.adoreapps.ai.ads.model.PurchaseModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable configuration for the Adore Ads library.
 * Build via {@link Builder} and pass to {@link AdoreAds#init(android.app.Application, AdoreAdsConfig)}.
 *
 * <pre>
 * AdoreAdsConfig config = new AdoreAdsConfig.Builder(context)
 *     .setAdsEnabled(true)
 *     .setUseTestAdIds(BuildConfig.DEBUG)
 *     .addNativePlacement("NATIVE_HOME", new PlacementConfig(...))
 *     .setDefaultNativeAdId("ca-app-pub-.../default")
 *     .setInterstitialCooldownSeconds(30)
 *     .setNativeRefreshIntervalSeconds(20)
 *     .build();
 * </pre>
 */
public class AdoreAdsConfig {

    // General
    private final boolean adsEnabled;
    private final boolean useTestAdIds;
    private final boolean showLoadingDialog;
    private final @RawRes int loadingAnimationRes;

    // Placements
    private final Map<String, PlacementConfig> nativePlacements;
    private final Map<String, PlacementConfig> interstitialPlacements;
    private final Map<String, PlacementConfig> rewardPlacements;
    private final Map<String, PlacementConfig> bannerPlacements;

    // App Open
    private final List<String> appOpenAdIds;

    // Default fallback pool
    private final String defaultNativeAdId;
    private final String defaultInterstitialAdId;
    private final String defaultRewardAdId;
    private final String defaultBannerAdId;
    private final String defaultAppOpenAdId;
    private final boolean enableDefaultNativePool;
    private final boolean enableDefaultInterstitialPool;
    private final boolean enableDefaultRewardPool;
    private final boolean enableDefaultBannerFallback;
    private final boolean enableDefaultAppOpenFallback;

    // Timings
    private final long interstitialCooldownSeconds;
    private final long nativeRefreshIntervalSeconds;
    private final long adExpiryMinutes;

    // Billing
    private final List<PurchaseModel> purchaseProducts;

    // Consent
    private final String consentTestDeviceHashedId;

    // Adjust
    private final String adjustToken;
    private final String adjustEventAdImpression;
    private final String adjustEventPurchase;

    private AdoreAdsConfig(Builder builder) {
        this.adsEnabled = builder.adsEnabled;
        this.useTestAdIds = builder.useTestAdIds;
        this.showLoadingDialog = builder.showLoadingDialog;
        this.loadingAnimationRes = builder.loadingAnimationRes;
        this.nativePlacements = builder.nativePlacements;
        this.interstitialPlacements = builder.interstitialPlacements;
        this.rewardPlacements = builder.rewardPlacements;
        this.bannerPlacements = builder.bannerPlacements;
        this.appOpenAdIds = builder.appOpenAdIds;
        this.defaultNativeAdId = builder.defaultNativeAdId;
        this.defaultInterstitialAdId = builder.defaultInterstitialAdId;
        this.defaultRewardAdId = builder.defaultRewardAdId;
        this.defaultBannerAdId = builder.defaultBannerAdId;
        this.defaultAppOpenAdId = builder.defaultAppOpenAdId;
        this.enableDefaultNativePool = builder.enableDefaultNativePool;
        this.enableDefaultInterstitialPool = builder.enableDefaultInterstitialPool;
        this.enableDefaultRewardPool = builder.enableDefaultRewardPool;
        this.enableDefaultBannerFallback = builder.enableDefaultBannerFallback;
        this.enableDefaultAppOpenFallback = builder.enableDefaultAppOpenFallback;
        this.interstitialCooldownSeconds = builder.interstitialCooldownSeconds;
        this.nativeRefreshIntervalSeconds = builder.nativeRefreshIntervalSeconds;
        this.adExpiryMinutes = builder.adExpiryMinutes;
        this.purchaseProducts = builder.purchaseProducts;
        this.consentTestDeviceHashedId = builder.consentTestDeviceHashedId;
        this.adjustToken = builder.adjustToken;
        this.adjustEventAdImpression = builder.adjustEventAdImpression;
        this.adjustEventPurchase = builder.adjustEventPurchase;
    }

    // Getters
    public boolean isAdsEnabled() { return adsEnabled; }
    public boolean isUseTestAdIds() { return useTestAdIds; }
    public boolean isShowLoadingDialog() { return showLoadingDialog; }
    public @RawRes int getLoadingAnimationRes() { return loadingAnimationRes; }

    public Map<String, PlacementConfig> getNativePlacements() { return nativePlacements; }
    public Map<String, PlacementConfig> getInterstitialPlacements() { return interstitialPlacements; }
    public Map<String, PlacementConfig> getRewardPlacements() { return rewardPlacements; }
    public Map<String, PlacementConfig> getBannerPlacements() { return bannerPlacements; }

    public PlacementConfig getNativePlacement(String key) { return nativePlacements.get(key); }
    public PlacementConfig getInterstitialPlacement(String key) { return interstitialPlacements.get(key); }
    public PlacementConfig getRewardPlacement(String key) { return rewardPlacements.get(key); }
    public PlacementConfig getBannerPlacement(String key) { return bannerPlacements.get(key); }

    public List<String> getAppOpenAdIds() { return appOpenAdIds; }

    public String getDefaultNativeAdId() { return defaultNativeAdId; }
    public String getDefaultInterstitialAdId() { return defaultInterstitialAdId; }
    public String getDefaultRewardAdId() { return defaultRewardAdId; }
    public String getDefaultBannerAdId() { return defaultBannerAdId; }
    public String getDefaultAppOpenAdId() { return defaultAppOpenAdId; }
    public boolean isEnableDefaultNativePool() { return enableDefaultNativePool; }
    public boolean isEnableDefaultInterstitialPool() { return enableDefaultInterstitialPool; }
    public boolean isEnableDefaultRewardPool() { return enableDefaultRewardPool; }
    public boolean isEnableDefaultBannerFallback() { return enableDefaultBannerFallback; }
    public boolean isEnableDefaultAppOpenFallback() { return enableDefaultAppOpenFallback; }

    public long getInterstitialCooldownSeconds() { return interstitialCooldownSeconds; }
    public long getNativeRefreshIntervalSeconds() { return nativeRefreshIntervalSeconds; }
    public long getAdExpiryMinutes() { return adExpiryMinutes; }

    public List<PurchaseModel> getPurchaseProducts() { return purchaseProducts; }

    public String getConsentTestDeviceHashedId() { return consentTestDeviceHashedId; }

    public String getAdjustToken() { return adjustToken; }
    public String getAdjustEventAdImpression() { return adjustEventAdImpression; }
    public String getAdjustEventPurchase() { return adjustEventPurchase; }

    // =========================================================
    // BUILDER
    // =========================================================

    public static class Builder {
        private final Context context;

        private boolean adsEnabled = true;
        private boolean useTestAdIds = false;
        private boolean showLoadingDialog = true;
        private @RawRes int loadingAnimationRes = 0; // 0 = use bundled default

        private final Map<String, PlacementConfig> nativePlacements = new HashMap<>();
        private final Map<String, PlacementConfig> interstitialPlacements = new HashMap<>();
        private final Map<String, PlacementConfig> rewardPlacements = new HashMap<>();
        private final Map<String, PlacementConfig> bannerPlacements = new HashMap<>();

        private List<String> appOpenAdIds = new ArrayList<>();

        private String defaultNativeAdId = "";
        private String defaultInterstitialAdId = "";
        private String defaultRewardAdId = "";
        private String defaultBannerAdId = "";
        private String defaultAppOpenAdId = "";
        private boolean enableDefaultNativePool = true;
        private boolean enableDefaultInterstitialPool = true;
        private boolean enableDefaultRewardPool = true;
        private boolean enableDefaultBannerFallback = true;
        private boolean enableDefaultAppOpenFallback = true;

        private long interstitialCooldownSeconds = 30;
        private long nativeRefreshIntervalSeconds = 20;
        private long adExpiryMinutes = 55;

        private List<PurchaseModel> purchaseProducts = new ArrayList<>();

        private String consentTestDeviceHashedId = "";

        private String adjustToken = "";
        private String adjustEventAdImpression = "";
        private String adjustEventPurchase = "";

        public Builder(Context context) {
            this.context = context.getApplicationContext();
        }

        // General
        public Builder setAdsEnabled(boolean enabled) { this.adsEnabled = enabled; return this; }
        public Builder setUseTestAdIds(boolean useTest) { this.useTestAdIds = useTest; return this; }
        public Builder setShowLoadingDialog(boolean show) { this.showLoadingDialog = show; return this; }
        public Builder setLoadingAnimationRes(@RawRes int res) { this.loadingAnimationRes = res; return this; }

        // Placements
        public Builder addNativePlacement(String key, PlacementConfig config) {
            nativePlacements.put(key, config);
            return this;
        }
        public Builder addInterstitialPlacement(String key, PlacementConfig config) {
            interstitialPlacements.put(key, config);
            return this;
        }
        public Builder addRewardPlacement(String key, PlacementConfig config) {
            rewardPlacements.put(key, config);
            return this;
        }
        public Builder addBannerPlacement(String key, PlacementConfig config) {
            bannerPlacements.put(key, config);
            return this;
        }

        // App Open
        public Builder setAppOpenAdIds(List<String> ids) {
            this.appOpenAdIds = ids != null ? new ArrayList<>(ids) : new ArrayList<>();
            return this;
        }

        // Default fallback pool
        public Builder setDefaultNativeAdId(String id) { this.defaultNativeAdId = id; return this; }
        public Builder setDefaultInterstitialAdId(String id) { this.defaultInterstitialAdId = id; return this; }
        public Builder setDefaultRewardAdId(String id) { this.defaultRewardAdId = id; return this; }
        public Builder setDefaultBannerAdId(String id) { this.defaultBannerAdId = id; return this; }
        public Builder setDefaultAppOpenAdId(String id) { this.defaultAppOpenAdId = id; return this; }
        public Builder setEnableDefaultNativePool(boolean enable) { this.enableDefaultNativePool = enable; return this; }
        public Builder setEnableDefaultInterstitialPool(boolean enable) { this.enableDefaultInterstitialPool = enable; return this; }
        public Builder setEnableDefaultRewardPool(boolean enable) { this.enableDefaultRewardPool = enable; return this; }
        public Builder setEnableDefaultBannerFallback(boolean enable) { this.enableDefaultBannerFallback = enable; return this; }
        public Builder setEnableDefaultAppOpenFallback(boolean enable) { this.enableDefaultAppOpenFallback = enable; return this; }

        // Timings
        public Builder setInterstitialCooldownSeconds(long seconds) { this.interstitialCooldownSeconds = seconds; return this; }
        public Builder setNativeRefreshIntervalSeconds(long seconds) { this.nativeRefreshIntervalSeconds = seconds; return this; }
        public Builder setAdExpiryMinutes(long minutes) { this.adExpiryMinutes = minutes; return this; }

        // Billing
        public Builder setPurchaseProducts(List<PurchaseModel> products) {
            this.purchaseProducts = products != null ? new ArrayList<>(products) : new ArrayList<>();
            return this;
        }

        // Consent
        public Builder setConsentTestDeviceHashedId(String id) { this.consentTestDeviceHashedId = id; return this; }

        // Adjust
        public Builder setAdjustToken(String token) { this.adjustToken = token; return this; }
        public Builder setAdjustEventAdImpression(String event) { this.adjustEventAdImpression = event; return this; }
        public Builder setAdjustEventPurchase(String event) { this.adjustEventPurchase = event; return this; }

        public AdoreAdsConfig build() {
            return new AdoreAdsConfig(this);
        }
    }
}
