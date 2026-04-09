package com.adoreapps.ai.ads.manager;

import com.adoreapps.ai.ads.settings.AdConstants;
import com.adoreapps.ai.ads.settings.AdSettingsStore;
import com.adoreapps.ai.ads.settings.AdsConfig;

import com.adoreapps.ai.ads.core.AdsMobileAdsManager;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.adoreapps.ai.ads.AdCallback;
import com.adoreapps.ai.ads.core.AdsMobileAdsManager;
import com.adoreapps.ai.ads.core.NetworkUtils;
import com.adoreapps.ai.ads.model.AdsResponse;
import com.adoreapps.ai.ads.wrapper.ApAdError;
import com.adoreapps.ai.ads.wrapper.ApAdNative;
import com.adoreapps.ai.ads.wrapper.ApInterstitialAd;
import com.adoreapps.ai.ads.consent.ConsentManager;
import com.adoreapps.ai.ads.billing.PurchaseManager;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Global fallback ad pool. Preloads one default ad per type (native, interstitial, reward)
 * at app startup. When position-specific ads fail, callers can pull from this pool.
 * After an ad is consumed, the pool auto-replenishes.
 */
public class DefaultAdPool {

    private static final String TAG = "DefaultAdPool";
    private static final long AD_EXPIRY_MS = AdConstants.AD_EXPIRY_MS;

    private static volatile DefaultAdPool instance;

    private DefaultAdPool() {}

    public static DefaultAdPool getInstance() {
        if (instance == null) {
            synchronized (DefaultAdPool.class) {
                if (instance == null) {
                    instance = new DefaultAdPool();
                }
            }
        }
        return instance;
    }

    // =========================================================
    // NATIVE
    // =========================================================
    private volatile AdsResponse<NativeAd> cachedNativeAd;
    private volatile long nativeLoadTime;
    private volatile boolean nativeLoading;

    // =========================================================
    // INTERSTITIAL
    // =========================================================
    private volatile InterstitialAd cachedInterstitialAd;
    private volatile long interstitialLoadTime;
    private volatile boolean interstitialLoading;

    // =========================================================
    // REWARD
    // =========================================================
    private volatile RewardedAd cachedRewardAd;
    private volatile long rewardLoadTime;
    private volatile boolean rewardLoading;

    // Keep a weak ref to activity for re-preloading
    private WeakReference<Activity> activityRef;

    // =========================================================
    // INIT — call once from SplashScreenActivity
    // =========================================================

    public void init(Activity activity) {
        if (PurchaseManager.getInstance().isPurchased()) return;
        activityRef = new WeakReference<>(activity);
        AdSettingsStore settings = AdSettingsStore.getInstance(activity);
        if (settings.getBoolean("preload_default_native", true)) {
            preloadNative(activity);
        }
        if (settings.getBoolean("preload_default_interstitial", true)) {
            preloadInterstitial(activity);
        }
        if (settings.getBoolean("preload_default_reward", true)) {
            preloadReward(activity);
        }
    }

    // =========================================================
    // VALIDATION
    // =========================================================

    private boolean canServe(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return false;
        if (PurchaseManager.getInstance().isPurchased()) return false;
        if (!AdSettingsStore.getInstance(activity).getBoolean("enable_all_ads", true)) return false;
        if (!ConsentManager.getInstance(activity).canRequestAds()) return false;
        return true;
    }

    private boolean canLoad(Activity activity) {
        return canServe(activity) && NetworkUtils.isNetworkAvailable(activity);
    }

    private boolean isExpired(long loadTime) {
        return loadTime == 0 || (System.currentTimeMillis() - loadTime) > AD_EXPIRY_MS;
    }

    private String getAdId(String defaultId) {
        if (AdsMobileAdsManager.getInstance().isUseTestAdIds()) {
            return AdConstants.TEST_NATIVE_AD_ID;
        }
        return defaultId;
    }

    private String getInterstitialTestId() {
        return AdsMobileAdsManager.getInstance().isUseTestAdIds()
                ? AdConstants.TEST_INTERSTITIAL_AD_ID
                : AdsConfig.interstitialDefaultId;
    }

    private String getRewardTestId() {
        return AdsMobileAdsManager.getInstance().isUseTestAdIds()
                ? AdConstants.TEST_REWARD_AD_ID
                : AdsConfig.rewardDefaultId;
    }

    @Nullable
    private Activity getActivity() {
        return activityRef != null ? activityRef.get() : null;
    }

    // =========================================================
    // NATIVE — preload / get / replenish
    // =========================================================

    public void preloadNative(Activity activity) {
        if (nativeLoading || (cachedNativeAd != null && !isExpired(nativeLoadTime))) return;
        if (!canLoad(activity)) return;

        nativeLoading = true;
        String adId = getAdId(AdsConfig.nativeDefaultId);
        Log.d(TAG, "Preloading default native: " + adId);

        AdsMobileAdsManager.getInstance().loadUnifiedNativeAd(activity, adId, new AdCallback() {
            @Override
            public void onNativeAds(ApAdNative nativeAd) {
                super.onNativeAds(nativeAd);
                cachedNativeAd = new AdsResponse<>(nativeAd.getNativeAd(), adId);
                nativeLoadTime = System.currentTimeMillis();
                nativeLoading = false;
                Log.d(TAG, "Default native preloaded");
            }

            @Override
            public void onAdFailedToLoad(@NonNull ApAdError error) {
                super.onAdFailedToLoad(error);
                nativeLoading = false;
                Log.w(TAG, "Default native preload failed");
            }
        });
    }

