# Adore Ads — Developer Documentation

## Architecture

```
com.adoreapps.ai.ads/
├── AdoreAds.java                  <- Singleton entry point
├── AdoreAdsConfig.java            <- Builder config (placements, IDs, toggles, consent)
├── PlacementConfig.java           <- Per-placement: ad unit IDs + enabled flag
├── AdCallback.java                <- Base callback for all ad events (wrapper types only)
├── AdoreAppsAdsApplication.java   <- Legacy Application base (optional)
│
├── core/
│   ├── AdsMobileAdsManager.java   <- Core ad loading engine (AdMob + mediation)
│   ├── AppOpenAdManager.java      <- App resume ads with waterfall
│   ├── NetworkUtils.java          <- Network connectivity check
│   └── AudienceNetworkInitializeHelper.java
│
├── manager/
│   ├── NativeAdManager.java       <- Auto-refresh, parallel preload, cache, expiry, fallback
│   ├── InterstitialAdManager.java <- Singleton, cooldown timer, priority waterfall
│   ├── RewardAdManager.java       <- Singleton, caching, expiry, auto-replenish
│   ├── BannerAdManager.java       <- Singleton, waterfall + default fallback
│   └── DefaultAdPool.java         <- Global fallback pool (native, interstitial, reward)
│
├── consent/
│   └── ConsentManager.java        <- Google UMP consent (configurable test device)
│
├── billing/
│   └── PurchaseManager.java       <- Google Play Billing, premium check
│
├── event/
│   ├── FirebaseAnalyticsEvents.java <- Ad request/impression/click/revenue tracking
│   ├── AdjustEvents.java          <- Adjust attribution events
│   └── AdType.java                <- NATIVE, INTERSTITIAL, REWARDED, BANNER, APP_OPEN
│
├── settings/
│   ├── AdConstants.java           <- Centralized timing, expiry, and test ad ID constants
│   ├── AdSettingsStore.java       <- Thread-safe SharedPreferences wrapper (refresh interval updated by remote config)
│   ├── AdsConfig.java             <- Production ad unit IDs (immutable)
│   ├── AdUnitsConfig.java         <- List<adUnitIds> + analytics event names
│   └── AdStatus.java              <- IDLE, LOADING, LOADED, FAILED
│
├── wrapper/
│   ├── ApAdBase.java              <- Base with StatusAd tracking
│   ├── ApAdNative.java            <- Wraps NativeAd
│   ├── ApInterstitialAd.java      <- Wraps InterstitialAd
│   ├── ApRewardItem.java          <- Wraps RewardItem
│   ├── ApAdError.java             <- Wraps LoadAdError
│   ├── ApAdView.java              <- Wraps AdView
│   └── StatusAd.java              <- AD_INIT, AD_LOADING, AD_LOADED, AD_LOAD_FAIL
│
├── model/
│   ├── AdsResponse.java           <- Generic ad + unitID + priority wrapper
│   ├── PurchaseModel.java         <- productId + productType for billing
│   └── RemoteAdUnit.java          <- JSON model for remote config ad unit overrides
│
├── interfaces/
│   ├── AdFinished.java            <- onAdFinished(), onAdFailed()
│   ├── OpenAdsLoadCallback.java   <- onAdsLoaded(AppOpenAd), onFail(LoadAdError)
│   └── PurchaseCallback.java      <- purchaseSuccess(), purchaseFail()
│
├── dialog/
│   ├── LoadingAdsDialog.java      <- Fullscreen Lottie loading with configurable timeout
│   └── WelcomeBackDialog.java     <- App open resume dialog
│
└── utils/
    ├── RemoteConfigManager.java   <- Firebase Remote Config with typed getters + auto-apply
    └── SharePrefUtils.java        <- Persistent key-value storage
```

## Manager Access Pattern

All ad managers are singletons accessed via `AdoreAds.getInstance()`:

