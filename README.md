# Adore Ads

Full-featured Android ad monetization library with AdMob, mediation, auto-refresh, preload, fallback pool, and lifecycle management.

## Features

- **Native Ads** — Parallel preload, auto-refresh, expiry, default pool fallback
- **Interstitial Ads** — Priority waterfall, configurable cooldown timer
- **Reward Ads** — Preload caching, auto-replenish after consumption, expiry check
- **Banner Ads** — Waterfall loading with default fallback
- **App Open Ads** — Resume ads with waterfall, per-activity control
- **Default Ad Pool** — Global fallback for all ad types when position-specific ads fail
- **6 Mediation Networks** — Facebook, AppLovin, Vungle, Mintegral, Pangle, Unity
- **UMP Consent** — Google User Messaging Platform for GDPR (configurable test devices)
- **In-App Billing** — Google Play Billing for premium check
- **Adjust Attribution** — Ad revenue and purchase tracking
- **Firebase Analytics & Crashlytics** — Built-in event tracking
- **Firebase Remote Config** — Dynamic ad placements, toggles, and ad unit IDs from console
- **Centralized Constants** — All timing, expiry, and test IDs in `AdConstants`

## Installation

```groovy
// settings.gradle
maven {
    url = uri("https://maven.pkg.github.com/rajkumaralma/adore-ads")
    credentials { username = ...; password = ... }
}

// app/build.gradle
implementation 'com.adoreapps.ai:ads:1.2.0'
```

## Quick Start

```java
// Application.onCreate()
AdoreAds.init(this, new AdoreAdsConfig.Builder(this)
    .setAdsEnabled(true)
    .setUseTestAdIds(BuildConfig.DEBUG)
    .addNativePlacement("NATIVE_HOME", new PlacementConfig(
        Arrays.asList("high_floor_id", "low_floor_id"), true))
    .setDefaultNativeAdId("default_fallback_id")
    .setInterstitialCooldownSeconds(30)
    .setRemoteConfigEnabled(true)
    .setConsentTestDeviceHashedId("YOUR_HASHED_DEVICE_ID")
    .setPurchaseProducts(purchaseList)
    .build());

// Show ads via singleton managers
AdoreAds.getInstance().nativeAds().showWithAutoRefresh(...);
AdoreAds.getInstance().interstitialAds().loadAndShowInterstitialAd(...);
AdoreAds.getInstance().rewardAds().loadAndShowRewardAd(...);
AdoreAds.getInstance().bannerAds().loadAndShowBannerAd(...);

// Add placements at runtime (no config rebuild needed)
AdoreAds.getInstance().addNativePlacement("NATIVE_PROMO",
    new PlacementConfig(Arrays.asList("high_id", "low_id"), true));
AdoreAds.getInstance().interstitialAds().loadAndShowByPlacement(
    activity, "INTER_SAVE", callback);
```

## Documentation

- [Integration Guide](docs/INTEGRATION.md) — Step-by-step setup for new apps
- [Developer Documentation](docs/DEVELOPER.md) — Architecture, flows, API reference

## License

Proprietary — AdoreApps. Internal use only.
