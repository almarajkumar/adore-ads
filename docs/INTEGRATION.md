# Adore Ads — Integration Guide

## Quick Start

### 1. Add Maven Repository

In your project's `settings.gradle`:

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()

        // Adore Ads private Maven
        maven {
            url = uri("https://maven.pkg.github.com/rajkumaralma/adore-ads")
            credentials {
                username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_USERNAME")
                password = project.findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
            }
        }

        // Required for mediation partners
        maven { url = uri("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea") }
        maven { url = uri("https://artifact.bytedance.com/repository/pangle/") }
    }
}
```

Add credentials to `~/.gradle/gradle.properties`:
```properties
gpr.user=your-github-username
gpr.key=ghp_your_personal_access_token
```

### 2. Add Dependency

In your app's `build.gradle`:

```groovy
dependencies {
    implementation 'com.adoreapps.ai:ads:1.2.0'
}
```

### 3. Initialize in Application

```java
public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        AdoreAdsConfig config = new AdoreAdsConfig.Builder(this)
            .setAdsEnabled(true)
            .setUseTestAdIds(BuildConfig.DEBUG)

            // Native ad placements
            .addNativePlacement("NATIVE_HOME", new PlacementConfig(
                Arrays.asList("ca-app-pub-.../high_floor", "ca-app-pub-.../low_floor"),
                true, "home_view", "home_click"))
            .addNativePlacement("NATIVE_SHARE", new PlacementConfig(
                Arrays.asList("ca-app-pub-.../share_1"),
                true))

            // Interstitial placements
            .addInterstitialPlacement("INTER_SAVE", new PlacementConfig(
                Arrays.asList("ca-app-pub-.../inter_1", "ca-app-pub-.../inter_2"),
                true))

            // Banner placements
            .addBannerPlacement("BANNER_SETTINGS", new PlacementConfig(
                Arrays.asList("ca-app-pub-.../banner_1"),
                true))

            // Reward placements
            .addRewardPlacement("REWARD_GENERATE", new PlacementConfig(
                Arrays.asList("ca-app-pub-.../reward_1", "ca-app-pub-.../reward_2"),
                true))

            // Default fallback pool
            .setDefaultNativeAdId("ca-app-pub-.../default_native")
            .setDefaultInterstitialAdId("ca-app-pub-.../default_inter")
            .setDefaultRewardAdId("ca-app-pub-.../default_reward")
            .setDefaultBannerAdId("ca-app-pub-.../default_banner")

            // App Open ads
            .setAppOpenAdIds(Arrays.asList("ca-app-pub-.../aoa_high", "ca-app-pub-.../aoa_low"))

            // Timing (defaults shown — override as needed)
            .setInterstitialCooldownSeconds(30)
            .setNativeRefreshIntervalSeconds(20)
            .setAdExpiryMinutes(55)

            // Remote Config (auto-fetch and apply ad settings from Firebase Console)
            .setRemoteConfigEnabled(true)
            .setRemoteConfigDefaultsResId(R.xml.remote_config_defaults)

            // Native auto-refresh (can also be toggled via remote config)
            .setNativeAutoRefreshEnabled(true)

            // Consent test device (for UMP debug — omit in production)
            .setConsentTestDeviceHashedId("YOUR_HASHED_DEVICE_ID")

            // Billing (for premium check)
            .setPurchaseProducts(Arrays.asList(
                new PurchaseModel("premium_monthly", PurchaseModel.ProductType.SUBS),
                new PurchaseModel("premium_yearly", PurchaseModel.ProductType.SUBS)
            ))

            // Adjust attribution (optional)
            .setAdjustToken("your_adjust_token")
            .setAdjustEventAdImpression("your_event_token")

            // Custom Lottie loading animation (optional)
            .setLoadingAnimationRes(R.raw.my_loading)

            .build();

        AdoreAds.init(this, config);
    }
}
```

### 4. Add Firebase config

Add `google-services.json` to your app module and apply the plugin:

```groovy
// app/build.gradle
plugins {
    id 'com.google.gms.google-services'
    id 'com.google.firebase.crashlytics'
}
```

---

## Usage

All ad managers are accessed as singletons, either directly or through `AdoreAds.getInstance()`:

```java
AdoreAds ads = AdoreAds.getInstance();
ads.nativeAds()        // NativeAdManager
ads.interstitialAds()  // InterstitialAdManager
ads.rewardAds()        // RewardAdManager
ads.bannerAds()        // BannerAdManager
ads.appOpenAds()       // AppOpenAdManager
ads.consent()          // ConsentManager
ads.purchases()        // PurchaseManager
ads.defaultPool()      // DefaultAdPool
```

### Native Ads

```java
// Show with auto-refresh (for long-lived screens)
AdoreAds.getInstance().nativeAds().showWithAutoRefresh(
    activity,
    findViewById(R.id.nativeAdContainer),   // FrameLayout
    findViewById(R.id.shimmerLayout),         // ShimmerFrameLayout
    R.layout.your_native_ad_layout,          // Your custom native ad layout
    "NATIVE_HOME",                           // Placement key from config
    "HomeFragment",                          // Unique tag for refresh tracking
    getViewLifecycleOwner()                  // Auto-stops on destroy
);