```java
AdoreAds ads = AdoreAds.getInstance();

ads.nativeAds()        // NativeAdManager.getInstance()
ads.interstitialAds()  // InterstitialAdManager.getInstance()
ads.rewardAds()        // RewardAdManager.getInstance()
ads.bannerAds()        // BannerAdManager.getInstance()
ads.appOpenAds()       // AppOpenAdManager.getInstance()
ads.consent()          // ConsentManager.getInstance(app)
ads.purchases()        // PurchaseManager.getInstance()
ads.defaultPool()      // DefaultAdPool.getInstance()
ads.core()             // AdsMobileAdsManager.getInstance()
```

## Remote Config Integration

The library auto-reads Firebase Remote Config keys prefixed with `adore_` and applies them to managers.

### Flow
```
1. AdoreAds.init() with remoteConfigEnabled=true
   '-- Auto-calls fetchRemoteConfig() after SDK init

2. fetchRemoteConfig(listener)
   |-- Sets XML defaults if configured
   |-- Calls FirebaseRemoteConfig.fetchAndActivate()
   '-- On complete: calls applyRemoteConfig()

3. applyRemoteConfig()
   |-- Reads global toggles: adore_ads_enabled, adore_interstitial_cooldown, etc.
   |-- For each registered placement:
   |   |-- Checks adore_{type}_{KEY}_enabled -> PlacementConfig.setEnabled()
   |   '-- Checks adore_{type}_{KEY}_ads -> JSON -> RemoteAdUnit.toSortedAdIds()
   |        -> PlacementConfig.setAdUnitIds()
   '-- Re-pushes updated config to all managers via populateFromConfig()
```

### RemoteAdUnit JSON Format
```json
[
  { "ad_id": "ca-app-pub-.../high", "priority": "high", "is_enabled": true },
  { "ad_id": "ca-app-pub-.../low", "priority": "low", "is_enabled": true }
]
```

`RemoteAdUnit.toSortedAdIds(json)` filters enabled entries, sorts by priority (high>medium>low>default), and returns ordered ID list.

### App Access to Custom Keys
```java
// After fetchRemoteConfig completes
AdoreAds.getInstance().remoteConfig().getString("my_custom_key", "default");
AdoreAds.getInstance().remoteConfig().getBoolean("feature_flag", false);
AdoreAds.getInstance().remoteConfig().getLong("timeout_ms", 5000);
AdoreAds.getInstance().remoteConfig().getRemoteConfig(); // raw FirebaseRemoteConfig
```

## AdConstants

All timing values, expiry durations, and test ad IDs are centralized in `AdConstants`:

| Constant | Value | Usage |
|----------|-------|-------|
| `AD_EXPIRY_MS` | 55 min | Ad cache invalidation (AdMob expires at ~60 min) |
| `MIN_REFRESH_INTERVAL_SECONDS` | 15s | Minimum native ad auto-refresh interval |
| `DEFAULT_REFRESH_INTERVAL_SECONDS` | 20s | Default native ad auto-refresh interval |
| `DEFAULT_INTERSTITIAL_COOLDOWN_SECONDS` | 30s | Cooldown between interstitial shows |
| `LOADING_DIALOG_TIMEOUT_MS` | 15s | Loading dialog auto-dismiss timeout |
| `TEST_BANNER_AD_ID` | Google test | Used when `useTestAdIds = true` |
| `TEST_INTERSTITIAL_AD_ID` | Google test | Used when `useTestAdIds = true` |
| `TEST_NATIVE_AD_ID` | Google test | Used when `useTestAdIds = true` |
| `TEST_REWARD_AD_ID` | Google test | Used when `useTestAdIds = true` |
| `TEST_APP_OPEN_AD_ID` | Google test | Used when `useTestAdIds = true` |

## AdCallback

`AdCallback` uses wrapper types exclusively. All methods receive library wrapper types (`ApAdError`, `ApAdNative`, `ApInterstitialAd`, `ApRewardItem`, `ApAdView`) rather than raw Google SDK types.

