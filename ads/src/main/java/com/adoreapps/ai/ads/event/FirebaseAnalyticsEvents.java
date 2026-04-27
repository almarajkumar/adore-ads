package com.adoreapps.ai.ads.event;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.adoreapps.ai.ads.core.AdsMobileAdsManager;
import com.adoreapps.ai.ads.core.NetworkUtils;
import com.applovin.mediation.MaxAd;
import com.facebook.appevents.AppEventsConstants;
import com.facebook.appevents.AppEventsLogger;
import com.google.android.gms.ads.AdValue;
import com.google.android.gms.ads.ResponseInfo;
import com.google.firebase.analytics.FirebaseAnalytics;

public class FirebaseAnalyticsEvents {
    private static FirebaseAnalyticsEvents instance;
    private AppEventsLogger appEventsLogger;
    private boolean facebookEnabled = true;

    public static final String AD_PLATFORM_ADMOB = "admob_sdk";
    Context context;

    private FirebaseAnalyticsEvents() {
    }

    public static synchronized FirebaseAnalyticsEvents getInstance() {
        if (instance == null) {
            instance = new FirebaseAnalyticsEvents();
        }
        return instance;
    }

    public void init(Context context) {
        this.context = context;
        if (facebookEnabled) {
            try {
                appEventsLogger = AppEventsLogger.newLogger(context);
            } catch (Exception e) {
                Log.w("FirebaseAnalyticsEvents", "Facebook SDK init failed: " + e.getMessage());
                appEventsLogger = null;
            }
        }
    }

    /**
     * Enable or disable Facebook event logging.
     * When disabled, only Firebase Analytics events are logged.
     */
    public void setFacebookEnabled(boolean enabled) {
        this.facebookEnabled = enabled;
        if (!enabled) {
            appEventsLogger = null;
        } else if (context != null && appEventsLogger == null) {
            try {
                appEventsLogger = AppEventsLogger.newLogger(context);
            } catch (Exception ignored) {}
        }
    }

    public boolean isFacebookEnabled() {
        return facebookEnabled;
    }

    private void logFbEvent(String eventName, Bundle params) {
        if (appEventsLogger != null) {
            appEventsLogger.logEvent(eventName, params);
        }
    }

    private void logFbEvent(String eventName, double value, Bundle params) {
        if (appEventsLogger != null) {
            appEventsLogger.logEvent(eventName, value, params);
        }
    }

    private void logFbEvent(String eventName) {
        if (appEventsLogger != null) {
            appEventsLogger.logEvent(eventName);
        }
    }