// One-off load (for dialogs, adapters)
AdoreAds.getInstance().nativeAds().loadAndShow(
    activity,
    frameLayout,
    shimmerLayout,
    R.layout.your_native_ad_layout,
    "NATIVE_HOME"
);

// Preload for later (e.g. during splash)
AdoreAds.getInstance().nativeAds().load(activity, "NATIVE_HOME", new AdCallback() {});

// Stop refresh manually (if not using LifecycleOwner)
AdoreAds.getInstance().nativeAds().stopAutoRefresh("HomeFragment");

// Batch preload multiple placements at once (v1.2.0)
AdoreAds.getInstance().nativeAds().preloadMultiple(activity,
    Arrays.asList("NATIVE_HOME", "NATIVE_SETTINGS", "NATIVE_EDIT"));

// Staggered preload (500ms between each — avoids request flooding)
AdoreAds.getInstance().nativeAds().preloadMultipleStaggered(activity,
    Arrays.asList("NATIVE_HOME", "NATIVE_SETTINGS"), 500);
```

### Interstitial Ads

```java
// Load and show with high/low floor waterfall
AdoreAds.getInstance().interstitialAds().loadAndShowInterstitialAd(
    activity,
    new AdFinished() {
        @Override
        public void onAdFinished() { navigateToNextScreen(); }
        @Override
        public void onAdFailed() { navigateToNextScreen(); }
    },
    "ca-app-pub-.../inter_high",  // high floor ID
    "ca-app-pub-.../inter_low",   // low floor ID
    true,                          // enable high
    true                           // enable low
);

// Load with priority ID list
ArrayList<String> ids = new ArrayList<>(Arrays.asList("id_1", "id_2", "id_3"));
AdoreAds.getInstance().interstitialAds().loadAdWithPriorityIds(
    activity, ids, true, adFinishedCallback
);

// Map-based placement flags (v1.2.0)
AdoreAds.getInstance().interstitialAds().setPlacementFlag("show_inter_home", true);
boolean enabled = AdoreAds.getInstance().interstitialAds().getPlacementFlag("show_inter_home", true);

// Cooldown persisted to SharedPreferences (survives process death)
AdoreAds.getInstance().interstitialAds().setCooldownSeconds(45);

// Configurable timeout via remote config (default 60s)
AdoreAds.getInstance().interstitialAds().setInterstitialTimeoutMs(30000);
```

### Reward Ads

```java
// Preload during screen init
AdoreAds.getInstance().rewardAds().preloadRewardAd(activity, true, true, "generate");

// Show when user taps "Watch Ad"
AdoreAds.getInstance().rewardAds().loadAndShowRewardAd(
    activity,
    new AdFinished() {
        @Override
        public void onAdFinished() { grantReward(); }
        @Override
        public void onAdFailed() { showToast("Ad not available"); }
    },
    true,        // enable high floor
    true,        // enable low floor
    "generate"   // placement name
);