```java
new AdCallback() {
    @Override public void onNativeAds(ApAdNative nativeAd, String unitID) { }
    @Override public void onResultInterstitialAd(ApInterstitialAd ad) { }
    @Override public void onUserEarnedReward(ApRewardItem item) { }
    @Override public void onAdFailedToLoad(@NonNull ApAdError error) { }
    @Override public void onAdFailedToShowFullScreenContent(@NonNull ApAdError error) { }
    @Override public void onAdLoaded() { }
    @Override public void onAdLoaded(ApAdView adView) { }
    @Override public void onNextScreen() { }
    @Override public void onAdShowedFullScreenContent() { }
    @Override public void onAdClicked() { }
    @Override public void onAdImpression() { }
    @Override public void onAdDismissedFullScreenContent() { }
    @Override public void onAdClosed() { }
}
```

## Key Flows

### Native Ad Lifecycle
```
1. populateFromConfig(AdoreAdsConfig) -- maps placement keys to ad unit IDs
2. load(activity, "NATIVE_HOME", callback) -- parallel preload (high + low floor)
3. showWithAutoRefresh(activity, ..., "NATIVE_HOME", "HomeFragment", lifecycleOwner)
   |-- Check cache -> show instantly if valid + not expired (AD_EXPIRY_MS)
   |-- Cache miss -> loadAlternateNative(high -> low sequential)
   |   '-- onAdFailedToLoad -> DefaultAdPool.consumeDefaultNativeAd()
   '-- Register auto-refresh (every N seconds, swap-on-success)
4. stopAutoRefresh("HomeFragment") -- auto via LifecycleOwner or manual in onDestroy
```

### Interstitial Lifecycle
```
1. InterstitialAdManager.getInstance().loadAndShowInterstitialAd(...)
   |-- Check: premium? consent? enabled? cooldown?
   |-- Show LoadingAdsDialog with LOADING_DIALOG_TIMEOUT_MS timeout
   |-- loadAlternateInterstitialAds(ids) -- recursive waterfall
   |   '-- All fail -> DefaultAdPool.consumeDefaultInterstitialAd()
   '-- show(activity, interstitialAd, showNextScreen)
        '-- After show: cooldown active, re-enables after cooldownSeconds
```

### Reward Ad Lifecycle
```
1. RewardAdManager.getInstance().preloadRewardAd(...) -- cache for later
2. RewardAdManager.getInstance().loadAndShowRewardAd(...)
   |-- Check cached ad (not expired, matching placement)
   |   |-- YES -> show instantly, preload next in background
   |   '-- NO -> loadAlternateRewardAd(ids) -> waterfall
   |        '-- All fail -> DefaultAdPool.consumeDefaultRewardAd()
   '-- onAdDismissed -> check isUserEarnedReward -> callback
```

### Default Ad Pool
```
init(activity) -- preloads one ad per type (native, interstitial, reward)
consumeDefaultNativeAd(activity) -- returns cached ad, clears pool, triggers re-preload
AD_EXPIRY_MS expiry on all cached ads
Premium + consent + network validated before serving
```

## Interstitial Placement Flags (v1.2.0 — Map-Based)

Placement flags are stored in a `ConcurrentHashMap` for easy remote config control:

```java
InterstitialAdManager mgr = InterstitialAdManager.getInstance();

// Set any flag by key (from remote config, settings, etc.)
mgr.setPlacementFlag("show_inter_home", true);
mgr.setPlacementFlag("show_inter_onb_1", false);

// Read with default value
boolean enabled = mgr.getPlacementFlag("show_inter_home", true);

// Cooldown persisted to SharedPreferences (survives process death)
mgr.setCooldownSeconds(45);

// Configurable loading dialog timeout (default 60s, override via remote config)
mgr.setInterstitialTimeoutMs(30000);
```

