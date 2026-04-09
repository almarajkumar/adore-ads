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
│   ├── AdSettingsStore.java       <- Thread-safe SharedPreferences wrapper
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
│   └── PurchaseModel.java         <- productId + productType for billing
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
    ├── RemoteConfigManager.java   <- Firebase Remote Config wrapper
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

## Interstitial Placement Flags

`InterstitialAdManager` exposes placement flags as instance methods:

```java
InterstitialAdManager mgr = InterstitialAdManager.getInstance();

// Enable/disable global interstitial showing
mgr.setShowInterstitialAd(true);

// Per-placement control
mgr.setShowInterOnb1(false);
mgr.setShowInterMenu1(true);
mgr.setShowInterStore1(true);
mgr.setShowInterGallery1(true);
mgr.setShowInterSave1(true);
mgr.setShowInterHome1(true);
mgr.setShowInterShareBack(true);
// ... etc (see full list in InterstitialAdManager.java)

// Configure cooldown between shows
mgr.setCooldownSeconds(45);
```

## Reward Placement Flags

`RewardAdManager` exposes placement flags as instance methods:

```java
RewardAdManager mgr = RewardAdManager.getInstance();

mgr.setShowRewardStore1(false);
mgr.setShowRewardStore2(false);
mgr.setShowRewardSticker(true);
mgr.setShowRewardWtm(true);
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
| **AdMob** | play-services-ads | 24.9.0 |
| **Facebook** | audience-network-sdk | 6.21.0 |
| **AppLovin** | mediation:applovin | 13.5.1.0 |
| **Vungle** | mediation:vungle | 7.6.3.1 |
| **Mintegral** | mediation:mintegral | 17.0.61.1 |
| **Pangle** | mediation:pangle | 7.8.5.9.0 |
| **Unity** | unity-ads + mediation | 4.16.6 |
| **Firebase** | analytics, crashlytics, remote-config | BOM 34.8.0 |
| **UMP** | user-messaging-platform | 4.0.0 |
| **Billing** | billing | 8.0.0 |
| **Adjust** | adjust-android | 5.5.0 |
| **Shimmer** | shimmer | 0.5.0 |
| **Lottie** | lottie | 6.6.2 |
| **Gson** | gson | 2.11.0 |