    public boolean hasDefaultNative() {
        if (cachedNativeAd == null || isExpired(nativeLoadTime)) return false;
        Activity activity = getActivity();
        if (activity == null) return false;
        return AdSettingsStore.getInstance(activity).getBoolean("preload_default_native", true);
    }

    /**
     * Returns the cached default native ad and clears the pool.
     * Triggers auto-replenish in background.
     * Caller must validate activity with canServe() before showing.
     */
    @Nullable
    public AdsResponse<NativeAd> consumeDefaultNativeAd(Activity activity) {
        if (!canServe(activity)) return null;
        if (!AdSettingsStore.getInstance(activity).getBoolean("preload_default_native", true)) return null;
        if (cachedNativeAd == null || isExpired(nativeLoadTime)) {
            // Expired — destroy and replenish
            if (cachedNativeAd != null) {
                cachedNativeAd.getAds().destroy();
                cachedNativeAd = null;
            }
            preloadNative(activity);
            return null;
        }

        AdsResponse<NativeAd> ad = cachedNativeAd;
        cachedNativeAd = null;
        nativeLoadTime = 0;

        // Auto-replenish
        preloadNative(activity);
        Log.d(TAG, "Default native consumed, replenishing");
        return ad;
    }

    // =========================================================
    // INTERSTITIAL — preload / get / replenish
    // =========================================================

    public void preloadInterstitial(Activity activity) {
        if (interstitialLoading || (cachedInterstitialAd != null && !isExpired(interstitialLoadTime))) return;
        if (!canLoad(activity)) return;

        interstitialLoading = true;
        String adId = getInterstitialTestId();
        Log.d(TAG, "Preloading default interstitial: " + adId);

        List<String> ids = new ArrayList<>();
        ids.add(adId);
        AdsMobileAdsManager.getInstance().loadAlternateInter(activity, ids, new AdCallback() {
            @Override
            public void onResultInterstitialAd(ApInterstitialAd interstitialAd) {
                super.onResultInterstitialAd(interstitialAd);
                cachedInterstitialAd = interstitialAd.getInterstitialAd();
                interstitialLoadTime = System.currentTimeMillis();
                interstitialLoading = false;
                Log.d(TAG, "Default interstitial preloaded");
            }

            @Override
            public void onAdFailedToLoad(@NonNull ApAdError error) {
                super.onAdFailedToLoad(error);
                interstitialLoading = false;
                Log.w(TAG, "Default interstitial preload failed");
            }

            @Override
            public void onNextScreen() {
                super.onNextScreen();
                interstitialLoading = false;
            }
        });
    }

    public boolean hasDefaultInterstitial() {
        if (cachedInterstitialAd == null || isExpired(interstitialLoadTime)) return false;
        Activity activity = getActivity();
        if (activity == null) return false;
        return AdSettingsStore.getInstance(activity).getBoolean("preload_default_interstitial", true);
    }

    @Nullable
    public InterstitialAd consumeDefaultInterstitialAd(Activity activity) {
        if (!canServe(activity)) return null;
        if (!AdSettingsStore.getInstance(activity).getBoolean("preload_default_interstitial", true)) return null;
        if (cachedInterstitialAd == null || isExpired(interstitialLoadTime)) {
            cachedInterstitialAd = null;
            preloadInterstitial(activity);
            return null;
        }

        InterstitialAd ad = cachedInterstitialAd;
        cachedInterstitialAd = null;
        interstitialLoadTime = 0;

        // Auto-replenish
        preloadInterstitial(activity);
        Log.d(TAG, "Default interstitial consumed, replenishing");
        return ad;
    }

    // =========================================================
    // REWARD — preload / get / replenish
    // =========================================================

    public void preloadReward(Activity activity) {
        if (rewardLoading || (cachedRewardAd != null && !isExpired(rewardLoadTime))) return;
        if (!canLoad(activity)) return;

        rewardLoading = true;
        String adId = getRewardTestId();
        Log.d(TAG, "Preloading default reward: " + adId);

        List<String> ids = new ArrayList<>();
        ids.add(adId);
        AdsMobileAdsManager.getInstance().loadAlternateRewardAd(activity, ids, new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                super.onAdLoaded(rewardedAd);
                cachedRewardAd = rewardedAd;
                rewardLoadTime = System.currentTimeMillis();
                rewardLoading = false;
                Log.d(TAG, "Default reward preloaded");
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                super.onAdFailedToLoad(loadAdError);
                rewardLoading = false;
                Log.w(TAG, "Default reward preload failed");
            }
        });
    }

    public boolean hasDefaultReward() {
        if (cachedRewardAd == null || isExpired(rewardLoadTime)) return false;
        Activity activity = getActivity();
        if (activity == null) return false;
        return AdSettingsStore.getInstance(activity).getBoolean("preload_default_reward", true);
    }

    @Nullable
    public RewardedAd consumeDefaultRewardAd(Activity activity) {
        if (!canServe(activity)) return null;
        if (!AdSettingsStore.getInstance(activity).getBoolean("preload_default_reward", true)) return null;
        if (cachedRewardAd == null || isExpired(rewardLoadTime)) {
            cachedRewardAd = null;
            preloadReward(activity);
            return null;
        }

        RewardedAd ad = cachedRewardAd;
        cachedRewardAd = null;
        rewardLoadTime = 0;

        // Auto-replenish
        preloadReward(activity);
        Log.d(TAG, "Default reward consumed, replenishing");
        return ad;
    }
}