## Reward Placements (v1.2.0 — Config-Driven)

Reward ad ID mapping is now fully driven by `populateFromConfig()` and `addPlacement()`.
No more hardcoded switch-case for "watermark", "sticker", "store" etc.

```java
// Register via config builder
.addRewardPlacement("REWARD_BONUS", new PlacementConfig(
    Arrays.asList("high_id", "low_id"), true))

// Or add at runtime
AdoreAds.getInstance().addRewardPlacement("REWARD_UNLOCK", config);

// Load and show by key
AdoreAds.getInstance().rewardAds().loadAndShowByPlacement(activity, "REWARD_BONUS", callback);
```

## Batch Preloading (v1.2.0)

Preload multiple native ad placements in one call:

```java
// Preload all at once
AdoreAds.getInstance().nativeAds().preloadMultiple(activity,
    Arrays.asList("NATIVE_HOME", "NATIVE_SETTINGS", "NATIVE_EDIT"));

// Staggered (500ms between each — avoids request flooding)
AdoreAds.getInstance().nativeAds().preloadMultipleStaggered(activity,
    Arrays.asList("NATIVE_HOME", "NATIVE_SETTINGS"), 500);
```

## Runtime Placement Management

All managers support adding/removing placements at runtime without rebuilding the full config.

### API Surface

| Method | Available On | Description |
|--------|-------------|-------------|
| `addPlacement(key, PlacementConfig)` | All 4 managers + `AdoreAds` facade | Add or replace a single placement |
| `removePlacement(key)` | All 4 managers + `AdoreAds` facade | Remove a placement (+ cleanup) |
| `hasPlacement(key)` | All 4 managers | Check if a placement is registered |
| `loadAndShowByPlacement(activity, key, callback)` | Interstitial, Reward | Load and show by registered placement key |
| `populateFromConfig(AdoreAdsConfig)` | All 4 managers | Bulk-replace all placements from config |

### AdoreAds Facade (Convenience)

```java
// These delegate to the appropriate manager
AdoreAds.getInstance().addNativePlacement(key, config);
AdoreAds.getInstance().addInterstitialPlacement(key, config);
AdoreAds.getInstance().addRewardPlacement(key, config);
AdoreAds.getInstance().addBannerPlacement(key, config);
AdoreAds.getInstance().removePlacement(key);  // removes from ALL managers
```

### Placement-Based Loading

Interstitial and Reward managers support loading by placement key:

```java
// Register once (at init or dynamically)
InterstitialAdManager.getInstance().addPlacement("INTER_CHECKOUT",
    new PlacementConfig(Arrays.asList("high_id", "low_id"), true));

// Load and show by key (anywhere in the app)
InterstitialAdManager.getInstance().loadAndShowByPlacement(
    activity, "INTER_CHECKOUT", adFinishedCallback);
```

### NativeAdManager Cleanup

`removePlacement()` on NativeAdManager also:
- Destroys the cached native ad (`NativeAd.destroy()`)
- Removes expiry tracking
- Stops any active auto-refresh for that placement

## Configuration

### Builder-based (recommended)

```java
AdoreAdsConfig config = new AdoreAdsConfig.Builder(context)
    .setAdsEnabled(true)
    .setUseTestAdIds(BuildConfig.DEBUG)
    .addNativePlacement("KEY", new PlacementConfig(ids, enabled))
    .addInterstitialPlacement("KEY", new PlacementConfig(ids, enabled))
    .addRewardPlacement("KEY", new PlacementConfig(ids, enabled))
    .addBannerPlacement("KEY", new PlacementConfig(ids, enabled))
    .setDefaultNativeAdId("ca-app-pub-.../default")
    .setInterstitialCooldownSeconds(30)
    .setNativeRefreshIntervalSeconds(20)
    .setAdExpiryMinutes(55)
    .setConsentTestDeviceHashedId("HASHED_ID")  // UMP debug
    .setPurchaseProducts(purchaseList)
    .setAdjustToken("token")
    .build();

AdoreAds.init(app, config);
```

