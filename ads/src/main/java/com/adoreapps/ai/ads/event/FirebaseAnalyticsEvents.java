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
    static AppEventsLogger appEventsLogger;

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
        appEventsLogger = AppEventsLogger.newLogger(context);
    }

    public void logEvent(String eventName, String screenName) {
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName);
        Log.i("picshiner_analytics_event", "Event: " + eventName);
        FirebaseAnalytics.getInstance(context).logEvent(eventName, bundle);

    }

    public void logEvent(String eventName) {
        FirebaseAnalytics.getInstance(context).logEvent(eventName, null);
        appEventsLogger.logEvent(eventName);
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

        appEventsLogger.logEvent(event, fbBundle);



    }

    public void logToolClickEvent(String toolName, String toolItem) {
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, toolName);
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, toolItem);

        FirebaseAnalytics.getInstance(context).logEvent("select_feature", bundle);

        Bundle fbBundle = new Bundle();
        fbBundle.putString(AppEventsConstants.EVENT_PARAM_CONTENT, toolName);
        fbBundle.putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID, toolItem);

        appEventsLogger.logEvent("select_feature", bundle);
    }
    public  void logPaidAdImpression(
            Context context,
            AdValue adValue,
            String adUnitId,
            ResponseInfo responseInfo,
            AdType adType
    ) {
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

    // Firebase Custom event ad impression to tracking revenue
    private  void logPaidAdImpressionValue(
            Context context,
            double value,
            int precision,
            String adUnitId,
            String network,
            String mediationProvider,
            String adSourceUnitId,
            AdType adType,
            String adSource
    ) {
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
        appEventsLogger.logEvent("paid_ad_impression", valueData, fbParams);
    }

    public void logAdImpressionValue(
            Context context,
            String adUnitId,
            String network,
            AdType adType
    ) {

        Bundle params = new Bundle();
        params.putString("adunitid", adUnitId);
        params.putString("network", network);
        params.putString("ad_format", adType.toString());
        logAdImpression(network, adUnitId, adType.toString());
        FirebaseAnalytics.getInstance(context).logEvent("track_ad_impression", params);
        FirebaseAnalytics.getInstance(context).logEvent("ad_impression_custom", params);
        appEventsLogger.logEvent("ad_impression_custom", params);
    }

    public void logAdImpression(
            String adPlatform,
            String adUnitId,
            String adFormat
    ) {

        Bundle params = new Bundle();
        params.putString("ad_platform", adPlatform); // admob
        params.putString("ad_format", adFormat);     // banner / interstitial / native
        params.putString("ad_unit_id", adUnitId);
        appEventsLogger.logEvent("ad_impression", params);
    }

    public  void trackAdRequest(
            Context context,
            String adPlatform,
            String adUnitId,
            AdType adType
    ) {
        Log.i(
                "FirebaseAnalyticsEvents",
                String.format(
                        "trackAdRequest adPlatform:%s  - adUnitId: %s  -  adType: %s",
                        adPlatform,
                        logAdUnit(adUnitId),
                        adType.toString()
                )
        );

        Bundle bundle = new Bundle();
        bundle.putString("ad_platform", adPlatform);
        bundle.putString("adunitid", adUnitId);
        bundle.putBoolean("network_status", NetworkUtils.isNetworkAvailable(context));
        bundle.putString("ad_format", adType.toString());

        FirebaseAnalytics.getInstance(context).logEvent("track_ad_request", bundle);
        appEventsLogger.logEvent("track_ad_request", bundle);
    }

    public  void trackAdMatchedRequest(
            Context context,
            String adPlatform,
            String adUnitId,
            AdType adType,
            ResponseInfo responseInfo
    ) {
        Bundle bundle = new Bundle();
        bundle.putString("ad_platform", adPlatform);
        bundle.putString("adunitid", adUnitId);
        bundle.putBoolean("network_status", NetworkUtils.isNetworkAvailable(context));
        bundle.putString("ad_format", adType.toString());

        if (responseInfo != null && responseInfo.getLoadedAdapterResponseInfo() != null) {
            Log.i(
                    "FirebaseAnalyticsEvents",
                    String.format(
                            "trackAdMatchedRequest adPlatform:%s  - adUnitId: %s  -  adType: %s  -  adSourceUnitId:   -  adSource: %s",
                            adPlatform,
                            logAdUnit(adUnitId),
                            adType.toString(),
                            responseInfo.getLoadedAdapterResponseInfo().getAdSourceName()
                    )
            );

            bundle.putString("ad_source", responseInfo.getLoadedAdapterResponseInfo().getAdSourceName());
            bundle.putString("ad_source_unit_id", null);
        } else {
            Log.i(
                    "FirebaseAnalyticsEvents",
                    String.format(
                            "trackAdMatchedRequest adPlatform:%s  - adUnitId: %s  -  adType: %s  - responseInfo is null",
                            adPlatform,
                            logAdUnit(adUnitId),
                            adType.toString()
                    )
            );
        }

        FirebaseAnalytics.getInstance(context).logEvent("track_ad_matched_request", bundle);
        appEventsLogger.logEvent("track_ad_matched_request", bundle);
    }

    public  void trackAdMatchedRequest(
            Context context,
            String adPlatform,
            String adUnitId,
            AdType adType,
            MaxAd adValue
    ) {
        Log.i(
                "FirebaseAnalyticsEvents",
                String.format(
                        "trackAdMatchedRequest adPlatform:%s  - adUnitId: %s  -  adType: %s  -  adSourceUnitId: %s  -  adSource: %s",
                        adPlatform,
                        logAdUnit(adUnitId),
                        adType.toString(),
                        adValue.getNetworkPlacement(),
                        adValue.getNetworkName()
                )
        );

        Bundle bundle = new Bundle();
        bundle.putString("ad_platform", adPlatform);
        bundle.putString("adunitid", adUnitId);
        bundle.putBoolean("network_status", NetworkUtils.isNetworkAvailable(context));
        bundle.putString("ad_format", adType.toString());
        bundle.putString("ad_source", adValue.getNetworkName());
        bundle.putString("ad_source_unit_id", adValue.getNetworkPlacement());

        FirebaseAnalytics.getInstance(context).logEvent("track_ad_matched_request", bundle);
    }

    public  void logClickAdsEvent(Context context, String adUnitId) {
        Log.d(
                "FirebaseAnalyticsEvents",
                String.format(
                        "User click ad for ad unit %s.",
                        logAdUnit(adUnitId)
                )
        );

        Bundle bundle = new Bundle();
        bundle.putString("ad_unit_id", adUnitId);
        FirebaseAnalytics.getInstance(context).logEvent("event_user_click_ads", bundle);
        appEventsLogger.logEvent("event_user_click_ads", bundle);

    }

    // Utility to mimic Kotlin's extension function logAdUnit()
    private static String logAdUnit(String adUnitId) {
        return adUnitId == null ? "null" : adUnitId;
    }
}