    public void logEvent(String eventName, String screenName) {
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName);
        Log.i("picshiner_analytics_event", "Event: " + eventName);
        FirebaseAnalytics.getInstance(context).logEvent(eventName, bundle);
    }

    public void logEvent(String eventName) {
        FirebaseAnalytics.getInstance(context).logEvent(eventName, null);
        logFbEvent(eventName);
    }

    public void logAdEvent(String event, String adType, String adUnitId) {
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.AD_FORMAT, adType);
        bundle.putString(FirebaseAnalytics.Param.AD_SOURCE, "admob");
        bundle.putString(FirebaseAnalytics.Param.AD_UNIT_NAME, adUnitId);
        FirebaseAnalytics.getInstance(context).logEvent(event, bundle);

        Bundle fbBundle = new Bundle();
        fbBundle.putString(AppEventsConstants.EVENT_PARAM_AD_TYPE, adType);
        fbBundle.putString(FirebaseAnalytics.Param.AD_SOURCE, "admob");
        fbBundle.putString(FirebaseAnalytics.Param.AD_UNIT_NAME, adUnitId);
        logFbEvent(event, fbBundle);
    }

    public void logToolClickEvent(String toolName, String toolItem) {
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, toolName);
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, toolItem);
        FirebaseAnalytics.getInstance(context).logEvent("select_feature", bundle);

        Bundle fbBundle = new Bundle();
        fbBundle.putString(AppEventsConstants.EVENT_PARAM_CONTENT, toolName);
        fbBundle.putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID, toolItem);
        logFbEvent("select_feature", fbBundle);
    }

    public void logPaidAdImpression(
            Context context,
            AdValue adValue,
            String adUnitId,
            ResponseInfo responseInfo,
            AdType adType) {
        logPaidAdImpressionValue(
                context,
                adValue.getValueMicros(),
                adValue.getPrecisionType(),
                adUnitId,
                responseInfo.getMediationAdapterClassName(),
                AD_PLATFORM_ADMOB,
                null,
                adType,
                responseInfo.getLoadedAdapterResponseInfo().getAdSourceName()
        );
    }

    private void logPaidAdImpressionValue(
            Context context,
            double value,
            int precision,
            String adUnitId,
            String network,
            String mediationProvider,
            String adSourceUnitId,
            AdType adType,
            String adSource) {
        double valueData = value / 1000000.0;

        Bundle params = new Bundle();
        params.putDouble("value", valueData);
        params.putString("currency", "USD");
        params.putInt("precision", precision);
        params.putString("adunitid", adUnitId);
        params.putString("network", network);
        params.putString("ad_platform", mediationProvider);
        params.putString("ad_source_unit_id", adSourceUnitId);
        params.putString("ad_format", adType.toString());
        params.putString("ad_source", adSource);

        FirebaseAnalytics.getInstance(context).logEvent("paid_ad_impression_value", params);
        FirebaseAnalytics.getInstance(context).logEvent("paid_ad_impression", params);

        Bundle fbParams = new Bundle();
        fbParams.putString(AppEventsConstants.EVENT_PARAM_CURRENCY, "USD");
        fbParams.putString("ad_format", adType.toString());
        fbParams.putString("ad_source", adSource);
        fbParams.putString("ad_platform", mediationProvider);
        fbParams.putString("adunitid", adUnitId);
        fbParams.putString("network", network);
        logFbEvent("paid_ad_impression", valueData, fbParams);
    }

    public void logAdImpressionValue(
            Context context,
            String adUnitId,
            String network,
            AdType adType) {
        Bundle params = new Bundle();
        params.putString("adunitid", adUnitId);
        params.putString("network", network);
        params.putString("ad_format", adType.toString());
        logAdImpression(network, adUnitId, adType.toString());
        FirebaseAnalytics.getInstance(context).logEvent("track_ad_impression", params);
        FirebaseAnalytics.getInstance(context).logEvent("ad_impression_custom", params);
        logFbEvent("ad_impression_custom", params);
    }

    public void logAdImpression(String adPlatform, String adUnitId, String adFormat) {
        Bundle params = new Bundle();
        params.putString("ad_platform", adPlatform);
        params.putString("ad_format", adFormat);
        params.putString("ad_unit_id", adUnitId);
        logFbEvent("ad_impression", params);
    }

    public void trackAdRequest(
            Context context,
            String adPlatform,
            String adUnitId,
            AdType adType) {
        Log.i("FirebaseAnalyticsEvents",
                String.format("trackAdRequest adPlatform:%s  - adUnitId: %s  -  adType: %s",
                        adPlatform, logAdUnit(adUnitId), adType.toString()));

        Bundle bundle = new Bundle();
        bundle.putString("ad_platform", adPlatform);
        bundle.putString("adunitid", adUnitId);
        bundle.putBoolean("network_status", NetworkUtils.isNetworkAvailable(context));
        bundle.putString("ad_format", adType.toString());

        FirebaseAnalytics.getInstance(context).logEvent("track_ad_request", bundle);
        logFbEvent("track_ad_request", bundle);
    }

    public void trackAdMatchedRequest(
            Context context,
            String adPlatform,
            String adUnitId,
            AdType adType,
            ResponseInfo responseInfo) {
        Bundle bundle = new Bundle();
        bundle.putString("ad_platform", adPlatform);
        bundle.putString("adunitid", adUnitId);
        bundle.putBoolean("network_status", NetworkUtils.isNetworkAvailable(context));
        bundle.putString("ad_format", adType.toString());

        if (responseInfo != null && responseInfo.getLoadedAdapterResponseInfo() != null) {
            Log.i("FirebaseAnalyticsEvents",
                    String.format("trackAdMatchedRequest adPlatform:%s  - adUnitId: %s  -  adType: %s  -  adSource: %s",
                            adPlatform, logAdUnit(adUnitId), adType.toString(),
                            responseInfo.getLoadedAdapterResponseInfo().getAdSourceName()));
            bundle.putString("ad_source", responseInfo.getLoadedAdapterResponseInfo().getAdSourceName());
            bundle.putString("ad_source_unit_id", null);
        } else {
            Log.i("FirebaseAnalyticsEvents",
                    String.format("trackAdMatchedRequest adPlatform:%s  - adUnitId: %s  -  adType: %s  - responseInfo is null",
                            adPlatform, logAdUnit(adUnitId), adType.toString()));
        }

        FirebaseAnalytics.getInstance(context).logEvent("track_ad_matched_request", bundle);
        logFbEvent("track_ad_matched_request", bundle);
    }

    public void trackAdMatchedRequest(
            Context context,
            String adPlatform,
            String adUnitId,
            AdType adType,
            MaxAd adValue) {
        Log.i("FirebaseAnalyticsEvents",
                String.format("trackAdMatchedRequest adPlatform:%s  - adUnitId: %s  -  adType: %s  -  adSourceUnitId: %s  -  adSource: %s",
                        adPlatform, logAdUnit(adUnitId), adType.toString(),
                        adValue.getNetworkPlacement(), adValue.getNetworkName()));

        Bundle bundle = new Bundle();
        bundle.putString("ad_platform", adPlatform);
        bundle.putString("adunitid", adUnitId);
        bundle.putBoolean("network_status", NetworkUtils.isNetworkAvailable(context));
        bundle.putString("ad_format", adType.toString());
        bundle.putString("ad_source", adValue.getNetworkName());
        bundle.putString("ad_source_unit_id", adValue.getNetworkPlacement());

        FirebaseAnalytics.getInstance(context).logEvent("track_ad_matched_request", bundle);
    }

    public void logClickAdsEvent(Context context, String adUnitId) {
        Log.d("FirebaseAnalyticsEvents",
                String.format("User click ad for ad unit %s.", logAdUnit(adUnitId)));

        Bundle bundle = new Bundle();
        bundle.putString("ad_unit_id", adUnitId);
        FirebaseAnalytics.getInstance(context).logEvent("event_user_click_ads", bundle);
        logFbEvent("event_user_click_ads", bundle);
    }

    // =========================================================
    // FILL-RATE ANALYTICS
    // =========================================================

    public void logAdRequestSent(Context ctx, String adUnitId, AdType adType) {
        Bundle bundle = new Bundle();
        bundle.putString("ad_unit_id", adUnitId);
        bundle.putString("ad_format", adType.toString());
        FirebaseAnalytics.getInstance(ctx != null ? ctx : context).logEvent("ad_request_sent", bundle);
    }

    public void logAdRequestFilled(Context ctx, String adUnitId, AdType adType) {
        Bundle bundle = new Bundle();
        bundle.putString("ad_unit_id", adUnitId);
        bundle.putString("ad_format", adType.toString());
        FirebaseAnalytics.getInstance(ctx != null ? ctx : context).logEvent("ad_request_filled", bundle);
    }

    public void logAdShowSuccess(Context ctx, String adUnitId, AdType adType) {
        Bundle bundle = new Bundle();
        bundle.putString("ad_unit_id", adUnitId);
        bundle.putString("ad_format", adType.toString());
        FirebaseAnalytics.getInstance(ctx != null ? ctx : context).logEvent("ad_show_success", bundle);
    }

    public void logAdShowFailed(Context ctx, String adUnitId, AdType adType, String reason) {
        Bundle bundle = new Bundle();
        bundle.putString("ad_unit_id", adUnitId != null ? adUnitId : "unknown");
        bundle.putString("ad_format", adType.toString());
        bundle.putString("failure_reason", reason != null ? reason : "unknown");
        if (ctx != null) {
            FirebaseAnalytics.getInstance(ctx).logEvent("ad_show_failed", bundle);
        } else if (context != null) {
            FirebaseAnalytics.getInstance(context).logEvent("ad_show_failed", bundle);
        }
    }

    // =========================================================
    // PLACEMENT-AWARE ANALYTICS (v1.5.4)
    // Standard ad lifecycle events with placement_key for funnel analysis.
    // Filter Firebase Analytics by placement_key to see per-position metrics.
    // =========================================================

    public static final class AdEvent {
        public static final String REQUEST       = "ad_request";        // load started
        public static final String LOAD_SUCCESS  = "ad_load_success";   // load filled
        public static final String LOAD_FAILED   = "ad_load_failed";    // load error
        public static final String SHOW          = "ad_show";           // ad displayed
        public static final String SHOW_FAILED   = "ad_show_failed";    // show error
        public static final String IMPRESSION    = "ad_impression";     // viewable
        public static final String CLICK         = "ad_click";          // user clicked
        public static final String DISMISSED     = "ad_dismissed";      // user closed
        public static final String REWARD_EARNED = "ad_reward_earned";  // reward granted
        public static final String CACHE_HIT     = "ad_cache_hit";      // preload used
        public static final String FALLBACK_USED = "ad_fallback_used";  // pool/backup used
        public static final String SHOW_BLOCKED  = "ad_show_blocked";   // gated by cooldown/premium/etc.
        public static final String SKIPPED       = "ad_skipped";        // user skipped
        private AdEvent() {}
    }

    /**
     * Log a placement-aware ad event with standard fields.
     * <p>
     * Common parameters logged:
     * <ul>
     *     <li>{@code placement_key} — e.g. "INTER_HOME"</li>
     *     <li>{@code ad_format} — INTERSTITIAL / NATIVE / REWARDED / BANNER / APP_OPEN</li>
     *     <li>{@code ad_unit_id} — actual AdMob ad unit ID</li>
     *     <li>{@code ad_platform} — admob_sdk</li>
     * </ul>
     * Use {@link #logPlacementEvent(Context, String, String, String, AdType, Bundle)} to add extras
     * (e.g. {@code error_code}, {@code latency_ms}, {@code fallback_type}, {@code block_reason}).
     */
    public void logPlacementEvent(Context ctx, String eventName, String placementKey,
                                   String adUnitId, AdType adFormat) {
        logPlacementEvent(ctx, eventName, placementKey, adUnitId, adFormat, null);
    }

    /**
     * Log a placement-aware event with custom extras (error_code, latency_ms, etc.).
     */
    public void logPlacementEvent(Context ctx, String eventName, String placementKey,
                                   String adUnitId, AdType adFormat, Bundle extras) {
        Context c = ctx != null ? ctx : context;
        if (c == null) return;
        Bundle params = new Bundle();
        if (extras != null) params.putAll(extras);
        params.putString("placement_key", placementKey != null ? placementKey : "unknown");
        params.putString("ad_format", adFormat != null ? adFormat.toString() : "unknown");
        params.putString("ad_unit_id", adUnitId != null ? adUnitId : "unknown");
        params.putString("ad_platform", AD_PLATFORM_ADMOB);
        FirebaseAnalytics.getInstance(c).logEvent(eventName, params);

        if (BuildLog.ENABLED) {
            android.util.Log.d("AdEvent", eventName + " | placement=" + placementKey
                    + " format=" + adFormat + " unit=" + adUnitId);
        }
    }

    public void logRequest(Context ctx, String placementKey, String adUnitId, AdType adFormat) {
        logPlacementEvent(ctx, AdEvent.REQUEST, placementKey, adUnitId, adFormat);
    }

    public void logLoadSuccess(Context ctx, String placementKey, String adUnitId, AdType adFormat, long latencyMs) {
        Bundle b = new Bundle();
        if (latencyMs >= 0) b.putLong("latency_ms", latencyMs);
        logPlacementEvent(ctx, AdEvent.LOAD_SUCCESS, placementKey, adUnitId, adFormat, b);
    }

    public void logLoadFailed(Context ctx, String placementKey, String adUnitId, AdType adFormat,
                               int errorCode, String errorMessage, long latencyMs) {
        Bundle b = new Bundle();
        b.putInt("error_code", errorCode);
        b.putString("error_message", errorMessage != null ? errorMessage : "unknown");
        if (latencyMs >= 0) b.putLong("latency_ms", latencyMs);
        logPlacementEvent(ctx, AdEvent.LOAD_FAILED, placementKey, adUnitId, adFormat, b);
    }

    public void logShow(Context ctx, String placementKey, String adUnitId, AdType adFormat) {
        logPlacementEvent(ctx, AdEvent.SHOW, placementKey, adUnitId, adFormat);
    }

    public void logShowFailed(Context ctx, String placementKey, String adUnitId, AdType adFormat,
                               int errorCode, String errorMessage) {
        Bundle b = new Bundle();
        b.putInt("error_code", errorCode);
        b.putString("error_message", errorMessage != null ? errorMessage : "unknown");
        logPlacementEvent(ctx, AdEvent.SHOW_FAILED, placementKey, adUnitId, adFormat, b);
    }

    public void logImpression(Context ctx, String placementKey, String adUnitId, AdType adFormat) {
        logPlacementEvent(ctx, AdEvent.IMPRESSION, placementKey, adUnitId, adFormat);
    }

    public void logClick(Context ctx, String placementKey, String adUnitId, AdType adFormat) {
        logPlacementEvent(ctx, AdEvent.CLICK, placementKey, adUnitId, adFormat);
    }

    public void logDismissed(Context ctx, String placementKey, String adUnitId, AdType adFormat) {
        logPlacementEvent(ctx, AdEvent.DISMISSED, placementKey, adUnitId, adFormat);
    }

    public void logRewardEarned(Context ctx, String placementKey, String adUnitId,
                                 String rewardType, int rewardAmount) {
        Bundle b = new Bundle();
        b.putString("reward_type", rewardType != null ? rewardType : "unknown");
        b.putInt("reward_amount", rewardAmount);
        logPlacementEvent(ctx, AdEvent.REWARD_EARNED, placementKey, adUnitId, AdType.REWARDED, b);
    }

    public void logCacheHit(Context ctx, String placementKey, String adUnitId, AdType adFormat) {
        logPlacementEvent(ctx, AdEvent.CACHE_HIT, placementKey, adUnitId, adFormat);
    }

    /**
     * @param fallbackType "default_pool" | "native_backup" | "interstitial_fallback"
     */
    public void logFallbackUsed(Context ctx, String placementKey, String adUnitId,
                                 AdType adFormat, String fallbackType) {
        Bundle b = new Bundle();
        b.putString("fallback_type", fallbackType != null ? fallbackType : "unknown");
        logPlacementEvent(ctx, AdEvent.FALLBACK_USED, placementKey, adUnitId, adFormat, b);
    }

    /**
     * @param blockReason "cooldown" | "premium" | "no_consent" | "ads_disabled" | "no_network" |
     *                   "activity_destroyed" | "no_placement" | "disabled"
     */
    public void logShowBlocked(Context ctx, String placementKey, AdType adFormat, String blockReason) {
        Bundle b = new Bundle();
        b.putString("block_reason", blockReason != null ? blockReason : "unknown");
        logPlacementEvent(ctx, AdEvent.SHOW_BLOCKED, placementKey, null, adFormat, b);
    }

    public void logSkipped(Context ctx, String placementKey, String adUnitId, AdType adFormat,
                            long secondsBeforeSkip) {
        Bundle b = new Bundle();
        b.putLong("seconds_before_skip", secondsBeforeSkip);
        logPlacementEvent(ctx, AdEvent.SKIPPED, placementKey, adUnitId, adFormat, b);
    }

    private static final class BuildLog {
        static final boolean ENABLED = true;  // flip to false in production
    }

    private static String logAdUnit(String adUnitId) {
        return adUnitId == null ? "null" : adUnitId;
    }
}