### Legacy populateAdUnitMap (backward compatible)

```java
NativeAdManager.getInstance().populateAdUnitMap(activity);
BannerAdManager.getInstance().populateAdUnitMap(activity);
// Uses AdsConfig final fields + AdSettingsStore booleans
```

### Dynamic Updates

```java
// After Remote Config fetch
AdoreAds.getInstance().updateConfig(newConfig);
// Repopulates NativeAdManager, BannerAdManager, and interstitial cooldown
```

## Performance Optimizations (v1.2.0)

| Optimization | Impact |
|---|---|
| **RemoteConfig activate-first** | Instant cached values (~0ms vs 10-30s blocking) |
| **Parallel mediation init** (3-thread pool) | 3-8s faster SDK startup |
| **Shared native pool** (non-consuming `getDefaultNativeAd()`) | Same fallback ad reused across failed placements |
| **DefaultAdPool depth-2 + retry backoff** | 30s/60s/120s retry; 2 ads cached per type |
| **10s load timeout** on all ad loads | Prevents stuck placements (AtomicBoolean guard) |
| **Auto-refresh stagger** (random 0-5s offset) | Prevents thundering herd |
| **Ad expiry checked at show time** | No dead ads shown |
| **Persisted interstitial cooldown** (SharedPreferences) | Survives process death |
| **Fill-rate analytics** | 4 events: request_sent, filled, show_success, show_failed |
| **Configurable interstitial timeout** | Via remote config key `adore_interstitial_timeout_ms` |

## Thread Safety

All singleton managers use double-checked locking with `volatile`:

```java
private static volatile InterstitialAdManager instance;

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
```

- `AdSettingsStore` — Synchronized singleton, safe for concurrent access
- `ConsentManager` — Synchronized singleton with configurable test device hash
- `NativeAdManager` — Uses `ConcurrentHashMap` for thread-safe caching
- `DefaultAdPool` — Uses `volatile` fields for cross-thread visibility
- `InterstitialAdManager` — Cooldown flag is `volatile` for safe reads from main/background threads
- `AdsConfig` — All fields are `public static final` (immutable)

## Publishing

### Build AAR
```bash
./gradlew :ads:assembleRelease
# Output: ads/build/outputs/aar/ads-release.aar
```

### Publish to GitHub Packages
```bash
export GITHUB_USERNAME=your-username
export GITHUB_TOKEN=ghp_xxx  # scope: write:packages
export GITHUB_OWNER=adoreapps  # GitHub org or user
./gradlew :ads:publishReleasePublicationToGitHubPackagesRepository
```

### Version Bump
Edit `ads/build.gradle`:
```groovy
version = '1.1.0'  // Change here
```

## Dependencies Included

| Category | Package | Version |
|----------|---------|---------|
| **AdMob** | play-services-ads | 25.1.0 |
| **Facebook** | audience-network-sdk | 6.21.0 |
| **AppLovin** | mediation:applovin | 13.6.2.0 |
| **Vungle** | mediation:vungle | 7.7.2.0 |
| **Mintegral** | mediation:mintegral | 17.1.31.0 |
| **Pangle** | mediation:pangle | 7.9.1.3.0 |
| **Unity** | unity-ads + mediation | 4.17.0 |
| **Firebase** | analytics, crashlytics, remote-config | BOM 34.11.0 |
| **UMP** | user-messaging-platform | 4.0.0 |
| **Billing** | billing | 8.3.0 |
| **Adjust** | adjust-android | 5.6.0 |
| **Shimmer** | shimmer | 0.5.0 |
| **Lottie** | lottie | 6.7.1 |
| **Gson** | gson | 2.13.2 |
| **Lifecycle** | lifecycle-process | 2.10.0 |
| **Facebook SDK** | facebook-android-sdk | 18.2.3 |
