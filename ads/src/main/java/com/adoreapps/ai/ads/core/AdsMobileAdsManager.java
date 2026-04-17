package com.adoreapps.ai.ads.core;

import com.adoreapps.ai.ads.AdCallback;
import com.adoreapps.ai.ads.core.AppOpenAdManager;
import com.adoreapps.ai.ads.AdoreAds;
import com.adoreapps.ai.ads.billing.PurchaseManager;
import com.adoreapps.ai.ads.R;
import com.adoreapps.ai.ads.settings.AdConstants;

import static com.adoreapps.ai.ads.event.FirebaseAnalyticsEvents.AD_PLATFORM_ADMOB;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.adoreapps.ai.ads.event.AdType;
import com.adoreapps.ai.ads.event.AdjustEvents;
import com.adoreapps.ai.ads.event.FirebaseAnalyticsEvents;
import com.adoreapps.ai.ads.model.AdsResponse;
import com.adoreapps.ai.ads.wrapper.ApAdError;
import com.adoreapps.ai.ads.wrapper.ApAdNative;
import com.adoreapps.ai.ads.wrapper.ApAdView;
import com.adoreapps.ai.ads.wrapper.ApInterstitialAd;
import com.adoreapps.ai.ads.wrapper.ApRewardItem;
import com.facebook.ads.AudienceNetworkAds;
import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdValue;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.OnPaidEventListener;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.ResponseInfo;
import com.google.android.gms.ads.VideoOptions;
import com.google.android.gms.ads.initialization.AdapterStatus;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.mbridge.msdk.MBridgeConstans;
import com.mbridge.msdk.MBridgeSDK;
import com.mbridge.msdk.out.MBridgeSDKFactory;
import com.unity3d.ads.metadata.MetaData;
import com.vungle.ads.VunglePrivacySettings;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class AdsMobileAdsManager {
    private static AdsMobileAdsManager instance;
    private boolean adsEnabled = true;
    private boolean isShowLoadingDialog = true;
    private boolean isShowTestAds = false;
    private boolean isUseTestAdIds = false;
    private boolean facebookEnabled = true;
    private boolean hasLog = true;
    private List<String> testDeviceIds = new ArrayList<>();


    private final LoadAdError errAd = new LoadAdError(2, "No Ad", "", (AdError)null, (ResponseInfo)null);

    private final WeakHashMap<FrameLayout, BroadcastReceiver> registeredReceivers = new WeakHashMap<>();

    private InterstitialAd interstitialAd;
    private RewardedAd rewardedAd;

    private AdsMobileAdsManager() {}

    public static synchronized AdsMobileAdsManager getInstance() {
        if (instance == null) {
            instance = new AdsMobileAdsManager();
        }
        return instance;
    }

    private List<String> deduplicateAdIds(List<String> ids) {
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    public void init(Context context, Runnable runnable) {
        long startTime = System.currentTimeMillis();

        // Initialize AdMob on a background thread to prevent ANR
        new Thread(() -> {
            Log.i("AdInit", "Initializing AdMob...");
            MobileAds.disableMediationAdapterInitialization(context);
            MobileAds.initialize(context, initializationStatus -> {
                long duration = System.currentTimeMillis() - startTime;
                Log.i("AdInit", "AdMob SDK initialized in " + duration + " ms");

                // Optional: print adapter status to find slow ones
                try {
                    Map<String, AdapterStatus> statusMap = initializationStatus.getAdapterStatusMap();
                    for (Map.Entry<String, AdapterStatus> entry : statusMap.entrySet()) {
                        Log.d("AdInitAdapter", entry.getKey() + " - " + entry.getValue().getInitializationState());
                    }
                } catch (Exception e) {
                    Log.w("AdInit", "Failed to read adapter statuses: " + e.getMessage());
                }

                // Run callback on main thread after AdMob is ready
                if (runnable != null) {
                    new Handler(Looper.getMainLooper()).post(runnable);
                }

            });

            // Apply test device IDs if configured
            if (!testDeviceIds.isEmpty()) {
                applyTestDeviceConfig();
            } else if (isShowTestAds) {
                // Legacy: auto-detect current device
                testDeviceIds.add(getDeviceId(context));
                applyTestDeviceConfig();
            }
        }, "AdMob-Init").start();
    }

    /**
     * Initialize remaining mediation networks in background with parallel execution.
     */
    public void initRemainingNetworksAsync(Context context, boolean hasConsent) {
        // Parallel init with 3 threads (Funswap optimization: saves 3-8s vs sequential).
        // Group SDKs to minimize class-loading lock contention:
        //   Thread 1: Vungle
        //   Thread 2: MBridge
        //   Thread 3: Unity
        //   Meta: delayed post to main thread (avoids class-loading pressure)
        ExecutorService parallelExecutor = Executors.newFixedThreadPool(3);

        // Thread 1: Vungle
        parallelExecutor.execute(() -> {
            try {
                Log.i("AdInit", "Initializing Vungle...");
                VunglePrivacySettings.setGDPRStatus(hasConsent, "v1.0.0");
                VunglePrivacySettings.setCCPAStatus(hasConsent);
                Log.i("AdInit", "Vungle ready");
            } catch (Exception e) {
                Log.e("AdInit", "Vungle init failed: " + e.getMessage());
            }
        });

        // Thread 2: MBridge
        // AppLovin 13.6+, Pangle 7.9+, and MBridge 17.1+ read consent from
        // the IAB TCF string written by UMP — no manual consent setters needed.
        parallelExecutor.execute(() -> {
            try {
                Log.i("AdInit", "Initializing MBridge...");
                MBridgeSDK mBridgeSDK = MBridgeSDKFactory.getMBridgeSDK();
                mBridgeSDK.setConsentStatus(context, hasConsent ? MBridgeConstans.IS_SWITCH_ON : MBridgeConstans.IS_SWITCH_OFF);
                Log.i("AdInit", "MBridge ready");
            } catch (Exception e) {
                Log.e("AdInit", "MBridge init failed: " + e.getMessage());
            }
        });

        // Thread 3: Unity
        parallelExecutor.execute(() -> {
            try {
                Log.i("AdInit", "Initializing Unity...");
                MetaData gdprMetaData = new MetaData(context);
                gdprMetaData.set("gdpr.consent", hasConsent);
                gdprMetaData.commit();

                MetaData ccpaMetaData = new MetaData(context);
                ccpaMetaData.set("privacy.consent", hasConsent);
                ccpaMetaData.commit();
                Log.i("AdInit", "Unity ready");
            } catch (Exception e) {
                Log.e("AdInit", "Unity init failed: " + e.getMessage());
            }
        });

        // Meta Audience Network — delayed post to main thread (avoids class-loading pressure).
        if (facebookEnabled) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    AudienceNetworkInitializeHelper.initialize(context);
                    Log.i("AdInit", "Meta Audience Network initialized");
                } catch (Exception e) {
                    Log.e("AdInit", "Meta init failed: " + e.getMessage());
                }
            }, 3000);
        } else {
            Log.i("AdInit", "Meta Audience Network skipped (Facebook disabled)");
        }

        parallelExecutor.shutdown();
    }

    /**
     * Re-propagate consent status to all mediation networks after UMP consent gathering.
     * Call this from Splash after gatherConsent() completes.
     */
    public void updateConsentNetworks(Context context, boolean hasConsent) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                // AppLovin 13.6+, Pangle 7.9+, MBridge 17.1+ read consent
                // from the IAB TCF string written by UMP automatically.
                VunglePrivacySettings.setGDPRStatus(hasConsent, "v1.0.0");
                VunglePrivacySettings.setCCPAStatus(hasConsent);

                MBridgeSDK mBridgeSDK = MBridgeSDKFactory.getMBridgeSDK();
                mBridgeSDK.setConsentStatus(context, hasConsent ? MBridgeConstans.IS_SWITCH_ON : MBridgeConstans.IS_SWITCH_OFF);

                MetaData gdprMetaData = new MetaData(context);
                gdprMetaData.set("gdpr.consent", hasConsent);
                gdprMetaData.commit();

                MetaData ccpaMetaData = new MetaData(context);
                ccpaMetaData.set("privacy.consent", hasConsent);
                ccpaMetaData.commit();

                Log.i("AdInit", "✅ Consent updated for all mediation networks: " + hasConsent);
            } catch (Exception e) {
                Log.e("AdInit", "Failed to update consent: " + e.getMessage());
            }
        });
        executor.shutdown();
    }

    public void init(Context context, String deviceID, boolean isShowTestAds, Runnable runnable) {
        try {
            init(context, runnable);
            setShowTestAds(isShowTestAds);

        } catch (Exception var4) {
            Exception e = var4;
            Log.e("AdsMobileAdsManager", "Error: " + e.getMessage());
        }

    }

    public boolean isShowLoadingDialog() {
        return this.isShowLoadingDialog;
    }

    public boolean isUseTestAdIds() {
        return this.isUseTestAdIds;
    }

    public void setUseTestAdIds(boolean isUseTestAdIds) {
        this.isUseTestAdIds = isUseTestAdIds;
    }

    public void setShowLoadingDialog(boolean showLoadingDialog) {
        this.isShowLoadingDialog = showLoadingDialog;
    }

    public boolean isShowTestAds() {
        return this.isShowTestAds;
    }

    public void setShowTestAds(boolean showTestAds) {
        this.isShowTestAds = showTestAds;
    }

    public void setAdsEnabled(boolean enabled) {
        this.adsEnabled = enabled;
    }


    public boolean isAdsEnabled() {
        return adsEnabled;
    }

    public void setFacebookEnabled(boolean enabled) {
        this.facebookEnabled = enabled;
    }

    public boolean isFacebookEnabled() {
        return facebookEnabled;
    }

    /**
     * Register test device IDs. These devices see live ads with a "Test Ad" tag.
     * Also adds the current device if autoDetect is true.
     */
    public void setTestDeviceIds(List<String> ids, boolean autoDetectCurrentDevice, Context context) {
        this.testDeviceIds = new ArrayList<>();
        if (ids != null) this.testDeviceIds.addAll(ids);
        if (autoDetectCurrentDevice && context != null) {
            String currentDeviceId = getDeviceId(context);
            if (!this.testDeviceIds.contains(currentDeviceId)) {
                this.testDeviceIds.add(currentDeviceId);
            }
        }
        applyTestDeviceConfig();
    }

    private void applyTestDeviceConfig() {
        if (!testDeviceIds.isEmpty()) {
            new Handler(Looper.getMainLooper()).post(() -> {
                RequestConfiguration config = new RequestConfiguration.Builder()
                        .setTestDeviceIds(testDeviceIds)
                        .build();
                MobileAds.setRequestConfiguration(config);
                Log.i("AdInit", "Test devices registered: " + testDeviceIds.size());
            });
        }
    }


    public AdRequest getAdRequest() {
        if (this.isAdsEnabled() && !PurchaseManager.getInstance().isPurchased()) {
            AdRequest.Builder builder = new AdRequest.Builder();
            return builder.build();
        } else {
            return null;
        }
    }
    public void loadInterAds(Context context, String id, final AdCallback callback) {
        if(PurchaseManager.getInstance().isPurchased()) {
            callback.onNextScreen();
            return;
        }
        this.log("request inter: " + id);
        AdRequest request = this.getAdRequest();
        if (request == null) {
            callback.onAdFailedToLoad(new ApAdError(this.errAd));
        } else {
            FirebaseAnalyticsEvents.getInstance().trackAdRequest(
                    context,
                    AD_PLATFORM_ADMOB,
                    id,
                    AdType.INTERSTITIAL
            );
            FirebaseAnalyticsEvents.getInstance().logAdEvent(
                    "ad_request",
                    AdType.INTERSTITIAL.name(),
                    id
            );
            InterstitialAd.load(context, id, request, new InterstitialAdLoadCallback() {
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    super.onAdFailedToLoad(loadAdError);
                    if (callback != null) {
                        callback.onAdFailedToLoad(new ApAdError(loadAdError));
                    }

                }

                public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                    super.onAdLoaded(interstitialAd);
                    FirebaseAnalyticsEvents.getInstance().trackAdMatchedRequest(
                            context,
                            AD_PLATFORM_ADMOB,
                            id,
                            AdType.INTERSTITIAL,
                            interstitialAd.getResponseInfo()
                    );
                    if (callback != null) {
                        callback.onResultInterstitialAd(new ApInterstitialAd(interstitialAd));
                    }
                }
            });
        }
    }

    public void log(String s) {
        if (this.hasLog) {
            Log.d("android_log", "log: " + s);
        }

    }
    public void loadAlternateInterstitialAds(final Context context, List<String> idsInput, final AdCallback callback) {
        if (PurchaseManager.getInstance().isPurchased()) {
            callback.onNextScreen();
            return;
        }
        final List<String> ids = deduplicateAdIds(idsInput);
        if (ids.isEmpty()) {
            callback.onNextScreen();
        } else {
            if (AppOpenAdManager.getInstance().isInitialized()) {
                AppOpenAdManager.getInstance().disableAppResume();
            }
            final AtomicBoolean responded = new AtomicBoolean(false);
            Handler timeoutHandler = new Handler(Looper.getMainLooper());
            timeoutHandler.postDelayed(() -> {
                if (responded.compareAndSet(false, true)) {
                    Log.w("AdmobLogger", "loadAlternateInterstitialAds: TIMEOUT");
                    if (AppOpenAdManager.getInstance().isInitialized()) {
                        AppOpenAdManager.getInstance().enableAppResume();
                    }
                    callback.onAdFailedToLoad(new ApAdError(errAd));
                    callback.onNextScreen();
                }
            }, AdConstants.AD_LOAD_TIMEOUT_MS);
            loadAlternateInter(context, idsInput, new AdCallback() {
                @Override
                public void onResultInterstitialAd(ApInterstitialAd ad) {
                    if (responded.compareAndSet(false, true)) {
                        timeoutHandler.removeCallbacksAndMessages(null);
                        callback.onResultInterstitialAd(ad);
                    }
                }
                @Override
                public void onAdFailedToLoad(@NonNull ApAdError error) {
                    if (responded.compareAndSet(false, true)) {
                        timeoutHandler.removeCallbacksAndMessages(null);
                        callback.onAdFailedToLoad(error);
                    }
                }
                @Override
                public void onNextScreen() {
                    if (responded.compareAndSet(false, true)) {
                        timeoutHandler.removeCallbacksAndMessages(null);
                        callback.onNextScreen();
                    }
                }
                @Override
                public void onAdClicked() { callback.onAdClicked(); }
                @Override
                public void onAdImpression() { callback.onAdImpression(); }
            });
        }
    }
    public void loadAlternateInter(final Context context, List<String> idsInput, final AdCallback callback) {

        final List<String> ids = deduplicateAdIds(idsInput);
        if (ids.isEmpty()) {
            if(context != null) {
                context.sendBroadcast(new Intent("action_dismiss_dialog"));
            }
            if (AppOpenAdManager.getInstance().isInitialized()) {
                AppOpenAdManager.getInstance().enableAppResume();
            }
            callback.onNextScreen();
        } else {
            String interstitialAdId = (String)ids.get(0);
            if(isUseTestAdIds()) {
                interstitialAdId = AdConstants.TEST_INTERSTITIAL_AD_ID;
            }


            String finalInterstitialAdId = interstitialAdId;
            String finalInterstitialAdId1 = interstitialAdId;
            FirebaseAnalyticsEvents.getInstance().logAdEvent(
                    "ad_request",
                    AdType.INTERSTITIAL.name(),
                    interstitialAdId
            );
            this.loadInterAds(context, interstitialAdId , new AdCallback() {
                public void onAdFailedToLoad(@NonNull ApAdError i) {
                    super.onAdFailedToLoad(i);

                    Log.w("AdmobLogger", "loadAlternateInter: fail-" + finalInterstitialAdId);
                    ids.remove(0);
                    if (ids.isEmpty()) {
                        if(context != null) {
                            context.sendBroadcast(new Intent("action_dismiss_dialog"));
                        }
                        if (AppOpenAdManager.getInstance().isInitialized()) {
                            AppOpenAdManager.getInstance().enableAppResume();
                        }
                        callback.onAdFailedToLoad(i);
                    } else {
                        loadAlternateInter(context, ids, callback);
                    }

                }

                public void onResultInterstitialAd(ApInterstitialAd interstitialAd) {
                    super.onResultInterstitialAd(interstitialAd);
                    if (AppOpenAdManager.getInstance().isInitialized()) {
                        AppOpenAdManager.getInstance().enableAppResume();
                    }
                    callback.onResultInterstitialAd(interstitialAd);
                    Log.i("AdmobLogger", "loadAlternateInter: success-" + finalInterstitialAdId);
                }

                public void onAdClicked() {
                    super.onAdClicked();
                    if (callback != null) {
                        callback.onAdClicked();
                    }
                    FirebaseAnalyticsEvents.getInstance().logClickAdsEvent(
                            context,
                            finalInterstitialAdId
                    );

                }

                public void onAdImpression() {
                    super.onAdImpression();
                    FirebaseAnalyticsEvents.getInstance().logAdImpressionValue(
                            context,
                            finalInterstitialAdId1,
                            AD_PLATFORM_ADMOB,
                            AdType.INTERSTITIAL
                    );
                    if (callback != null) {
                        callback.onAdImpression();
                    }

                }
            });
        }
    }

    public void showInterstitial(final Activity context, InterstitialAd mInterstitialAd, final AdCallback callback) {
        if (mInterstitialAd != null && !PurchaseManager.getInstance().isPurchased()) {
            mInterstitialAd.setOnPaidEventListener(new OnPaidEventListener() {
                @Override
                public void onPaidEvent(@NonNull AdValue adValue) {
                    AdjustEvents.getInstance().pushTrackEventAdmob(adValue);
                    FirebaseAnalyticsEvents.getInstance()
                            .logPaidAdImpression(context,
                                    adValue,
                                    mInterstitialAd.getAdUnitId(),
                                    mInterstitialAd.getResponseInfo(),
                                    AdType.INTERSTITIAL
                            );
                }
            });
            mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                public void onAdDismissedFullScreenContent() {
                    FirebaseAnalyticsEvents.getInstance().logAdEvent(
                            "ad_closed",
                            AdType.INTERSTITIAL.name(),
                            mInterstitialAd.getAdUnitId()
                    );
                    if(context != null) {
                        context.sendBroadcast(new Intent("action_dismiss_dialog"));
                    }
                    if (AppOpenAdManager.getInstance().isInitialized()) {
                        AppOpenAdManager.getInstance().enableAppResume();
                    }

                    if (callback != null) {
                        callback.onAdClosed();
                        callback.onNextScreen();
                    }

                }

                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    if (AppOpenAdManager.getInstance().isInitialized()) {
                        AppOpenAdManager.getInstance().enableAppResume();
                    }

                    if(context != null) {
                        context.sendBroadcast(new Intent("action_dismiss_dialog"));
                    }
                    if (callback != null) {
                        callback.onAdClosed();
                        callback.onNextScreen();
                    }

                }

                public void onAdShowedFullScreenContent() {
                    if (AppOpenAdManager.getInstance().isInitialized()) {
                        AppOpenAdManager.getInstance().disableAppResume();
                    }
                    if(context != null) {
                        context.sendBroadcast(new Intent("action_dismiss_dialog"));
                    }
                    if (callback != null) {
                        callback.onAdShowedFullScreenContent();
                    }

                }

                public void onAdClicked() {
                    super.onAdClicked();
                    if (callback != null) {
                        callback.onAdClicked();
                    }
                    FirebaseAnalyticsEvents.getInstance().logClickAdsEvent(
                            context,
                            mInterstitialAd.getAdUnitId()
                    );
                }
            });
            if (context != null && !context.isDestroyed() && ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {

                if (AppOpenAdManager.getInstance().isInitialized()) {
                    AppOpenAdManager.getInstance().disableAppResume();
                }
                mInterstitialAd.show(context);
            } else if (callback != null) {
                if(context != null) {
                    context.sendBroadcast(new Intent("action_dismiss_dialog"));
                }
                if (AppOpenAdManager.getInstance().isInitialized()) {
                    AppOpenAdManager.getInstance().enableAppResume();
                }
                callback.onAdClosed();
                callback.onNextScreen();
            }

        } else {
            if(context != null) {
                context.sendBroadcast(new Intent("action_dismiss_dialog"));
            }
            if (AppOpenAdManager.getInstance().isInitialized()) {
                AppOpenAdManager.getInstance().enableAppResume();
            }
            if (callback != null) {
                callback.onAdClosed();
                callback.onNextScreen();
            }

        }
    }

    public void loadBanner(Activity mActivity, final String id, final FrameLayout adContainer, final AdCallback callback) {
        loadBanner(mActivity, id, adContainer, null, callback);
    }

    public void loadBanner(Activity mActivity, final String id, final FrameLayout adContainer,
                           AdSize customSize, final AdCallback callback) {
        if(PurchaseManager.getInstance().isPurchased()) {
            adContainer.removeAllViews();
            adContainer.setVisibility(View.GONE);
            return;
        }
        this.log("Request Banner :" + id);
        AdRequest request = this.getAdRequest();
        if (request == null) {
            adContainer.removeAllViews();
            adContainer.setVisibility(View.GONE);
        } else {
            try {
                final AdView adView = new AdView(mActivity);
                adView.setAdUnitId(id);
                AdSize adSize = customSize != null ? customSize : this.getAdSize(mActivity);
                adView.setAdSize(adSize);
                adView.setLayerType(View.LAYER_TYPE_SOFTWARE, (Paint)null);
                FirebaseAnalyticsEvents.getInstance().trackAdRequest(
                        mActivity,
                        AD_PLATFORM_ADMOB,
                        id,
                        AdType.BANNER
                );
                adView.loadAd(request);
                adView.setAdListener(new AdListener() {
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        super.onAdFailedToLoad(loadAdError);
                        adContainer.removeAllViews();
                        adContainer.setVisibility(View.GONE);
                        callback.onAdFailedToLoad(new ApAdError(loadAdError));
                    }

                    public void onAdLoaded() {
                        callback.onAdLoaded();
                        FirebaseAnalyticsEvents.getInstance().logAdEvent(
                                "ad_request",
                                AdType.BANNER.name(),
                                id
                        );
                        FirebaseAnalyticsEvents.getInstance().trackAdMatchedRequest(
                                mActivity,
                                AD_PLATFORM_ADMOB,
                                adView.getAdUnitId(),
                                AdType.BANNER,
                                adView.getResponseInfo()
                        );
                        adContainer.removeAllViews();
                        adContainer.setVisibility(View.VISIBLE);
                        adContainer.addView(adView);
                        adView.setOnPaidEventListener(new OnPaidEventListener() {
                            @Override
                            public void onPaidEvent(@NonNull AdValue adValue) {
                                AdjustEvents.getInstance().pushTrackEventAdmob(adValue);
                                FirebaseAnalyticsEvents.getInstance()
                                        .logPaidAdImpression(mActivity,
                                                adValue,
                                                adView.getAdUnitId(),
                                                adView.getResponseInfo(),
                                                AdType.BANNER
                                        );
                            }
                        });
                    }

                    public void onAdImpression() {
                        super.onAdImpression();
                        FirebaseAnalyticsEvents.getInstance().logAdImpressionValue(
                                mActivity,
                                id,
                                AD_PLATFORM_ADMOB,
                                AdType.BANNER
                        );
                        if (callback != null) {
                            callback.onAdImpression();
                        }

                    }

                    public void onAdClicked() {
                        super.onAdClicked();
                        if (callback != null) {
                            callback.onAdClicked();
                        }
                        FirebaseAnalyticsEvents.getInstance().logClickAdsEvent(
                                mActivity,
                                id
                        );
                    }
                });
            } catch (Exception var8) {
                Exception e = var8;
                Log.e("AdsMobileAdsManager", "Error: " + e.getMessage());
            }

        }
    }


    public void loadAlternateBanner(final Activity activity, final List<String> idsInput, final FrameLayout adContainer) {
        loadAlternateBanner(activity, idsInput, adContainer, null);
    }

    public void loadAlternateBanner(final Activity activity, final List<String> idsInput,
                                     final FrameLayout adContainer, final AdSize customSize) {
        if(PurchaseManager.getInstance().isPurchased()) {
            adContainer.removeAllViews();
            adContainer.setVisibility(View.GONE);
            return;
        }
        final List<String> ids = deduplicateAdIds(idsInput);
        if (ids.isEmpty()) {
            adContainer.setVisibility(View.GONE);
        } else {
            String bannerId;
            if(isUseTestAdIds()) {
                bannerId = AdConstants.TEST_BANNER_AD_ID;
            } else {
                bannerId = (String) ids.get(0);
            }
            String finalBannerId = bannerId;
            FirebaseAnalyticsEvents.getInstance().logAdEvent(
                    "ad_request",
                    AdType.BANNER.name(),
                    bannerId
            );
            FirebaseAnalyticsEvents.getInstance().trackAdRequest(
                    activity,
                    AD_PLATFORM_ADMOB,
                    bannerId,
                    AdType.BANNER
            );
            this.loadBanner(activity, bannerId, adContainer, customSize, new AdCallback() {
                public void onAdFailedToLoad(@NonNull ApAdError i) {
                    super.onAdFailedToLoad(i);
                    Log.w("AdmobLogger", "loadAlternateBanner: fail-" + finalBannerId);
                    ids.remove(0);
                    if (!ids.isEmpty()) {
                        adContainer.setVisibility(View.VISIBLE);
                        loadAlternateBanner(activity, ids, adContainer, customSize);
                    }
                }

                public void onAdLoaded() {
                    super.onAdLoaded();
                    Log.i("AdmobLogger", "loadAlternateBanner: success-" + finalBannerId);
                }
            });
        }
    }


    private AdSize getAdSize(Activity mActivity) {
        DisplayMetrics displayMetrics = mActivity.getResources().getDisplayMetrics();
        int adWidth = (int) (displayMetrics.widthPixels / displayMetrics.density);
        return AdSize.getLargeAnchoredAdaptiveBannerAdSize(mActivity, adWidth);
    }

    public void loadAlternateNative(final Context context, List<String> idsInput, final AdCallback callback) {
        if(PurchaseManager.getInstance().isPurchased()) {
            callback.onAdFailedToLoad(new ApAdError(this.errAd));
            return;
        }
        final List<String> ids = deduplicateAdIds(idsInput);
        if (ids.isEmpty()) {
            callback.onAdFailedToLoad(new ApAdError(this.errAd));
            Log.d("AdmobLogger", "loadAlternateNative: empty");
            return;
        }

        final AtomicBoolean responded = new AtomicBoolean(false);
        Handler timeoutHandler = new Handler(Looper.getMainLooper());
        timeoutHandler.postDelayed(() -> {
            if (responded.compareAndSet(false, true)) {
                Log.w("AdmobLogger", "loadAlternateNative: TIMEOUT");
                callback.onAdFailedToLoad(new ApAdError(errAd));
            }
        }, AdConstants.AD_LOAD_TIMEOUT_MS);

        loadAlternateNativeInternal(context, new ArrayList<>(ids), responded, timeoutHandler, callback);
    }

    private void loadAlternateNativeInternal(final Context context, List<String> ids,
                                              AtomicBoolean responded, Handler timeoutHandler,
                                              final AdCallback callback) {
        String nativeAdId = ids.get(0);
        if (isUseTestAdIds()) {
            nativeAdId = AdConstants.TEST_NATIVE_AD_ID;
        }
        String finalNativeAdId = nativeAdId;
        FirebaseAnalyticsEvents.getInstance().logAdEvent("ad_request", AdType.NATIVE.name(), finalNativeAdId);

        this.loadUnifiedNativeAd(context, nativeAdId, new AdCallback() {
            public void onAdFailedToLoad(@NonNull ApAdError i) {
                super.onAdFailedToLoad(i);
                ids.remove(0);
                if (ids.isEmpty()) {
                    if (responded.compareAndSet(false, true)) {
                        timeoutHandler.removeCallbacksAndMessages(null);
                        callback.onAdFailedToLoad(i);
                    }
                } else {
                    loadAlternateNativeInternal(context, ids, responded, timeoutHandler, callback);
                }
            }

            public void onNativeAds(ApAdNative nativeAd) {
                super.onNativeAds(nativeAd);
                if (responded.compareAndSet(false, true)) {
                    timeoutHandler.removeCallbacksAndMessages(null);
                    callback.onNativeAds(nativeAd, finalNativeAdId);
                    Log.i("AdmobLogger", "loadAlternateNative: success-" + finalNativeAdId);
                } else {
                    nativeAd.getNativeAd().destroy();
                }
            }

            public void onAdClicked() {
                super.onAdClicked();
                if (callback != null) callback.onAdClicked();
                FirebaseAnalyticsEvents.getInstance().logClickAdsEvent(context, finalNativeAdId);
            }

            public void onAdImpression() {
                super.onAdImpression();
                FirebaseAnalyticsEvents.getInstance().logAdImpressionValue(
                        context, finalNativeAdId, AD_PLATFORM_ADMOB, AdType.NATIVE);
                if (callback != null) callback.onAdImpression();
            }
        });
    }




    public void loadUnifiedNativeAd(Context context, String id, final AdCallback callback) {
        if(PurchaseManager.getInstance().isPurchased()) {
            callback.onAdFailedToLoad(new ApAdError(this.errAd));
            return;
        }

        AdRequest request = this.getAdRequest();
        if (request == null) {
            callback.onAdFailedToLoad(new ApAdError(this.errAd));
        } else {
            FirebaseAnalyticsEvents.getInstance().trackAdRequest(
                    context,
                    AD_PLATFORM_ADMOB,
                    id,
                    AdType.NATIVE
            );
            VideoOptions videoOptions = (new VideoOptions.Builder()).setStartMuted(true).build();
            NativeAdOptions adOptions = (new NativeAdOptions.Builder())
                    .setVideoOptions(videoOptions)
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .setRequestCustomMuteThisAd(false)
                    .build();
            AdLoader adLoader = (new AdLoader.Builder(context, id)).forNativeAd((nativeAd) -> {
                if (callback != null) {
                    callback.onNativeAds(new ApAdNative(nativeAd));
                }
            }).withAdListener(new AdListener() {
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    super.onAdFailedToLoad(loadAdError);
                    if (callback != null) {
                        callback.onAdFailedToLoad(new ApAdError(loadAdError));
                    }

                }

                @Override
                public void onAdLoaded() {
                    super.onAdLoaded();
                }

                public void onAdImpression() {
                    super.onAdImpression();
                    FirebaseAnalyticsEvents.getInstance().logAdImpressionValue(
                        context,
                            id,
                            AD_PLATFORM_ADMOB,
                            AdType.NATIVE
                    );
                    callback.onAdImpression();
                }

                public void onAdClicked() {
                    super.onAdClicked();
                    callback.onAdClicked();
                    FirebaseAnalyticsEvents.getInstance().logClickAdsEvent(
                            context,
                            id
                    );
                }
            }).withNativeAdOptions(adOptions).build();
            adLoader.loadAd(request);
        }
    }

    public void showNative(Context context, AdsResponse<NativeAd> adsWithID, FrameLayout placeHolder, @LayoutRes int customLayoutId) {
        this.registerDialogBehaviorReceiver(context, placeHolder);
        View nativeView = LayoutInflater.from(context).inflate(customLayoutId, (ViewGroup)null);
        NativeAdView nativeAdView = new NativeAdView(context);
        nativeAdView.addView(nativeView);
        this.onBindAdView((NativeAd)adsWithID.getAds(), nativeAdView);
        placeHolder.removeAllViews();
        placeHolder.addView(nativeAdView);
    }




    private void registerDialogBehaviorReceiver(Context context, final FrameLayout placeHolder) {
        // Unregister previous receiver for this placeholder to prevent leaks
        BroadcastReceiver previousReceiver = registeredReceivers.remove(placeHolder);
        if (previousReceiver != null) {
            try {
                context.unregisterReceiver(previousReceiver);
            } catch (Exception ignored) {}
        }

        BroadcastReceiver receiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() != null && placeHolder != null) {
                    if (intent.getAction().equals("ACTION_CLOSE_NATIVE_ADS")) {
                        placeHolder.setVisibility(View.GONE);
                    } else if (intent.getAction().equals("ACTION_OPEN_NATIVE_ADS")) {
                        placeHolder.setVisibility(View.VISIBLE);
                    }
                }
            }
        };
        registeredReceivers.put(placeHolder, receiver);

        if (Build.VERSION.SDK_INT >= 26) {
            context.registerReceiver(receiver, this.initDialogBehaviorIntentFilter(), Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, this.initDialogBehaviorIntentFilter());
        }
    }

    private IntentFilter initDialogBehaviorIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("ACTION_CLOSE_NATIVE_ADS");
        intentFilter.addAction("ACTION_OPEN_NATIVE_ADS");
        return intentFilter;
    }

    private int getLayoutNative(NativeAdType type) {
        switch (type) {
            case BIG:
                return R.layout.custom_native_meta_273;

            case SMALL:
                return R.layout.custom_banner_native_meta_100;

            case MEDIUM:
                return R.layout.custom_banner_native_meta_120;

            case SMALLER:
                return R.layout.custom_banner_native_meta_50;

            case THEME_STYLE:
                return R.layout.custom_banner_native_meta_100_add_theme;

            case HOME_SMALLER:
                return R.layout.custom_banner_native_meta_50_home;

            case WELCOME:
                return R.layout.custom_native_welcome;

            case LANGUAGE:
                return R.layout.custom_native_meta_language;

            case FULLSCREEN:
                return R.layout.custom_full_screen_native_ads;

            default:
                return R.layout.custom_native_meta_273;
        }
    }

    private void onBindAdView(NativeAd nativeAd, NativeAdView adView) {
        Exception e;
        nativeAd.setOnPaidEventListener(new OnPaidEventListener() {
            @Override
            public void onPaidEvent(@NonNull AdValue adValue) {
                AdjustEvents.getInstance().pushTrackEventAdmob(adValue);
                FirebaseAnalyticsEvents.getInstance()
                        .logPaidAdImpression(adView.getContext(),
                                adValue,
                                "",
                                nativeAd.getResponseInfo(),
                                AdType.NATIVE
                        );
            }
        });
        try {
            adView.setMediaView((MediaView)adView.findViewById(R.id.ad_media));
        } catch (Exception var10) {
            e = var10;
            Log.e("AdsMobileAdsManager", "Error: " + e.getMessage());
        }

        try {
            adView.setHeadlineView(adView.findViewById(R.id.ad_headline));
            ((TextView)adView.getHeadlineView()).setText(nativeAd.getHeadline());
        } catch (Exception var9) {
            e = var9;
            Log.e("AdsMobileAdsManager", "Error: " + e.getMessage());
        }

        try {
            adView.setBodyView(adView.findViewById(R.id.ad_body));
            if (nativeAd.getBody() == null) {
                adView.getBodyView().setVisibility(View.INVISIBLE);
            } else {
                adView.getBodyView().setVisibility(View.VISIBLE);
                ((TextView)adView.getBodyView()).setText(nativeAd.getBody());
            }
        } catch (Exception var8) {
            e = var8;
            Log.e("AdsMobileAdsManager", "Error: " + e.getMessage());
        }

        try {
            adView.setCallToActionView(adView.findViewById(R.id.ad_call_to_action));
            if (nativeAd.getCallToAction() == null) {
                adView.getCallToActionView().setVisibility(View.INVISIBLE);
            } else {
                adView.getCallToActionView().setVisibility(View.VISIBLE);
                ((TextView)adView.getCallToActionView()).setText(nativeAd.getCallToAction());
            }
        } catch (Exception var7) {
            e = var7;
            Log.e("AdsMobileAdsManager", "Error: " + e.getMessage());
        }

        try {
            adView.setIconView(adView.findViewById(R.id.ad_app_icon));
            if (nativeAd.getIcon() == null) {
                adView.getIconView().setVisibility(View.GONE);
            } else {
                ((ImageView)adView.getIconView()).setImageDrawable(nativeAd.getIcon().getDrawable());
                adView.getIconView().setVisibility(View.VISIBLE);
            }
        } catch (Exception var6) {
            e = var6;
            Log.e("AdsMobileAdsManager", "Error: " + e.getMessage());
        }

        try {
            adView.setStarRatingView(adView.findViewById(R.id.ad_stars));
            if (nativeAd.getStarRating() == null) {
                adView.getStarRatingView().setVisibility(View.INVISIBLE);
            } else {
                ((RatingBar)adView.getStarRatingView()).setRating(nativeAd.getStarRating().floatValue());
                adView.getStarRatingView().setVisibility(View.VISIBLE);
            }
        } catch (Exception var5) {
            e = var5;
            Log.e("AdsMobileAdsManager", "Error: " + e.getMessage());
        }

        try {
            adView.setAdvertiserView(adView.findViewById(R.id.ad_advertiser));
            if (nativeAd.getAdvertiser() == null) {
                adView.getAdvertiserView().setVisibility(View.GONE);
            } else {
                ((TextView)adView.getAdvertiserView()).setText(nativeAd.getAdvertiser());
                adView.getAdvertiserView().setVisibility(View.VISIBLE);
            }
        } catch (Exception var4) {
            e = var4;
            Log.e("AdsMobileAdsManager", "Error: " + e.getMessage());
        }

        adView.setNativeAd(nativeAd);
    }

    public void initLoadAlternateRewardAd(Context context, List<String> idsInput, final RewardedAdLoadCallback adLoadCallback) {
        if (AppOpenAdManager.getInstance().isInitialized()) {
            AppOpenAdManager.getInstance().disableAppResume();
        }
        final AtomicBoolean responded = new AtomicBoolean(false);
        Handler timeoutHandler = new Handler(Looper.getMainLooper());
        timeoutHandler.postDelayed(() -> {
            if (responded.compareAndSet(false, true)) {
                Log.w("AdmobLogger", "initLoadAlternateRewardAd: TIMEOUT");
                if (AppOpenAdManager.getInstance().isInitialized()) {
                    AppOpenAdManager.getInstance().enableAppResume();
                }
                adLoadCallback.onAdFailedToLoad(errAd);
            }
        }, AdConstants.AD_LOAD_TIMEOUT_MS);

        loadAlternateRewardAd(context, idsInput, new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                if (responded.compareAndSet(false, true)) {
                    timeoutHandler.removeCallbacksAndMessages(null);
                    adLoadCallback.onAdLoaded(rewardedAd);
                }
            }
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                if (responded.compareAndSet(false, true)) {
                    timeoutHandler.removeCallbacksAndMessages(null);
                    adLoadCallback.onAdFailedToLoad(loadAdError);
                }
            }
        });
    }

        public void loadAlternateRewardAd(Context context, List<String > idsInput, final RewardedAdLoadCallback adLoadCallback) {
        final List<String> ids = deduplicateAdIds(idsInput);
        if (ids.isEmpty()) {
            if(context != null) {
                context.sendBroadcast(new Intent("action_dismiss_dialog"));
            }
            if (AppOpenAdManager.getInstance().isInitialized()) {
                AppOpenAdManager.getInstance().enableAppResume();
            }
            adLoadCallback.onAdFailedToLoad(this.errAd);
            Log.d("AdmobLogger", "loadAlternateReward: empty");
        } else {

            String rewardAdId = ids.get(0);
            if(isUseTestAdIds()) {
                rewardAdId = AdConstants.TEST_REWARD_AD_ID;
            }
            String finalRewardAdId = rewardAdId;
            this.loadRewardAd(context, finalRewardAdId, new RewardedAdLoadCallback() {
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    super.onAdFailedToLoad(loadAdError);
                    Log.w("AdmobLogger", "loadAlternateReward: fail-" + finalRewardAdId);
                    ids.remove(0);
                    if (ids.isEmpty()) {
                        if(context != null) {
                            context.sendBroadcast(new Intent("action_dismiss_dialog"));
                        }
                        if (AppOpenAdManager.getInstance().isInitialized()) {
                            AppOpenAdManager.getInstance().enableAppResume();
                        }
                        adLoadCallback.onAdFailedToLoad(loadAdError);
                    } else {
                        loadAlternateRewardAd(context, ids, adLoadCallback);
                    }
                }

                @Override
                public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                    super.onAdLoaded(rewardedAd);
                    FirebaseAnalyticsEvents.getInstance().trackAdMatchedRequest(
                            context,
                            AD_PLATFORM_ADMOB,
                            rewardedAd.getAdUnitId(),
                            AdType.REWARDED,
                            rewardedAd.getResponseInfo()
                    );
                    adLoadCallback.onAdLoaded(rewardedAd);
                }
            });
        }
    }
    public void loadRewardAd(Context context, String id, final RewardedAdLoadCallback adLoadCallback) {
        if (AppOpenAdManager.getInstance().isInitialized()) {
            AppOpenAdManager.getInstance().disableAppResume();
        }
        this.log("Request RewardAd :" + id);
        AdRequest request = this.getAdRequest();
        if (request == null) {
            if (AppOpenAdManager.getInstance().isInitialized()) {
                AppOpenAdManager.getInstance().enableAppResume();
            }
            adLoadCallback.onAdFailedToLoad(this.errAd);
        } else {
            FirebaseAnalyticsEvents.getInstance().trackAdRequest(
                    context,
                    AD_PLATFORM_ADMOB,
                    id,
                    AdType.REWARDED
            );
            FirebaseAnalyticsEvents.getInstance().logAdEvent(
                    "ad_request",
                    AdType.REWARDED.name(),
                    id
            );
            RewardedAd.load(context, id, request, new RewardedAdLoadCallback() {
                public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                    super.onAdLoaded(rewardedAd);
                    FirebaseAnalyticsEvents.getInstance().trackAdMatchedRequest(
                            context,
                            AD_PLATFORM_ADMOB,
                            id,
                            AdType.REWARDED,
                            rewardedAd.getResponseInfo()
                    );
                    adLoadCallback.onAdLoaded(rewardedAd);
                }

                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    super.onAdFailedToLoad(loadAdError);
                    if (AppOpenAdManager.getInstance().isInitialized()) {
                        AppOpenAdManager.getInstance().enableAppResume();
                    }
                    context.sendBroadcast(new Intent("action_dismiss_dialog"));
                    adLoadCallback.onAdFailedToLoad(loadAdError);

                }
            });
        }
    }

    public void showRewardAd(Activity activity, RewardedAd rewardedAd, final AdCallback callback) {
        if (this.isAdsEnabled() && rewardedAd != null && !PurchaseManager.getInstance().isPurchased()
                && activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
            this.log("Show RewardAd :" + rewardedAd.getAdUnitId());
            rewardedAd.setOnPaidEventListener(new OnPaidEventListener() {
                @Override
                public void onPaidEvent(@NonNull AdValue adValue) {
                    AdjustEvents.getInstance().pushTrackEventAdmob(adValue);
                    FirebaseAnalyticsEvents.getInstance()
                            .logPaidAdImpression(activity,
                                    adValue,
                                    rewardedAd.getAdUnitId(),
                                    rewardedAd.getResponseInfo(),
                                    AdType.REWARDED
                            );
                }
            });
            rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                public void onAdClicked() {
                    super.onAdClicked();
                    callback.onAdClicked();
                    FirebaseAnalyticsEvents.getInstance().logClickAdsEvent(
                            activity,
                            rewardedAd.getAdUnitId()
                    );
                }

                public void onAdShowedFullScreenContent() {
                    if (AppOpenAdManager.getInstance().isInitialized()) {
                        AppOpenAdManager.getInstance().disableAppResume();
                    }
                    super.onAdShowedFullScreenContent();
                    activity.sendBroadcast(new Intent("action_dismiss_dialog"));
                    callback.onAdShowedFullScreenContent();
                }

                public void onAdDismissedFullScreenContent() {

                    super.onAdDismissedFullScreenContent();
                    FirebaseAnalyticsEvents.getInstance().logAdEvent(
                            "ad_closed",
                            AdType.REWARDED.name(),
                            rewardedAd.getAdUnitId()
                    );
                    if (AppOpenAdManager.getInstance().isInitialized()) {
                        AppOpenAdManager.getInstance().enableAppResume();
                    }
                    activity.sendBroadcast(new Intent("action_dismiss_dialog"));
                    callback.onAdDismissedFullScreenContent();
                }

                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    if (AppOpenAdManager.getInstance().isInitialized()) {
                        AppOpenAdManager.getInstance().enableAppResume();
                    }
                    super.onAdFailedToShowFullScreenContent(adError);
                    activity.sendBroadcast(new Intent("action_dismiss_dialog"));
                    callback.onAdFailedToShowFullScreenContent(new ApAdError(errAd));
                    callback.onAdClosed();
                }
            });
            // show() MUST be called AFTER setFullScreenContentCallback to avoid missed events
            rewardedAd.show(activity, (rewardItem) -> {
                callback.onUserEarnedReward(new ApRewardItem(rewardItem));
            });
        } else {
            if (AppOpenAdManager.getInstance().isInitialized()) {
                AppOpenAdManager.getInstance().enableAppResume();
            }
            if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                activity.sendBroadcast(new Intent("action_dismiss_dialog"));
            }
            if (callback != null) {
                callback.onAdFailedToShowFullScreenContent(new ApAdError(this.errAd));
                callback.onAdClosed();
            }
            FirebaseAnalyticsEvents.getInstance().logAdShowFailed(
                    activity, rewardedAd != null ? rewardedAd.getAdUnitId() : "unknown",
                    AdType.REWARDED, "precondition_failed");
        }
    }

    @SuppressLint({"HardwareIds"})
    public String getDeviceId(Context context) {
        String android_id = Settings.Secure.getString(context.getContentResolver(), "android_id");
        return this.md5(android_id).toUpperCase();
    }

    private String md5(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte[] messageDigest = digest.digest();
            StringBuilder hexString = new StringBuilder();
            byte[] var5 = messageDigest;
            int var6 = messageDigest.length;

            for(int var7 = 0; var7 < var6; ++var7) {
                byte b = var5[var7];
                StringBuilder h = new StringBuilder(Integer.toHexString(255 & b));

                while(h.length() < 2) {
                    h.insert(0, "0");
                }

                hexString.append(h);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException var10) {
            return "";
        }
    }




    public AdRequest getAdCollapsibleBannerRequest() {
        if (this.isAdsEnabled() && !PurchaseManager.getInstance().isPurchased()) {
            Bundle extras = new Bundle();
            extras.putString("collapsible", "bottom");
            AdRequest.Builder adRequest = (AdRequest.Builder)(new AdRequest.Builder()).addNetworkExtrasBundle(AdMobAdapter.class, extras);
            return adRequest.build();
        } else {
            return null;
        }
    }

    public static enum NativeAdType {
        FULLSCREEN,
        BIG,
        MEDIUM,
        SMALL,
        SMALLER,
        WELCOME,
        LANGUAGE,
        HOME_SMALLER,
        THEME_STYLE;

        private NativeAdType() {
        }
    }
}