// Convenience methods for specific placements
AdoreAds.getInstance().rewardAds().loadAndShowWatermarkRewardAd(activity, callback);
AdoreAds.getInstance().rewardAds().loadAndShowStickerRewardAd(activity, callback);
AdoreAds.getInstance().rewardAds().loadAndShowStoreRewardAd(activity, callback);
AdoreAds.getInstance().rewardAds().loadAndShowGenerateRewardAd(activity, callback);

// Reward placements are now fully config-driven (no hardcoded switch-case)
```

### Banner Ads

```java
// Default size (ADAPTIVE_LARGE)
AdoreAds.getInstance().bannerAds().loadAndShowBannerAd(
    activity, "BANNER_SETTINGS", bannerFrameLayout
);

// With specific size
AdoreAds.getInstance().bannerAds().loadAndShowBannerAd(
    activity, "BANNER_SETTINGS", bannerFrameLayout, BannerSize.MEDIUM_RECTANGLE
);

// Set per-placement size (applies to all subsequent loads)
AdoreAds.getInstance().bannerAds().setBannerSize("BANNER_HOME", BannerSize.LARGE_BANNER);

// Set global default
AdoreAds.getInstance().bannerAds().setDefaultBannerSize(BannerSize.ADAPTIVE);
```

**Available sizes:**

| Size | Dimensions | Use Case |
|------|------------|----------|
| `BANNER` | 320x50 | Standard banner |
| `LARGE_BANNER` | 320x100 | Slightly taller standard |
| `MEDIUM_RECTANGLE` | 300x250 | MREC / in-feed |
| `FULL_BANNER` | 468x60 | Tablet |
| `LEADERBOARD` | 728x90 | Tablet top/bottom |
| `ADAPTIVE` | Full-width, SDK-computed height | Modern standard |
| `ADAPTIVE_LARGE` | Full-width, larger height (SDK 25+) | Default |
| `INLINE_ADAPTIVE` | Flexible height for feeds | Scrollable content |

**Priority order when resolving size:**
1. Size passed to `loadAndShowBannerAd(..., size)` (explicit)
2. Per-placement size via `setBannerSize(placement, size)`
3. Global default via `setDefaultBannerSize(size)` or remote config `ad_banner_size`
4. Library default: `ADAPTIVE_LARGE`

**Remote config override:**
```
Key: ad_banner_size
Type: String
Values: BANNER | LARGE_BANNER | MEDIUM_RECTANGLE | FULL_BANNER | LEADERBOARD | ADAPTIVE | ADAPTIVE_LARGE | INLINE_ADAPTIVE
```

### App Open Ads

```java
// Init in Application (after AdoreAds.init)
AppOpenAdManager.getInstance().init(this, config.getAppOpenAdIds());
AppOpenAdManager.getInstance().disableLoadAtActivity(SplashActivity.class);

// Control resume behavior
AppOpenAdManager.getInstance().enableAppResume();
AppOpenAdManager.getInstance().disableAppResume();
```

### Consent (UMP)

```java
// Test device hash is set via config — no hardcoded values needed
ConsentManager.getInstance(activity).gatherConsent(activity, error -> {
    if (ConsentManager.getInstance(activity).canRequestAds()) {
        // Load ads
    }
});
```

### Premium Check

```java
// The library checks this automatically before showing any ad
AdoreAds.getInstance().isPremium();

// Launch purchase flow
AdoreAds.getInstance().purchases().launchPurchase(activity, "premium_monthly");
```

### Dynamic Config Update (After Remote Config Fetch)

```java
// Rebuild config with new values from Remote Config
AdoreAdsConfig updatedConfig = new AdoreAdsConfig.Builder(context)
    .setAdsEnabled(remoteConfig.getBoolean("enable_all_ads"))
    .addNativePlacement("NATIVE_HOME", new PlacementConfig(
        Arrays.asList(
            remoteConfig.getString("native_home_high"),
            remoteConfig.getString("native_home_low")
        ),
        remoteConfig.getBoolean("show_native_home"),
        "home_view", "home_click"))
    // ... other placements
    .build();

