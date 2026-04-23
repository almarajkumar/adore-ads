package com.adoreapps.ai.ads.manager;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.adoreapps.ai.ads.AdCallback;
import com.adoreapps.ai.ads.core.AdsMobileAdsManager;
import com.adoreapps.ai.ads.settings.AdConstants;
import com.adoreapps.ai.ads.wrapper.ApAdError;
import com.adoreapps.ai.ads.wrapper.ApInterstitialAd;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

/**
 * Manages ad preloading for interstitial, rewarded, and app open formats.
 *
 * <h3>Strategy:</h3>
 * <ol>
 *   <li>If AdMob's native Preloader API is available (SDK 24.4+ with account access),
 *       use it — it handles auto-reload, retries, and expiration automatically.</li>
 *   <li>Otherwise, fall back to a manual preload queue per placement. Load one ad up to
 *       {@code bufferSize}, poll to consume, replenish on poll.</li>
 * </ol>
 *
 * <h3>Usage:</h3>
 * <pre>
 * AdPreloadManager.getInstance().startInterstitial(context, "INTER_SAVE", "ca-app-pub-.../save", 2);
 * InterstitialAd ad = AdPreloadManager.getInstance().pollInterstitial("INTER_SAVE");
 * if (ad != null) ad.show(activity);
 * </pre>
 */
public class AdPreloadManager {

    private static final String TAG = "AdPreloadManager";

    private static volatile AdPreloadManager instance;

    private AdPreloadManager() {
        nativeApiAvailable = detectNativePreloaderApi();
        Log.i(TAG, "AdMob Preloader API available: " + nativeApiAvailable);
    }

    public static AdPreloadManager getInstance() {
        if (instance == null) {
            synchronized (AdPreloadManager.class) {
                if (instance == null) {
                    instance = new AdPreloadManager();
                }
            }
        }
        return instance;
    }

    // =========================================================
    // STATE
    // =========================================================

    private final boolean nativeApiAvailable;

    // Manual fallback queues per placement key
    private final Map<String, Queue<InterstitialAd>> interstitialQueues = new HashMap<>();
    private final Map<String, Queue<RewardedAd>> rewardQueues = new HashMap<>();
    private final Map<String, Queue<AppOpenAd>> appOpenQueues = new HashMap<>();

    // Placement key -> ad unit ID mapping (for replenish)
    private final Map<String, String> interstitialAdIds = new HashMap<>();
    private final Map<String, String> rewardAdIds = new HashMap<>();
    private final Map<String, String> appOpenAdIds = new HashMap<>();

    // Per-placement buffer size
    private final Map<String, Integer> bufferSizes = new HashMap<>();

    // Loading flags (avoid duplicate requests)
    private final Map<String, Boolean> loading = Collections.synchronizedMap(new HashMap<>());

    // =========================================================
    // DETECTION
    // =========================================================

