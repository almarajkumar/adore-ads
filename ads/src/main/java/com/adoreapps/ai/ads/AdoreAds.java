package com.adoreapps.ai.ads;

import android.app.Application;
import android.util.Log;

import com.adoreapps.ai.ads.billing.PurchaseManager;
import com.adoreapps.ai.ads.consent.ConsentManager;
import com.adoreapps.ai.ads.core.AdsMobileAdsManager;
import com.adoreapps.ai.ads.core.AppOpenAdManager;
import com.adoreapps.ai.ads.event.AdjustEvents;
import com.adoreapps.ai.ads.event.FirebaseAnalyticsEvents;
import com.adoreapps.ai.ads.manager.BannerAdManager;
import com.adoreapps.ai.ads.manager.DefaultAdPool;
import com.adoreapps.ai.ads.manager.InterstitialAdManager;
import com.adoreapps.ai.ads.manager.NativeAdManager;
import com.adoreapps.ai.ads.manager.RewardAdManager;
import com.adoreapps.ai.ads.model.RemoteAdUnit;
import com.adoreapps.ai.ads.settings.AdConstants;
import com.adoreapps.ai.ads.utils.RemoteConfigManager;

import java.util.List;
import java.util.Map;

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
        boolean isDebugBuild = (application.getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;

        AdsMobileAdsManager coreManager = AdsMobileAdsManager.getInstance();
        // Test ad IDs only allowed in debug builds — silently ignored in release
        boolean useTestIds = config.isUseTestAdIds() && isDebugBuild;
        if (config.isUseTestAdIds() && !isDebugBuild) {
            Log.w(TAG, "setUseTestAdIds(true) ignored — not a debug build");
        }
        coreManager.setUseTestAdIds(useTestIds);
        coreManager.setAdsEnabled(config.isAdsEnabled());
        coreManager.setShowLoadingDialog(config.isShowLoadingDialog());
        coreManager.setFacebookEnabled(config.isFacebookEnabled());
        // Test device mode only in debug builds
        boolean testDeviceMode = config.isTestDeviceMode() && isDebugBuild;
        if (config.isTestDeviceMode() && !isDebugBuild) {
            Log.w(TAG, "setTestDeviceMode(true) ignored — not a debug build");
        }
        if (testDeviceMode || !config.getTestDeviceIds().isEmpty()) {
            coreManager.setTestDeviceIds(
                    config.getTestDeviceIds(),
                    testDeviceMode,
                    application
            );
        }
        coreManager.init(application, () -> {
            Log.i(TAG, "AdMob SDK initialized");
        });

        // Configure Facebook event logging
        FirebaseAnalyticsEvents.getInstance().setFacebookEnabled(config.isFacebookEnabled());

        // Configure Adjust (only enabled when tokens are provided)
        if (!config.getAdjustEventAdImpression().isEmpty() || !config.getAdjustEventPurchase().isEmpty()) {
            AdjustEvents.getInstance().setTokens(
                    config.getAdjustEventAdImpression(),
                    config.getAdjustEventPurchase()
            );
        }

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
        NativeAdManager.getInstance().setAutoRefreshEnabled(config.isNativeAutoRefreshEnabled());
        BannerAdManager.getInstance().populateFromConfig(config);
        InterstitialAdManager.getInstance().populateFromConfig(config);
        InterstitialAdManager.getInstance().setCooldownSeconds(config.getInterstitialCooldownSeconds());
        RewardAdManager.getInstance().populateFromConfig(config);

        // Start ad preloading if enabled
        if (config.isPreloadingEnabled()) {
            startPreloading(application, config);
        }

        // Auto-fetch remote config if enabled
        if (config.isRemoteConfigEnabled()) {
            ads.fetchRemoteConfig(null);
        }
    }

    /**
     * Start preloading for all registered interstitial and reward placements.
     */
    private static void startPreloading(Application application, AdoreAdsConfig config) {
        int bufferSize = config.getPreloadBufferSize();
        com.adoreapps.ai.ads.manager.AdPreloadManager preloader =
                com.adoreapps.ai.ads.manager.AdPreloadManager.getInstance();

        for (Map.Entry<String, PlacementConfig> entry : config.getInterstitialPlacements().entrySet()) {
            PlacementConfig pc = entry.getValue();
            if (pc.isEnabled() && !pc.getAdUnitIds().isEmpty()) {
                preloader.startInterstitial(application, entry.getKey(),
                        pc.getAdUnitIds().get(0), bufferSize);
            }
        }
        for (Map.Entry<String, PlacementConfig> entry : config.getRewardPlacements().entrySet()) {
            PlacementConfig pc = entry.getValue();
            if (pc.isEnabled() && !pc.getAdUnitIds().isEmpty()) {
                preloader.startReward(application, entry.getKey(),
                        pc.getAdUnitIds().get(0), bufferSize);
            }
        }
        Log.i(TAG, "Ad preloading started (native API: " + preloader.isNativeApiAvailable() + ")");
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
    // REMOTE CONFIG
    // =========================================================

    /**
     * Fetch Firebase Remote Config, apply ad-related keys, then invoke callback.
     * The library automatically reads keys prefixed with "adore_" and applies them
     * to the active managers.
     *
     * @param listener Called when fetch completes (success or failure).
     *                 On failure, cached/default values are still applied.
     */
    public void fetchRemoteConfig(RemoteConfigManager.OnRemoteConfigReadyListener listener) {
        RemoteConfigManager rcm = RemoteConfigManager.getInstance();
        if (config != null && config.getRemoteConfigDefaultsResId() != 0) {
            rcm.setDefaultsResourceId(config.getRemoteConfigDefaultsResId());
        }
        rcm.init(success -> {
            applyRemoteConfig();
            if (listener != null) listener.onReady(success);
        });
    }

    /**
     * Apply currently activated remote config values to all ad managers.
     * Called automatically after fetchRemoteConfig(), but can also be called
     * manually if the app activates remote config on its own.
     */
    public void applyRemoteConfig() {
        RemoteConfigManager rcm = RemoteConfigManager.getInstance();

        // --- Global toggles (ad_{setting}) ---
        boolean adsEnabled = rcm.getBoolean("ad_enabled", config != null && config.isAdsEnabled());
        AdsMobileAdsManager.getInstance().setAdsEnabled(adsEnabled);

        long cooldown = rcm.getLong("ad_interstitial_cooldown",
                config != null ? config.getInterstitialCooldownSeconds() : 30);
        InterstitialAdManager.getInstance().setCooldownSeconds(cooldown);

        long interTimeout = rcm.getLong("ad_interstitial_timeout_ms",
                AdConstants.DEFAULT_INTERSTITIAL_TIMEOUT_MS);
        InterstitialAdManager.getInstance().setInterstitialTimeoutMs(interTimeout);

        long refreshInterval = rcm.getLong("ad_native_refresh_interval",
                config != null ? config.getNativeRefreshIntervalSeconds() : 20);
        if (application != null) {
            com.adoreapps.ai.ads.settings.AdSettingsStore.getInstance(application)
                    .setLong("native_refresh_interval", refreshInterval);
        }

        boolean autoRefresh = rcm.getBoolean("ad_native_auto_refresh_enabled",
                config != null && config.isNativeAutoRefreshEnabled());
        NativeAdManager.getInstance().setAutoRefreshEnabled(autoRefresh);

        // Banner default size
        String bannerSizeStr = rcm.getString("ad_banner_size", "");
        if (!bannerSizeStr.isEmpty()) {
            BannerAdManager.getInstance().setDefaultBannerSize(
                    com.adoreapps.ai.ads.settings.BannerSize.fromString(bannerSizeStr));
        }

        // App open ad toggle + ad unit override
        boolean appOpenEnabled = rcm.getBoolean("ad_app_open_enabled", true);
        if (AppOpenAdManager.getInstance().isInitialized()) {
            if (appOpenEnabled) {
                AppOpenAdManager.getInstance().enableAppResume();
            } else {
                AppOpenAdManager.getInstance().disableAppResume();
            }

            // Override app open ad unit IDs from remote config
            String appOpenJson = rcm.getJson("ad_app_open_ads");
            if (appOpenJson != null && !appOpenJson.trim().isEmpty()) {
                List<String> remoteIds = RemoteAdUnit.toSortedAdIds(appOpenJson);
                if (!remoteIds.isEmpty()) {
                    AppOpenAdManager.getInstance().setAdIds(remoteIds);
                }
            }
        }

        // --- Per-placement overrides ---
        applyPlacementOverrides("native", config != null ? config.getNativePlacements() : null);
        applyPlacementOverrides("inter", config != null ? config.getInterstitialPlacements() : null);
        applyPlacementOverrides("reward", config != null ? config.getRewardPlacements() : null);
        applyPlacementOverrides("banner", config != null ? config.getBannerPlacements() : null);

        // Re-push updated placements to managers
        if (config != null) {
            NativeAdManager.getInstance().populateFromConfig(config);
            BannerAdManager.getInstance().populateFromConfig(config);
            InterstitialAdManager.getInstance().populateFromConfig(config);
            RewardAdManager.getInstance().populateFromConfig(config);
        }

        Log.i(TAG, "Remote config applied | adsEnabled=" + adsEnabled
                + " | cooldown=" + cooldown + "s | refresh=" + refreshInterval + "s"
                + " | autoRefresh=" + autoRefresh);
    }

    /**
     * Apply remote config overrides to a set of placements.
     * Key format: ad_{type}_{position_lowercase}_enabled / _ads
     *
     * Example: placement "NATIVE_HOME" with type "native"
     *   → ad_native_home_enabled
     *   → ad_native_home_ads
     */
    private void applyPlacementOverrides(String type, Map<String, PlacementConfig> placements) {
        if (placements == null) return;
        RemoteConfigManager rcm = RemoteConfigManager.getInstance();

        for (Map.Entry<String, PlacementConfig> entry : placements.entrySet()) {
            String key = entry.getKey();
            PlacementConfig pc = entry.getValue();

            // Convert "NATIVE_HOME" → "home", "INTER_SAVE" → "save", "REWARD_BONUS" → "bonus"
            String position = toShortPosition(key);

            // Enable/disable toggle
            String enabledKey = "ad_" + type + "_" + position + "_enabled";
            boolean enabled = rcm.getBoolean(enabledKey, pc.isEnabled());
            pc.setEnabled(enabled);

            // Ad unit ID override (JSON array)
            String adsKey = "ad_" + type + "_" + position + "_ads";
            String json = rcm.getJson(adsKey);
            if (json != null && !json.trim().isEmpty()) {
                List<String> remoteIds = RemoteAdUnit.toSortedAdIds(json);
                if (!remoteIds.isEmpty()) {
                    pc.setAdUnitIds(remoteIds);
                }
            }
        }
    }

    /**
     * Convert a placement key to a short lowercase position name for remote config keys.
     * Examples:
     *   "NATIVE_HOME"    → "home"
     *   "NATIVE_SPLASH"  → "splash"
     *   "INTER_SAVE"     → "save"
     *   "REWARD_BONUS"   → "bonus"
     *   "BANNER_HOME"    → "home"
     *   "MY_CUSTOM_KEY"  → "my_custom_key" (passthrough if no prefix match)
     */
    private String toShortPosition(String placementKey) {
        if (placementKey == null) return "";
        String lower = placementKey.toLowerCase();
        // Strip common prefixes: NATIVE_, INTER_, REWARD_, BANNER_
        for (String prefix : new String[]{"native_", "inter_", "reward_", "banner_"}) {
            if (lower.startsWith(prefix)) {
                return lower.substring(prefix.length());
            }
        }
        return lower;
    }

    /**
     * Access RemoteConfigManager for reading app-specific (non-ad) remote config values.
     */
    public RemoteConfigManager remoteConfig() {
        return RemoteConfigManager.getInstance();
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