AdoreAds.getInstance().updateConfig(updatedConfig);
```

### Firebase Remote Config

The library integrates with Firebase Remote Config to dynamically control ad placements, toggles, ad unit IDs, and timing from the Firebase Console — no app update needed.

**Enable in config:**
```java
AdoreAdsConfig config = new AdoreAdsConfig.Builder(this)
    .setRemoteConfigEnabled(true)
    .setRemoteConfigDefaultsResId(R.xml.remote_config_defaults) // optional XML defaults
    .build();
```

**Fetch and apply in SplashActivity:**
```java
AdoreAds.getInstance().fetchRemoteConfig(success -> {
    // All adore_* keys are automatically applied to ad managers
    // App can also read its own custom keys:
    String promoText = AdoreAds.getInstance().remoteConfig().getString("promo_text", "");
    boolean showOnboarding = AdoreAds.getInstance().remoteConfig().getBoolean("show_onboarding", true);
    proceedToHome();
});
```

**Firebase Console key conventions:**

Key format: `ad_{type}_{position}_{suffix}` where `position` is the lowercase placement key without its type prefix (e.g. `NATIVE_HOME` → `home`).

**Global toggles:**

| Key | Type | Description |
|-----|------|-------------|
| `ad_enabled` | boolean | Master kill-switch for all ads |
| `ad_interstitial_cooldown` | long | Seconds between interstitial shows |
| `ad_interstitial_timeout_ms` | long | Interstitial loading dialog timeout (ms) |
| `ad_native_refresh_interval` | long | Native ad auto-refresh interval (seconds) |
| `ad_native_auto_refresh_enabled` | boolean | Enable/disable native auto-refresh |
| `ad_app_open_enabled` | boolean | Enable/disable app open ads globally |
| `ad_app_open_ads` | JSON | Override app open ad unit IDs |
| `ad_banner_size` | string | Default banner size (BANNER, ADAPTIVE, MEDIUM_RECTANGLE, etc.) |

**Per-placement toggles:**

| Key | Type | Description |
|-----|------|-------------|
| `ad_native_{position}_enabled` | boolean | Toggle a native placement |
| `ad_inter_{position}_enabled` | boolean | Toggle an interstitial placement |
| `ad_reward_{position}_enabled` | boolean | Toggle a reward placement |
| `ad_banner_{position}_enabled` | boolean | Toggle a banner placement |

**Per-placement ad unit overrides (JSON):**

| Key | Type | Description |
|-----|------|-------------|
| `ad_native_{position}_ads` | JSON | Override ad unit IDs for a native placement |
| `ad_inter_{position}_ads` | JSON | Override ad unit IDs for an interstitial placement |
| `ad_reward_{position}_ads` | JSON | Override ad unit IDs for a reward placement |
| `ad_banner_{position}_ads` | JSON | Override ad unit IDs for a banner placement |

**JSON format for ad unit overrides** (set as string value in Firebase Console):

```json
[
  { "ad_id": "ca-app-pub-.../high_floor", "priority": "high", "is_enabled": true },
  { "ad_id": "ca-app-pub-.../low_floor", "priority": "low", "is_enabled": true },
  { "ad_id": "ca-app-pub-.../test_floor", "priority": "medium", "is_enabled": false }
]
```

Priority order: `high` > `medium` > `low` > `default`. Only entries with `is_enabled: true` are used. This lets you A/B test individual floors without an app update.

**Configure Adjust event tokens (v1.2.0):**
```java
// After AdoreAds.init()
AdjustEvents.getInstance().setTokens("your_ad_impression_token", "your_purchase_token");
```

**Manual apply (if you fetch remote config yourself):**
```java
// After your own FirebaseRemoteConfig.fetchAndActivate()
AdoreAds.getInstance().applyRemoteConfig();
```

---

### Runtime Placement Management

Add, remove, or check placements at any time without rebuilding the full config:

```java
// Add a new native placement after app init (e.g. after a feature flag enables it)
AdoreAds.getInstance().addNativePlacement("NATIVE_PROMO",
    new PlacementConfig(
        Arrays.asList("ca-app-pub-.../promo_high", "ca-app-pub-.../promo_low"),
        true, "promo_view", "promo_click"));

