# Adore Ads - Consumer ProGuard Rules
# These rules are automatically applied to any app that uses this library

# Keep public API
-keep class com.adoreapps.ai.ads.AdoreAds { *; }
-keep class com.adoreapps.ai.ads.AdoreAdsConfig { *; }
-keep class com.adoreapps.ai.ads.AdoreAdsConfig$Builder { *; }
-keep class com.adoreapps.ai.ads.PlacementConfig { *; }
-keep class com.adoreapps.ai.ads.AdCallback { *; }

# Keep interfaces
-keep interface com.adoreapps.ai.ads.interfaces.** { *; }

# Keep managers (public API)
-keep class com.adoreapps.ai.ads.manager.** { *; }
-keep class com.adoreapps.ai.ads.core.** { *; }
-keep class com.adoreapps.ai.ads.consent.** { *; }
-keep class com.adoreapps.ai.ads.billing.** { *; }

# Keep wrappers and models
-keep class com.adoreapps.ai.ads.wrapper.** { *; }
-keep class com.adoreapps.ai.ads.model.** { *; }
-keep class com.adoreapps.ai.ads.settings.** { *; }

# Keep event classes
-keep class com.adoreapps.ai.ads.event.** { *; }

# Mediation adapters
-keep class com.google.ads.mediation.** { *; }

# Adjust
-keep class com.adjust.sdk.** { *; }
-keep class com.google.android.gms.ads.identifier.** { *; }

# Facebook
-keep class com.facebook.ads.** { *; }
