package com.adoreapps.ai.ads.core;

import com.adoreapps.ai.ads.billing.PurchaseManager;

import static com.adoreapps.ai.ads.event.FirebaseAnalyticsEvents.AD_PLATFORM_ADMOB;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.lifecycle.Lifecycle.State;

import com.adoreapps.ai.ads.dialog.WelcomeBackDialog;
import com.adoreapps.ai.ads.event.AdType;
import com.adoreapps.ai.ads.event.AdjustEvents;
import com.adoreapps.ai.ads.event.FirebaseAnalyticsEvents;
import com.adoreapps.ai.ads.interfaces.OpenAdsLoadCallback;
import com.google.android.gms.ads.AdActivity;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdValue;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnPaidEventListener;
import com.google.android.gms.ads.appopen.AppOpenAd;
import java.util.ArrayList;
import java.util.List;

public class AppOpenAdManager implements Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
    private static final String TAG = "AppOpenManager";
    @SuppressLint({"StaticFieldLeak"})
    private static volatile AppOpenAdManager INSTANCE;
    private AppOpenAd appResumeAd = null;
    public String openAppID;
    private List<String> openAppAdIds = new ArrayList<>();
    private Activity currentActivity;
    private Application myApplication;
    private boolean isShowingAd = false;
    private boolean isInitialized = false;
    private boolean isAppResumeEnabled = true;
    private long loadTime;
    private WelcomeBackDialog dialog;
    private long timeShowLoading = 100L;
    private final List<Class<?>> disableLoadActivities = new ArrayList<>();
    public String ACTION_SHOW_UPDATE_DIALOG = "ACTION_SHOW_UPDATE_DIALOG";
    private final String ACTION_DISMISS_NATIVE = "ACTION_DISMISS_NATIVE";
    private final String ACTION_SHOW_NATIVE = "ACTION_SHOW_NATIVE";
    private boolean isLoading = false;

    private AppOpenAdManager() {
    }

    public static synchronized AppOpenAdManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AppOpenAdManager();
        }

        return INSTANCE;
    }

    public void init(Application application, String appOpenID) {
        List<String> ids = new ArrayList<>();
        ids.add(appOpenID);
        init(application, ids);
    }

    public void init(Application application, List<String> appOpenAdIds) {
        this.isInitialized = true;
        this.myApplication = application;
        this.myApplication.registerActivityLifecycleCallbacks(this);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);

        // Auto-disable app open ads on full-screen ad activities
        disableLoadAtActivity(com.adoreapps.ai.ads.dialog.FullScreenNativeAdActivity.class);
        if(AdsMobileAdsManager.getInstance().isUseTestAdIds()) {
            this.openAppAdIds = new ArrayList<>();
            this.openAppAdIds.add(com.adoreapps.ai.ads.settings.AdConstants.TEST_APP_OPEN_AD_ID);
            this.openAppID = com.adoreapps.ai.ads.settings.AdConstants.TEST_APP_OPEN_AD_ID;
        }
        else {
            this.openAppAdIds = new ArrayList<>(appOpenAdIds);
            this.openAppID = this.openAppAdIds.isEmpty() ? "" : this.openAppAdIds.get(0);
        }
    }

    public boolean isInitialized() {
        return this.isInitialized;
    }

    public void disableLoadAtActivity(Class<?> activityClass) {
        Log.d("AppOpenManager", "disableAppResumeWithActivity: " + activityClass.getName());
        this.disableLoadActivities.add(activityClass);
    }

    public void enableLoadAtActivities(Class<?> activityClass) {
        Log.d("AppOpenManager", "enableAppResumeWithActivity: " + activityClass.getName());
        this.disableLoadActivities.remove(activityClass);
    }

    public void disableAppResume() {
        this.isAppResumeEnabled = false;
    }

    public void enableAppResume() {
        this.isAppResumeEnabled = true;
    }

    public void fetchAd() {
        if(PurchaseManager.getInstance().isPurchased()) {
            return;
        }
        if (this.currentActivity == null) {
            return;
        }
        // Do not fetch ads without user consent
        try {
            if (!com.adoreapps.ai.ads.consent.ConsentManager.getInstance(this.myApplication).canRequestAds()) {
                Log.d(TAG, "Skipping app open ad fetch - no user consent");
                return;
            }
        } catch (Exception ignored) {}
        for (Class<?> activity : this.disableLoadActivities) {
            if (activity.getName().equals(this.currentActivity.getClass().getName())) {
                Log.d("AppOpenManager", "onStart: activity is disabled");
                return;
            }
        }

        if (!this.isAdAvailable() && !this.isLoading && !this.openAppAdIds.isEmpty()) {
            this.isLoading = true;
            loadOpenAppWithWaterfall(new ArrayList<>(this.openAppAdIds));
        }
    }

    private void loadOpenAppWithWaterfall(List<String> remainingIds) {
        if (remainingIds.isEmpty()) {
            this.isLoading = false;
            return;
        }
        String adId = remainingIds.get(0);
        FirebaseAnalyticsEvents.getInstance().trackAdRequest(
                myApplication,
                AD_PLATFORM_ADMOB,
                adId,
                AdType.APP_OPEN
        );
        this.loadOpenApp(adId, new OpenAdsLoadCallback() {
            public void onAdsLoaded(AppOpenAd ad) {
                AppOpenAdManager.this.appResumeAd = ad;
                AppOpenAdManager.this.openAppID = adId;
                AppOpenAdManager.this.isLoading = false;
            }

            public void onFail(LoadAdError error) {
                remainingIds.remove(0);
                if (remainingIds.isEmpty()) {
                    AppOpenAdManager.this.isLoading = false;
                } else {
                    loadOpenAppWithWaterfall(remainingIds);
                }
            }
        });
    }

    private void loadOpenApp(String adsID, final OpenAdsLoadCallback callback) {
        if(PurchaseManager.getInstance().isPurchased()) {
            return;
        }
        AdRequest request = AdsMobileAdsManager.getInstance().getAdRequest();
        if (request != null) {
            AppOpenAd.AppOpenAdLoadCallback loadCallback = new AppOpenAd.AppOpenAdLoadCallback() {
                public void onAdLoaded(@NonNull AppOpenAd ad) {

                    callback.onAdsLoaded(ad);
                }

                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    callback.onFail(loadAdError);
                }
            };
            AdsMobileAdsManager.getInstance().log("Request OpenAd :" + adsID);
            AppOpenAd.load(this.myApplication, adsID, request, loadCallback);
        }
    }

    public boolean isAdAvailable() {
        return this.appResumeAd != null;
    }

    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    public void onActivityStarted(Activity activity) {
        this.currentActivity = activity;
    }

    public void onActivityResumed(Activity activity) {
        this.currentActivity = activity;
        this.fetchAd();
    }

    public void onActivityStopped(Activity activity) {
    }

    public void onActivityPaused(Activity activity) {
    }

    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
    }

    public void onActivityDestroyed(Activity activity) {
        this.currentActivity = null;
    }

    private void dismissDialogLoading() {
        try {
            this.dialog.dismiss();
        } catch (Exception var2) {
            Exception e = var2;
            Log.e(TAG, "AppOpenAd error: " + e.getMessage());
        }

    }

    private void showAdsWithLoading(FullScreenContentCallback fullScreenContentCallback) {
        if(PurchaseManager.getInstance().isPurchased()) {
            return;
        }
        if (!this.isShowingAd && this.appResumeAd != null) {
            if (ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(State.STARTED)) {
                try {
                    if (AdsMobileAdsManager.getInstance().isShowLoadingDialog()) {
                        this.dismissDialogLoading();
                        this.dialog = new WelcomeBackDialog(this.currentActivity);
                        this.dialog.show();
                    } else {
                        this.timeShowLoading = 0L;
                    }
                } catch (Exception var3) {
                    Exception e = var3;
                    this.dialog = null;
                    Log.e(TAG, "AppOpenAd error: " + e.getMessage());
                }

                Dialog finalDialog = this.dialog;
                if (fullScreenContentCallback != null) {
                    this.appResumeAd.setFullScreenContentCallback(fullScreenContentCallback);
                }

                (new Handler(android.os.Looper.getMainLooper())).postDelayed(() -> {
                    if (!AdsMobileAdsManager.getInstance().isShowLoadingDialog() || this.dialog != null && this.dialog.isShowing()) {
                        AdsMobileAdsManager.getInstance().log("Show OpenAd :" + this.openAppID);
                        this.appResumeAd.setOnPaidEventListener(new OnPaidEventListener() {
                            @Override
                            public void onPaidEvent(@NonNull AdValue adValue) {
                                AdjustEvents.getInstance().pushTrackEventAdmob(adValue);
                                if(appResumeAd != null) {
                                    FirebaseAnalyticsEvents.getInstance()
                                            .logPaidAdImpression(currentActivity,
                                                    adValue,
                                                    appResumeAd.getAdUnitId(),
                                                    appResumeAd.getResponseInfo(),
                                                    AdType.APP_OPEN
                                            );
                                }
                            }
                        });
                        this.appResumeAd.show(this.currentActivity);
                        this.appResumeAd = null;
                    }

                }, this.timeShowLoading);
            }

        }
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        if(PurchaseManager.getInstance().isPurchased()) {
            return;
        }
        if (!this.isAppResumeEnabled) {
            Log.d("AppOpenManager", "onResume: app resume is disabled");
        } else {
            (new Handler(android.os.Looper.getMainLooper())).postDelayed(new Runnable() {
                public void run() {
                    if (AppOpenAdManager.this.currentActivity == null) {
                        return;
                    }

                    for (Class<?> activity : AppOpenAdManager.this.disableLoadActivities) {
                        if (activity.getName().equals(AppOpenAdManager.this.currentActivity.getClass().getName())) {
                            Log.d("AppOpenManager", "onStart: activity is disabled");
                            return;
                        }
                    }

                    if (!AppOpenAdManager.this.currentActivity.getClass().getName().equals(AdActivity.class.getName())) {
                        AppOpenAdManager.this.showAdIfAvailable();
                    }
                }
            }, 300L);
        }
    }

    public void showAdIfAvailable() {
        if (PurchaseManager.getInstance().isPurchased()) {
            return;
        }
        if (this.currentActivity == null) {
            Log.d("AppOpenManager", "showAdIfAvailable: currentActivity is null");
            return;
        }
        if (!AdsMobileAdsManager.getInstance().isAdsEnabled()) {
            Log.d("AppOpenManager", "showAdIfAvailable: ads disabled");
            return;
        }
        if (!ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(State.STARTED)) {
            Log.d("AppOpenManager", "showAdIfAvailable: app not in foreground");
            return;
        }
        if (!this.isAdAvailable()) {
            Log.d("AppOpenManager", "showAdIfAvailable: no ad available, fetching");
            this.fetchAd();
            return;
        }

        Log.d("AppOpenManager", "Will show app open ad");
        FullScreenContentCallback fullScreenContentCallback = new FullScreenContentCallback() {
            public void onAdDismissedFullScreenContent() {
                AppOpenAdManager.this.appResumeAd = null;
                AppOpenAdManager.this.isShowingAd = false;
                AppOpenAdManager.this.dismissDialogLoading();
                AppOpenAdManager.this.myApplication.sendBroadcast(new Intent("ACTION_SHOW_NATIVE"));
            }

            public void onAdFailedToShowFullScreenContent(AdError adError) {
                AppOpenAdManager.this.dismissDialogLoading();
                AppOpenAdManager.this.appResumeAd = null;
                AppOpenAdManager.this.myApplication.sendBroadcast(new Intent("ACTION_SHOW_NATIVE"));
            }

            public void onAdShowedFullScreenContent() {
                AppOpenAdManager.this.isShowingAd = true;
            }

            @Override
            public void onAdClicked() {
                super.onAdClicked();
                FirebaseAnalyticsEvents.getInstance().logClickAdsEvent(
                        currentActivity,
                        openAppID
                );
            }
        };
        this.showAdsWithLoading(fullScreenContentCallback);
    }
}
