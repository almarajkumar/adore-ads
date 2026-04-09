package com.adoreapps.ai.ads;

import android.app.Application;
import android.util.Log;

import com.adoreapps.ai.ads.billing.PurchaseManager;
import com.adoreapps.ai.ads.consent.ConsentManager;
import com.adoreapps.ai.ads.core.AdsMobileAdsManager;
import com.adoreapps.ai.ads.core.AppOpenAdManager;
import com.adoreapps.ai.ads.manager.BannerAdManager;
import com.adoreapps.ai.ads.manager.DefaultAdPool;
import com.adoreapps.ai.ads.manager.InterstitialAdManager;
import com.adoreapps.ai.ads.manager.NativeAdManager;
import com.adoreapps.ai.ads.manager.RewardAdManager;

/**
 * Main entry point for the Adore Ads library.
 *
 * <h3>Quick Start:</h3>
 * <pre>
 * // In Application.onCreate()
 * AdoreAds.init(this, new AdoreAdsConfig.Builder(this)
 *     .setAdsEnabled(true)
 *     .setUseTestAdIds(BuildConfig.DEBUG)
 *     .addNativePlacement("NATIVE_HOME", new PlacementConfig(
 *         Arrays.asList("ca-app-pub-.../high", "ca-app-pub-.../low"),
 *         true, "home_view", "home_click"))
 *     .setDefaultNativeAdId("ca-app-pub-.../default")
 *     .setPurchaseProducts(purchaseModelList)
 *     .build());
 *
 * // In Activity/Fragment
 * AdoreAds.getInstance().nativeAds().showWithAutoRefresh(...);
 * AdoreAds.getInstance().interstitialAds().loadAndShow(...);
 * </pre>
 */
public final class AdoreAds {

    private static final String TAG = "AdoreAds";
    private static volatile AdoreAds instance;

    private Application application;
    private AdoreAdsConfig config;
    private boolean initialized = false;

    private AdoreAds() {}

    public static AdoreAds getInstance() {
        if (instance == null) {
            synchronized (AdoreAds.class) {
                if (instance == null) {
                    instance = new AdoreAds();
                }
            }
        }
        return instance;
    }

    /**
     * Initialize the Adore Ads library. Must be called from Application.onCreate().
     *
     * @param application The Application instance
     * @param config      Configuration built via {@link AdoreAdsConfig.Builder}
     */
    public static void init(Application application, AdoreAdsConfig config) {
        AdoreAds ads = getInstance();
        ads.application = application;
        ads.config = config;
        ads.initialized = true;

        Log.i(TAG, "Initializing Adore Ads v1.0.0"
                + " | adsEnabled=" + config.isAdsEnabled()
                + " | testIds=" + config.isUseTestAdIds()
                + " | nativePlacements=" + config.getNativePlacements().size()
                + " | interstitialPlacements=" + config.getInterstitialPlacements().size());

        // Initialize core ad SDK
        AdsMobileAdsManager coreManager = AdsMobileAdsManager.getInstance();
        coreManager.setUseTestAdIds(config.isUseTestAdIds());
        coreManager.setAdsEnabled(config.isAdsEnabled());
        coreManager.setShowLoadingDialog(config.isShowLoadingDialog());
        coreManager.init(application, () -> {
            Log.i(TAG, "AdMob SDK initialized");
        });

        // Configure consent test device hash if provided
        if (!config.getConsentTestDeviceHashedId().isEmpty()) {
            ConsentManager.getInstance(application).setTestDeviceHashedId(config.getConsentTestDeviceHashedId());
        }

        // Initialize billing / purchase manager
        if (!config.getPurchaseProducts().isEmpty()) {
            PurchaseManager.getInstance().init(application, config.getPurchaseProducts());
        }

        // Initialize all managers with placements from config
        NativeAdManager.getInstance().populateFromConfig(config);
        BannerAdManager.getInstance().populateFromConfig(config);
        InterstitialAdManager.getInstance().populateFromConfig(config);
        InterstitialAdManager.getInstance().setCooldownSeconds(config.getInterstitialCooldownSeconds());
        RewardAdManager.getInstance().populateFromConfig(config);
    }

