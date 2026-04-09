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
    implementation 'com.adoreapps.ai:ads:1.1.0'
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

// Control placement flags
AdoreAds.getInstance().interstitialAds().setShowInterstitialAd(true);
AdoreAds.getInstance().interstitialAds().setCooldownSeconds(45);
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

// Control placement flags
AdoreAds.getInstance().rewardAds().setShowRewardSticker(false);
```

### Banner Ads

```java
AdoreAds.getInstance().bannerAds().loadAndShowBannerAd(
    activity, "BANNER_SETTINGS", bannerFrameLayout
);
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