// Add a new interstitial placement
AdoreAds.getInstance().addInterstitialPlacement("INTER_CHECKOUT",
    new PlacementConfig(
        Arrays.asList("ca-app-pub-.../checkout_1"),
        true));

// Add a new reward placement
AdoreAds.getInstance().addRewardPlacement("REWARD_BONUS",
    new PlacementConfig(
        Arrays.asList("ca-app-pub-.../reward_bonus"),
        true));

// Add a new banner placement
AdoreAds.getInstance().addBannerPlacement("BANNER_FOOTER",
    new PlacementConfig(
        Arrays.asList("ca-app-pub-.../footer_banner"),
        true));

// Remove a placement from all managers
AdoreAds.getInstance().removePlacement("NATIVE_PROMO");

// Load interstitial/reward by placement key
AdoreAds.getInstance().interstitialAds().loadAndShowByPlacement(
    activity, "INTER_CHECKOUT", adFinishedCallback);
AdoreAds.getInstance().rewardAds().loadAndShowByPlacement(
    activity, "REWARD_BONUS", adFinishedCallback);

// Check if a placement exists
AdoreAds.getInstance().nativeAds().hasPlacement("NATIVE_HOME");  // true/false
```

You can also add placements directly on individual managers:

```java
NativeAdManager.getInstance().addPlacement("KEY", placementConfig);
InterstitialAdManager.getInstance().addPlacement("KEY", placementConfig);
RewardAdManager.getInstance().addPlacement("KEY", placementConfig);
BannerAdManager.getInstance().addPlacement("KEY", placementConfig);
```

### Bundled Native Ad Layouts (v1.3.0)

The library includes 4 native ad layouts with matching shimmer placeholders:

```java
// Use via AdoreLayouts constants
AdoreLayouts.NATIVE_FULL_SCREEN   // Full-screen with countdown + skip
AdoreLayouts.NATIVE_LARGE         // Card with 16:9 media
AdoreLayouts.NATIVE_MEDIUM        // Card with 120dp media
AdoreLayouts.NATIVE_SMALL         // Horizontal row, no media

// Matching shimmers
AdoreLayouts.SHIMMER_FULL_SCREEN
AdoreLayouts.SHIMMER_LARGE
AdoreLayouts.SHIMMER_MEDIUM
AdoreLayouts.SHIMMER_SMALL
```

**Full-screen native with countdown:**
```java
FullScreenNativeAdActivity.show(activity, "NATIVE_SPLASH", 5, true);
```

**Overriding layouts:** Create a file with the same name in your app's `res/layout/` to override any bundled layout. Android resource merging gives your app's version priority. Keep all required view IDs (`ad_media`, `ad_app_icon`, `ad_headline`, `ad_body`, `ad_call_to_action`).

```
app/src/main/res/layout/adore_native_medium.xml   <-- overrides library version
```

Or use your own custom layout directly:
```java
nativeAds().showWithAutoRefresh(activity, container, shimmer,
    R.layout.my_custom_layout, "NATIVE_HOME", "tag", this);
```

---

## Ad Waterfall

Every ad request follows this fallback chain:

```
High Floor Ad Unit -> Low Floor Ad Unit -> Default Ad Pool -> Hide
```

Native ads use parallel preloading — both floors load simultaneously, the first response wins.

---

## ProGuard

Consumer rules are bundled automatically. No additional ProGuard config needed.

---

## Requirements

- **Min SDK:** 24
- **Compile SDK:** 35
- **Java:** 11
- **Firebase:** Must include `google-services.json`
- **AdMob:** Must register App ID in `AndroidManifest.xml`:
  ```xml
  <meta-data android:name="com.google.android.gms.ads.APPLICATION_ID"
             android:value="ca-app-pub-XXXXX~YYYYY" />
  ```