    /**
     * Update configuration dynamically (e.g. after Firebase Remote Config fetch).
     * Call this to reconfigure placements, toggles, or ad unit IDs at runtime.
     */
    public void updateConfig(AdoreAdsConfig newConfig) {
        this.config = newConfig;
        AdsMobileAdsManager.getInstance().setUseTestAdIds(newConfig.isUseTestAdIds());
        AdsMobileAdsManager.getInstance().setAdsEnabled(newConfig.isAdsEnabled());
        NativeAdManager.getInstance().populateFromConfig(newConfig);
        BannerAdManager.getInstance().populateFromConfig(newConfig);
        InterstitialAdManager.getInstance().populateFromConfig(newConfig);
        InterstitialAdManager.getInstance().setCooldownSeconds(newConfig.getInterstitialCooldownSeconds());
        RewardAdManager.getInstance().populateFromConfig(newConfig);
        Log.i(TAG, "Config updated dynamically");
    }

    // =========================================================
    // RUNTIME PLACEMENT MANAGEMENT
    // =========================================================

    /**
     * Add a native ad placement at runtime without rebuilding the full config.
     */
    public void addNativePlacement(String key, PlacementConfig config) {
        NativeAdManager.getInstance().addPlacement(key, config);
    }

    /**
     * Add an interstitial ad placement at runtime.
     */
    public void addInterstitialPlacement(String key, PlacementConfig config) {
        InterstitialAdManager.getInstance().addPlacement(key, config);
    }

    /**
     * Add a reward ad placement at runtime.
     */
    public void addRewardPlacement(String key, PlacementConfig config) {
        RewardAdManager.getInstance().addPlacement(key, config);
    }

    /**
     * Add a banner ad placement at runtime.
     */
    public void addBannerPlacement(String key, PlacementConfig config) {
        BannerAdManager.getInstance().addPlacement(key, config);
    }

    /**
     * Remove any ad placement by key. Cleans up from all managers.
     */
    public void removePlacement(String key) {
        NativeAdManager.getInstance().removePlacement(key);
        InterstitialAdManager.getInstance().removePlacement(key);
        RewardAdManager.getInstance().removePlacement(key);
        BannerAdManager.getInstance().removePlacement(key);
    }

    // =========================================================
    // ACCESSORS
    // =========================================================

    public Application getApplication() { return application; }
    public AdoreAdsConfig getConfig() { return config; }
    public boolean isInitialized() { return initialized; }

    /** Check if user is premium (has active purchase). */
    public boolean isPremium() {
        return PurchaseManager.getInstance().isPurchased();
    }

    /** Core ad loading engine (low-level). Most apps use the managers instead. */
    public AdsMobileAdsManager core() { return AdsMobileAdsManager.getInstance(); }

    /** Native ad manager — preload, show with auto-refresh, cache, fallback pool. */
    public NativeAdManager nativeAds() { return NativeAdManager.getInstance(); }

    /** Interstitial ad manager — load-and-show with waterfall and cooldown. */
    public InterstitialAdManager interstitialAds() { return InterstitialAdManager.getInstance(); }

    /** Reward ad manager — preload, cache, and show reward ads. */
    public RewardAdManager rewardAds() { return RewardAdManager.getInstance(); }

    /** Banner ad manager — load-and-show with waterfall. */
    public BannerAdManager bannerAds() { return BannerAdManager.getInstance(); }

    /** App Open ad manager — resume ads with waterfall. */
    public AppOpenAdManager appOpenAds() { return AppOpenAdManager.getInstance(); }

    /** Consent manager (UMP). */
    public ConsentManager consent() { return ConsentManager.getInstance(application); }

    /** Default ad pool — global fallback for all ad types. */
    public DefaultAdPool defaultPool() { return DefaultAdPool.getInstance(); }

    /** Purchase manager — billing, premium status. */
    public PurchaseManager purchases() { return PurchaseManager.getInstance(); }
}
