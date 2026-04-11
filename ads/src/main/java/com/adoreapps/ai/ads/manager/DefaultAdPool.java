package com.adoreapps.ai.ads.manager;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
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
import com.adoreapps.ai.ads.settings.AdConstants;
import com.adoreapps.ai.ads.settings.AdSettingsStore;
import com.adoreapps.ai.ads.settings.AdsConfig;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Global fallback ad pool. Preloads up to {@link AdConstants#POOL_MAX_CACHE_SIZE} ads per type
 * at app startup. When position-specific ads fail, callers can pull from this pool.
 * After an ad is consumed, the pool auto-replenishes with retry backoff.
 */
public class DefaultAdPool {

    private static final String TAG = "DefaultAdPool";

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
    private final List<AdsResponse<NativeAd>> cachedNativeAds = new ArrayList<>();
    private final List<Long> nativeLoadTimes = new ArrayList<>();
    private volatile boolean nativeLoading;
    private int nativeRetryCount = 0;

    // =========================================================
    // INTERSTITIAL
    // =========================================================
    private final List<InterstitialAd> cachedInterstitialAds = new ArrayList<>();
    private final List<Long> interstitialLoadTimes = new ArrayList<>();
    private volatile boolean interstitialLoading;
    private int interstitialRetryCount = 0;

    // =========================================================
    // REWARD
    // =========================================================
    private final List<RewardedAd> cachedRewardAds = new ArrayList<>();
    private final List<Long> rewardLoadTimes = new ArrayList<>();
    private volatile boolean rewardLoading;
    private int rewardRetryCount = 0;

    private WeakReference<Activity> activityRef;
    private final Handler retryHandler = new Handler(Looper.getMainLooper());

    // =========================================================
    // INIT
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
        return loadTime == 0 || (System.currentTimeMillis() - loadTime) > AdConstants.AD_EXPIRY_MS;
    }

    private String getAdId(String defaultId) {
        return AdsMobileAdsManager.getInstance().isUseTestAdIds()
                ? AdConstants.TEST_NATIVE_AD_ID : defaultId;
    }

    private String getInterstitialTestId() {
        return AdsMobileAdsManager.getInstance().isUseTestAdIds()
                ? AdConstants.TEST_INTERSTITIAL_AD_ID : AdsConfig.interstitialDefaultId;
    }

    private String getRewardTestId() {
        return AdsMobileAdsManager.getInstance().isUseTestAdIds()
                ? AdConstants.TEST_REWARD_AD_ID : AdsConfig.rewardDefaultId;
    }

    @Nullable
    private Activity getActivity() {
        return activityRef != null ? activityRef.get() : null;
    }

    private long getRetryDelay(int retryCount) {
        return AdConstants.POOL_RETRY_BASE_DELAY_MS * (1L << Math.min(retryCount, AdConstants.POOL_MAX_RETRY_ATTEMPTS - 1));
    }

    // =========================================================
    // NATIVE — preload / get / replenish
    // =========================================================

    public synchronized void preloadNative(Activity activity) {
        if (nativeLoading) return;
        // Remove expired ads
        cleanExpired(cachedNativeAds, nativeLoadTimes);
        if (cachedNativeAds.size() >= AdConstants.POOL_MAX_CACHE_SIZE) return;
        if (!canLoad(activity)) return;

        nativeLoading = true;
        String adId = getAdId(AdsConfig.nativeDefaultId);
        Log.d(TAG, "Preloading default native (" + (cachedNativeAds.size() + 1) + "/" + AdConstants.POOL_MAX_CACHE_SIZE + "): " + adId);

        AdsMobileAdsManager.getInstance().loadUnifiedNativeAd(activity, adId, new AdCallback() {
            @Override
            public void onNativeAds(ApAdNative nativeAd) {
                super.onNativeAds(nativeAd);
                synchronized (DefaultAdPool.this) {
                    cachedNativeAds.add(new AdsResponse<>(nativeAd.getNativeAd(), adId));
                    nativeLoadTimes.add(System.currentTimeMillis());
                    nativeLoading = false;
                    nativeRetryCount = 0;
                    Log.d(TAG, "Default native preloaded (" + cachedNativeAds.size() + "/" + AdConstants.POOL_MAX_CACHE_SIZE + ")");
                }
                // Fill up to max
                if (cachedNativeAds.size() < AdConstants.POOL_MAX_CACHE_SIZE) {
                    preloadNative(activity);
                }
            }

            @Override
            public void onAdFailedToLoad(@NonNull ApAdError error) {
                super.onAdFailedToLoad(error);
                synchronized (DefaultAdPool.this) {
                    nativeLoading = false;
                }
                scheduleRetry(() -> {
                    Activity a = getActivity();
                    if (a != null) preloadNative(a);
                }, nativeRetryCount++);
            }
        });
    }

    public synchronized boolean hasDefaultNative() {
        cleanExpired(cachedNativeAds, nativeLoadTimes);
        if (cachedNativeAds.isEmpty()) return false;
        Activity activity = getActivity();
        if (activity == null) return false;
        return AdSettingsStore.getInstance(activity).getBoolean("preload_default_native", true);
    }

    /**
     * Returns the cached default native ad WITHOUT consuming it.
     * The same ad can be shared across multiple failed placements.
     * A background replacement load is triggered to keep the pool fresh.
     *
     * This is the Funswap optimization: shared native ads reduce request volume
     * while maintaining fill rate across all positions.
     */
    @Nullable
    public synchronized AdsResponse<NativeAd> getDefaultNativeAd(Activity activity) {
        if (!canServe(activity)) return null;
        if (!AdSettingsStore.getInstance(activity).getBoolean("preload_default_native", true)) return null;
        cleanExpired(cachedNativeAds, nativeLoadTimes);

        if (cachedNativeAds.isEmpty()) {
            preloadNative(activity);
            return null;
        }

        AdsResponse<NativeAd> ad = cachedNativeAds.get(0);
        Log.d(TAG, "Default native shared (not consumed), pool size: " + cachedNativeAds.size());

        // Load a replacement in background to keep pool fresh
        loadReplacementNative(activity);
        return ad;
    }

    /**
     * Consumes and removes the default native ad from the pool.
     * Use this only when the ad must be exclusively owned (e.g., full-screen native).
     * For most cases, prefer {@link #getDefaultNativeAd(Activity)} (shared/non-consuming).
     */
    @Nullable
    public synchronized AdsResponse<NativeAd> consumeDefaultNativeAd(Activity activity) {
        if (!canServe(activity)) return null;
        if (!AdSettingsStore.getInstance(activity).getBoolean("preload_default_native", true)) return null;
        cleanExpired(cachedNativeAds, nativeLoadTimes);

        if (cachedNativeAds.isEmpty()) {
            preloadNative(activity);
            return null;
        }

        AdsResponse<NativeAd> ad = cachedNativeAds.remove(0);
        nativeLoadTimes.remove(0);
        preloadNative(activity);
        Log.d(TAG, "Default native consumed, remaining: " + cachedNativeAds.size());
        return ad;
    }

    /**
     * Load a replacement native ad in background without blocking.
     * Called after sharing a native ad to keep the pool fresh for next use.
     */
    private void loadReplacementNative(Activity activity) {
        if (nativeLoading) return;
        if (!canLoad(activity)) return;
        preloadNative(activity);
    }

    // =========================================================
    // INTERSTITIAL — preload / get / replenish
    // =========================================================

    public synchronized void preloadInterstitial(Activity activity) {
        if (interstitialLoading) return;
        cleanExpiredSimple(cachedInterstitialAds, interstitialLoadTimes);
        if (cachedInterstitialAds.size() >= AdConstants.POOL_MAX_CACHE_SIZE) return;
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
                synchronized (DefaultAdPool.this) {
                    cachedInterstitialAds.add(interstitialAd.getInterstitialAd());
                    interstitialLoadTimes.add(System.currentTimeMillis());
                    interstitialLoading = false;
                    interstitialRetryCount = 0;
                    Log.d(TAG, "Default interstitial preloaded (" + cachedInterstitialAds.size() + "/" + AdConstants.POOL_MAX_CACHE_SIZE + ")");
                }
                if (cachedInterstitialAds.size() < AdConstants.POOL_MAX_CACHE_SIZE) {
                    preloadInterstitial(activity);
                }
            }

            @Override
            public void onAdFailedToLoad(@NonNull ApAdError error) {
                super.onAdFailedToLoad(error);
                synchronized (DefaultAdPool.this) {
                    interstitialLoading = false;
                }
                scheduleRetry(() -> {
                    Activity a = getActivity();
                    if (a != null) preloadInterstitial(a);
                }, interstitialRetryCount++);
            }

            @Override
            public void onNextScreen() {
                super.onNextScreen();
                synchronized (DefaultAdPool.this) {
                    interstitialLoading = false;
                }
            }
        });
    }

    public synchronized boolean hasDefaultInterstitial() {
        cleanExpiredSimple(cachedInterstitialAds, interstitialLoadTimes);
        if (cachedInterstitialAds.isEmpty()) return false;
        Activity activity = getActivity();
        if (activity == null) return false;
        return AdSettingsStore.getInstance(activity).getBoolean("preload_default_interstitial", true);
    }

    @Nullable
    public synchronized InterstitialAd consumeDefaultInterstitialAd(Activity activity) {
        if (!canServe(activity)) return null;
        if (!AdSettingsStore.getInstance(activity).getBoolean("preload_default_interstitial", true)) return null;
        cleanExpiredSimple(cachedInterstitialAds, interstitialLoadTimes);

        if (cachedInterstitialAds.isEmpty()) {
            preloadInterstitial(activity);
            return null;
        }

        InterstitialAd ad = cachedInterstitialAds.remove(0);
        interstitialLoadTimes.remove(0);
        preloadInterstitial(activity);
        Log.d(TAG, "Default interstitial consumed, remaining: " + cachedInterstitialAds.size());
        return ad;
    }

    // =========================================================
    // REWARD — preload / get / replenish
    // =========================================================

    public synchronized void preloadReward(Activity activity) {
        if (rewardLoading) return;
        cleanExpiredSimple(cachedRewardAds, rewardLoadTimes);
        if (cachedRewardAds.size() >= AdConstants.POOL_MAX_CACHE_SIZE) return;
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
                synchronized (DefaultAdPool.this) {
                    cachedRewardAds.add(rewardedAd);
                    rewardLoadTimes.add(System.currentTimeMillis());
                    rewardLoading = false;
                    rewardRetryCount = 0;
                    Log.d(TAG, "Default reward preloaded (" + cachedRewardAds.size() + "/" + AdConstants.POOL_MAX_CACHE_SIZE + ")");
                }
                if (cachedRewardAds.size() < AdConstants.POOL_MAX_CACHE_SIZE) {
                    preloadReward(activity);
                }
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                super.onAdFailedToLoad(loadAdError);
                synchronized (DefaultAdPool.this) {
                    rewardLoading = false;
                }
                scheduleRetry(() -> {
                    Activity a = getActivity();
                    if (a != null) preloadReward(a);
                }, rewardRetryCount++);
            }
        });
    }

    public synchronized boolean hasDefaultReward() {
        cleanExpiredSimple(cachedRewardAds, rewardLoadTimes);
        if (cachedRewardAds.isEmpty()) return false;
        Activity activity = getActivity();
        if (activity == null) return false;
        return AdSettingsStore.getInstance(activity).getBoolean("preload_default_reward", true);
    }

    @Nullable
    public synchronized RewardedAd consumeDefaultRewardAd(Activity activity) {
        if (!canServe(activity)) return null;
        if (!AdSettingsStore.getInstance(activity).getBoolean("preload_default_reward", true)) return null;
        cleanExpiredSimple(cachedRewardAds, rewardLoadTimes);

        if (cachedRewardAds.isEmpty()) {
            preloadReward(activity);
            return null;
        }

        RewardedAd ad = cachedRewardAds.remove(0);
        rewardLoadTimes.remove(0);
        preloadReward(activity);
        Log.d(TAG, "Default reward consumed, remaining: " + cachedRewardAds.size());
        return ad;
    }

    // =========================================================
    // RETRY & CLEANUP HELPERS
    // =========================================================

    private void scheduleRetry(Runnable retryAction, int retryCount) {
        if (retryCount >= AdConstants.POOL_MAX_RETRY_ATTEMPTS) {
            Log.w(TAG, "Max retry attempts reached, giving up");
            return;
        }
        long delay = getRetryDelay(retryCount);
        Log.d(TAG, "Scheduling retry in " + (delay / 1000) + "s (attempt " + (retryCount + 1) + ")");
        retryHandler.postDelayed(retryAction, delay);
    }

    private void cleanExpired(List<AdsResponse<NativeAd>> ads, List<Long> times) {
        for (int i = ads.size() - 1; i >= 0; i--) {
            if (isExpired(times.get(i))) {
                ads.get(i).getAds().destroy();
                ads.remove(i);
                times.remove(i);
            }
        }
    }

    private <T> void cleanExpiredSimple(List<T> ads, List<Long> times) {
        for (int i = ads.size() - 1; i >= 0; i--) {
            if (isExpired(times.get(i))) {
                ads.remove(i);
                times.remove(i);
            }
        }
    }
}
