# Adore Ads

Full-featured Android ad monetization library with AdMob, mediation, auto-refresh, preload, fallback pool, and lifecycle management.

## What's new in 1.5.7

Custom Remote Config keys per placement. Apps integrating into existing codebases can now reuse their own Firebase Remote Config keys (e.g. `show_inter_save`, `inter_save_ad_id`) instead of being forced to rename them to the library's auto-derived `ad_{type}_{position}_enabled` / `ad_{type}_{position}_ads` pattern. Two new builder methods on `PlacementConfig`:

```java
new PlacementConfig.Builder()
    .setAdUnitIds(Arrays.asList("ca-app-pub-.../save_default"))
    .setRemoteEnabledKey("show_inter_save")          // existing app's RC key
    .setRemoteAdUnitsKey("inter_save_ad_id")         // existing app's RC key
    .setEnabled(true)
    .build();
```

The ad-units value is format-detected automatically — JSON array (starts with `[`) is parsed as the existing `RemoteAdUnit` priority list, anything else is split as comma- or whitespace-separated plain ad unit IDs. Empty string (default) keeps the auto-derived behavior, so this is fully backwards compatible.

## What's new in 1.5.6

A defensive hardening release — no new features, just bug fixes and AdMob policy compliance. App Open ads now enforce the 4-hour TTL, a cold-start gate, and a 30s session cooldown. The preload queue drops stale cached ads before serving, exponential backoff (`min(60s, 1000 * 2^attempt)`) replaces NO_FILL request storms, and `BannerAdManager.bindLifecycle(...)` wires `pause/resume/destroy` to a `LifecycleOwner` to prevent memory leaks and invalid impressions from offscreen banners.

## What's new in 1.5.5

- **Collapsible banners** — Per-placement TOP/BOTTOM anchored collapsible banners via `setCollapsibleAnchor(...)`. Standard banners unchanged.
- **Placement-aware Firebase Analytics (1.5.4)** — Every manager auto-fires a 13-event funnel (`ad_request`, `ad_load_success`, `ad_show`, `ad_impression`, `ad_click`, `ad_dismissed`, `ad_show_blocked`, `ad_cache_hit`, `ad_fallback_used`, etc.) tagged with `placement_key`, `ad_format`, `ad_unit_id`. Queryable in Firebase Console with no app changes.

## Features

- **Native Ads** — Parallel preload, auto-refresh, expiry, default pool fallback
- **Interstitial Ads** — Priority waterfall, configurable cooldown timer
- **Reward Ads** — Preload caching, auto-replenish after consumption, expiry check
- **Banner Ads** — Waterfall loading with default fallback, optional collapsible anchor (TOP/BOTTOM)
- **App Open Ads** — Resume ads with waterfall, per-activity control
- **Default Ad Pool** — Global fallback for all ad types when position-specific ads fail
- **6 Mediation Networks** — Facebook, AppLovin, Vungle, Mintegral, Pangle, Unity
- **UMP Consent** — Google User Messaging Platform for GDPR (configurable test devices)
- **In-App Billing** — Google Play Billing for premium check
- **Adjust Attribution** — Ad revenue and purchase tracking
- **Firebase Analytics & Crashlytics** — Automatic per-placement funnel events on every ad
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
implementation 'com.adoreapps.ai:ads:1.5.7'
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