    private boolean detectNativePreloaderApi() {
        try {
            Class.forName("com.google.android.gms.ads.interstitial.InterstitialAdPreloader");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public boolean isNativeApiAvailable() {
        return nativeApiAvailable;
    }

    // =========================================================
    // INTERSTITIAL
    // =========================================================

    /**
     * Start preloading an interstitial for the given placement.
     *
     * @param context       Application or Activity context
     * @param placementKey  Unique key to identify the placement
     * @param adUnitId      Ad unit ID to preload (use highest-floor ID for waterfall placements)
     * @param bufferSize    Max ads to keep in cache (AdMob max: 4)
     */
    public synchronized void startInterstitial(Context context, String placementKey,
                                                String adUnitId, int bufferSize) {
        if (context == null || placementKey == null || adUnitId == null) return;
        interstitialAdIds.put(placementKey, adUnitId);
        bufferSizes.put(placementKey, Math.max(1, Math.min(4, bufferSize)));

        if (nativeApiAvailable) {
            startNativePreloaderInterstitial(context, placementKey, adUnitId, bufferSize);
        }
        // Kick off manual preload (always runs — even if native API claims availability,
        // we keep a local cache as backup)
        manualPreloadInterstitial(context, placementKey);
    }

    private void startNativePreloaderInterstitial(Context context, String placementKey,
                                                    String adUnitId, int bufferSize) {
        try {
            // Use reflection to avoid compile-time dependency on classes that may not exist
            Class<?> preloaderClass = Class.forName("com.google.android.gms.ads.interstitial.InterstitialAdPreloader");
            Class<?> configClass = Class.forName("com.google.android.gms.ads.preload.PreloadConfiguration");
            Class<?> configBuilderClass = Class.forName("com.google.android.gms.ads.preload.PreloadConfiguration$Builder");

            Object builder = configBuilderClass.getConstructor(String.class).newInstance(adUnitId);
            Method setBufferSize = configBuilderClass.getMethod("setBufferSize", int.class);
            builder = setBufferSize.invoke(builder, bufferSize);
            Method build = configBuilderClass.getMethod("build");
            Object config = build.invoke(builder);

            Method startMethod = preloaderClass.getMethod("start", String.class, configClass);
            startMethod.invoke(null, placementKey, config);

            Log.i(TAG, "AdMob Preloader started for " + placementKey + " (buffer=" + bufferSize + ")");
        } catch (Throwable t) {
            Log.w(TAG, "Native InterstitialAdPreloader failed, using manual fallback: " + t.getMessage());
        }
    }

    private void manualPreloadInterstitial(Context context, String placementKey) {
        Queue<InterstitialAd> queue = interstitialQueues.get(placementKey);
        if (queue == null) {
            queue = new ArrayDeque<>();
            interstitialQueues.put(placementKey, queue);
        }
        int target = bufferSizes.getOrDefault(placementKey, 1);
        if (queue.size() >= target) return;
        if (Boolean.TRUE.equals(loading.get("inter_" + placementKey))) return;
        loading.put("inter_" + placementKey, true);

        String adUnitId = interstitialAdIds.get(placementKey);
        if (adUnitId == null) {
            loading.put("inter_" + placementKey, false);
            return;
        }

        AdsMobileAdsManager.getInstance().loadInterAds(context, adUnitId, new AdCallback() {
            @Override
            public void onResultInterstitialAd(ApInterstitialAd interstitialAd) {
                super.onResultInterstitialAd(interstitialAd);
                Queue<InterstitialAd> q = interstitialQueues.get(placementKey);
                if (q != null && interstitialAd != null && interstitialAd.getInterstitialAd() != null) {
                    q.offer(interstitialAd.getInterstitialAd());
                    Log.d(TAG, "Preloaded interstitial for " + placementKey + " (" + q.size() + "/" + target + ")");
                }
                loading.put("inter_" + placementKey, false);
                // Fill up to buffer
                if (q != null && q.size() < target) {
                    manualPreloadInterstitial(context, placementKey);
                }
            }

            @Override
            public void onAdFailedToLoad(@NonNull ApAdError error) {
                super.onAdFailedToLoad(error);
                loading.put("inter_" + placementKey, false);
                Log.w(TAG, "Preload interstitial failed for " + placementKey);
            }
        });
    }

    /**
     * Poll a preloaded interstitial. Returns null if none available.
     * Triggers a background replenish after consuming.
     */
    @Nullable
    public synchronized InterstitialAd pollInterstitial(Context context, String placementKey) {
        InterstitialAd ad = null;

        // Try native API first
        if (nativeApiAvailable) {
            ad = pollNativeInterstitial(placementKey);
        }

        // Fall back to manual queue
        if (ad == null) {
            Queue<InterstitialAd> queue = interstitialQueues.get(placementKey);
            if (queue != null && !queue.isEmpty()) {
                ad = queue.poll();
            }
        }

        // Trigger replenish
        if (context != null) {
            manualPreloadInterstitial(context, placementKey);
        }
        return ad;
    }

    private InterstitialAd pollNativeInterstitial(String placementKey) {
        try {
            Class<?> preloaderClass = Class.forName("com.google.android.gms.ads.interstitial.InterstitialAdPreloader");
            Method pollMethod = preloaderClass.getMethod("pollAd", String.class);
            Object result = pollMethod.invoke(null, placementKey);
            return (InterstitialAd) result;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Check if a preloaded interstitial is available for the placement.
     */
    public boolean hasInterstitial(String placementKey) {
        if (nativeApiAvailable) {
            try {
                Class<?> preloaderClass = Class.forName("com.google.android.gms.ads.interstitial.InterstitialAdPreloader");
                Method isAvailable = preloaderClass.getMethod("isAdAvailable", String.class);
                Object result = isAvailable.invoke(null, placementKey);
                if (Boolean.TRUE.equals(result)) return true;
            } catch (Throwable ignored) {
            }
        }
        Queue<InterstitialAd> queue = interstitialQueues.get(placementKey);
        return queue != null && !queue.isEmpty();
    }

    // =========================================================
    // REWARD
    // =========================================================

    public synchronized void startReward(Context context, String placementKey,
                                          String adUnitId, int bufferSize) {
        if (context == null || placementKey == null || adUnitId == null) return;
        rewardAdIds.put(placementKey, adUnitId);
        bufferSizes.put(placementKey, Math.max(1, Math.min(4, bufferSize)));
        manualPreloadReward(context, placementKey);
    }

    private void manualPreloadReward(Context context, String placementKey) {
        Queue<RewardedAd> queue = rewardQueues.get(placementKey);
        if (queue == null) {
            queue = new ArrayDeque<>();
            rewardQueues.put(placementKey, queue);
        }
        int target = bufferSizes.getOrDefault(placementKey, 1);
        if (queue.size() >= target) return;
        if (Boolean.TRUE.equals(loading.get("reward_" + placementKey))) return;
        loading.put("reward_" + placementKey, true);

        String adUnitId = rewardAdIds.get(placementKey);
        if (adUnitId == null) {
            loading.put("reward_" + placementKey, false);
            return;
        }

        AdsMobileAdsManager.getInstance().loadRewardAd(context, adUnitId, new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                super.onAdLoaded(rewardedAd);
                Queue<RewardedAd> q = rewardQueues.get(placementKey);
                if (q != null) {
                    q.offer(rewardedAd);
                    Log.d(TAG, "Preloaded reward for " + placementKey + " (" + q.size() + "/" + target + ")");
                }
                loading.put("reward_" + placementKey, false);
                if (q != null && q.size() < target) {
                    manualPreloadReward(context, placementKey);
                }
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                super.onAdFailedToLoad(loadAdError);
                loading.put("reward_" + placementKey, false);
                Log.w(TAG, "Preload reward failed for " + placementKey);
            }
        });
    }

    @Nullable
    public synchronized RewardedAd pollReward(Context context, String placementKey) {
        Queue<RewardedAd> queue = rewardQueues.get(placementKey);
        RewardedAd ad = (queue != null) ? queue.poll() : null;
        if (context != null) {
            manualPreloadReward(context, placementKey);
        }
        return ad;
    }

    public boolean hasReward(String placementKey) {
        Queue<RewardedAd> queue = rewardQueues.get(placementKey);
        return queue != null && !queue.isEmpty();
    }

    // =========================================================
    // APP OPEN
    // =========================================================

    public synchronized void startAppOpen(Context context, String placementKey,
                                           String adUnitId, int bufferSize) {
        if (context == null || placementKey == null || adUnitId == null) return;
        appOpenAdIds.put(placementKey, adUnitId);
        bufferSizes.put(placementKey, Math.max(1, Math.min(4, bufferSize)));
        // App open preload via AdMob SDK happens in AppOpenAdManager already — we mirror the queue
    }

    public synchronized void storeAppOpen(String placementKey, AppOpenAd ad) {
        if (placementKey == null || ad == null) return;
        Queue<AppOpenAd> queue = appOpenQueues.get(placementKey);
        if (queue == null) {
            queue = new ArrayDeque<>();
            appOpenQueues.put(placementKey, queue);
        }
        int target = bufferSizes.getOrDefault(placementKey, 1);
        if (queue.size() < target) queue.offer(ad);
    }

    @Nullable
    public synchronized AppOpenAd pollAppOpen(String placementKey) {
        Queue<AppOpenAd> queue = appOpenQueues.get(placementKey);
        return (queue != null) ? queue.poll() : null;
    }

    // =========================================================
    // LIFECYCLE
    // =========================================================

    /**
     * Destroy all preload queues and stop native preloaders.
     */
    public synchronized void destroyAll() {
        interstitialQueues.clear();
        rewardQueues.clear();
        appOpenQueues.clear();
        loading.clear();

        if (nativeApiAvailable) {
            try {
                Class<?> preloaderClass = Class.forName("com.google.android.gms.ads.interstitial.InterstitialAdPreloader");
                Method destroyAll = preloaderClass.getMethod("destroyAll");
                destroyAll.invoke(null);
            } catch (Throwable ignored) {
            }
        }
    }
}
