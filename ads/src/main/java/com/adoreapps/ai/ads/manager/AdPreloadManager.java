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
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

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

    // v1.5.6 — TTLs per AdMob policy
    private static final long INTERSTITIAL_TTL_MS = 60L * 60 * 1000;     // 60 min
    private static final long REWARD_TTL_MS = 60L * 60 * 1000;           // 60 min
    private static final long APP_OPEN_TTL_MS = 4L * 60 * 60 * 1000;     // 4 hours

    // v1.5.6 — backoff for failing placements (manual preload)
    private final Map<String, Long> blockedUntilMs = new ConcurrentHashMap<>();
    private final Map<String, Integer> retryAttempts = new ConcurrentHashMap<>();

    /** v1.5.6 — wraps a cached ad with its load timestamp for TTL enforcement. */
    private static final class Holder<T> {
        final T ad;
        final long loadedAtMs;
        Holder(T ad) {
            this.ad = ad;
            this.loadedAtMs = System.currentTimeMillis();
        }
        boolean isExpired(long ttlMs) {
            return (System.currentTimeMillis() - loadedAtMs) > ttlMs;
        }
    }

    // Manual fallback queues per placement key (now holding timestamped wrappers)
    private final Map<String, Queue<Holder<InterstitialAd>>> interstitialQueues = new ConcurrentHashMap<>();
    private final Map<String, Queue<Holder<RewardedAd>>> rewardQueues = new ConcurrentHashMap<>();
    private final Map<String, Queue<Holder<AppOpenAd>>> appOpenQueues = new ConcurrentHashMap<>();

    // Placement key -> ad unit ID mapping (for replenish)
    private final Map<String, String> interstitialAdIds = new ConcurrentHashMap<>();
    private final Map<String, String> rewardAdIds = new ConcurrentHashMap<>();
    private final Map<String, String> appOpenAdIds = new ConcurrentHashMap<>();

    // Per-placement buffer size
    private final Map<String, Integer> bufferSizes = new ConcurrentHashMap<>();

    // Loading flags (avoid duplicate requests)
    private final Map<String, Boolean> loading = new ConcurrentHashMap<>();

    /** v1.5.6 — exponential backoff gate; returns true if a retry is allowed now. */
    private boolean backoffAllowsRetry(String key) {
        Long until = blockedUntilMs.get(key);
        return until == null || System.currentTimeMillis() >= until;
    }

    private void noteFailure(String key) {
        int attempt = retryAttempts.getOrDefault(key, 0) + 1;
        retryAttempts.put(key, attempt);
        long backoff = Math.min(60_000L, 1000L * (1L << Math.min(6, attempt)));
        blockedUntilMs.put(key, System.currentTimeMillis() + backoff);
        Log.w(TAG, "Preload backoff " + backoff + "ms for " + key + " (attempt " + attempt + ")");
    }

    private void noteSuccess(String key) {
        retryAttempts.remove(key);
        blockedUntilMs.remove(key);
    }

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
        final String backoffKey = "inter_" + placementKey;
        if (!backoffAllowsRetry(backoffKey)) return;
        Queue<Holder<InterstitialAd>> queue = interstitialQueues.get(placementKey);
        if (queue == null) {
            queue = new ConcurrentLinkedQueue<>();
            interstitialQueues.put(placementKey, queue);
        }
        int target = bufferSizes.getOrDefault(placementKey, 1);
        // drop expired before computing fill
        dropExpired(queue, INTERSTITIAL_TTL_MS);
        if (queue.size() >= target) return;
        if (Boolean.TRUE.equals(loading.get(backoffKey))) return;
        loading.put(backoffKey, true);

        String adUnitId = interstitialAdIds.get(placementKey);
        if (adUnitId == null) {
            loading.put(backoffKey, false);
            return;
        }

        AdsMobileAdsManager.getInstance().loadInterAds(context, adUnitId, new AdCallback() {
            @Override
            public void onResultInterstitialAd(ApInterstitialAd interstitialAd) {
                super.onResultInterstitialAd(interstitialAd);
                Queue<Holder<InterstitialAd>> q = interstitialQueues.get(placementKey);
                if (q != null && interstitialAd != null && interstitialAd.getInterstitialAd() != null) {
                    q.offer(new Holder<>(interstitialAd.getInterstitialAd()));
                    Log.d(TAG, "Preloaded interstitial for " + placementKey + " (" + q.size() + "/" + target + ")");
                    noteSuccess(backoffKey);
                }
                loading.put(backoffKey, false);
                if (q != null && q.size() < target) {
                    manualPreloadInterstitial(context, placementKey);
                }
            }

            @Override
            public void onAdFailedToLoad(@NonNull ApAdError error) {
                super.onAdFailedToLoad(error);
                loading.put(backoffKey, false);
                noteFailure(backoffKey);
                Log.w(TAG, "Preload interstitial failed for " + placementKey);
            }
        });
    }

    /** Drop expired holders from the head of the queue. */
    private static <T> void dropExpired(Queue<Holder<T>> queue, long ttl) {
        if (queue == null) return;
        Holder<T> head;
        while ((head = queue.peek()) != null && head.isExpired(ttl)) {
            queue.poll();
        }
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

        // Fall back to manual queue (drop expired first)
        if (ad == null) {
            Queue<Holder<InterstitialAd>> queue = interstitialQueues.get(placementKey);
            dropExpired(queue, INTERSTITIAL_TTL_MS);
            if (queue != null && !queue.isEmpty()) {
                Holder<InterstitialAd> h = queue.poll();
                if (h != null) ad = h.ad;
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
        Queue<Holder<InterstitialAd>> queue = interstitialQueues.get(placementKey);
        dropExpired(queue, INTERSTITIAL_TTL_MS);
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
        final String backoffKey = "reward_" + placementKey;
        if (!backoffAllowsRetry(backoffKey)) return;
        Queue<Holder<RewardedAd>> queue = rewardQueues.get(placementKey);
        if (queue == null) {
            queue = new ConcurrentLinkedQueue<>();
            rewardQueues.put(placementKey, queue);
        }
        int target = bufferSizes.getOrDefault(placementKey, 1);
        dropExpired(queue, REWARD_TTL_MS);
        if (queue.size() >= target) return;
        if (Boolean.TRUE.equals(loading.get(backoffKey))) return;
        loading.put(backoffKey, true);

        String adUnitId = rewardAdIds.get(placementKey);
        if (adUnitId == null) {
            loading.put(backoffKey, false);
            return;
        }

        AdsMobileAdsManager.getInstance().loadRewardAd(context, adUnitId, new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                super.onAdLoaded(rewardedAd);
                Queue<Holder<RewardedAd>> q = rewardQueues.get(placementKey);
                if (q != null) {
                    q.offer(new Holder<>(rewardedAd));
                    Log.d(TAG, "Preloaded reward for " + placementKey + " (" + q.size() + "/" + target + ")");
                    noteSuccess(backoffKey);
                }
                loading.put(backoffKey, false);
                if (q != null && q.size() < target) {
                    manualPreloadReward(context, placementKey);
                }
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                super.onAdFailedToLoad(loadAdError);
                loading.put(backoffKey, false);
                noteFailure(backoffKey);
                Log.w(TAG, "Preload reward failed for " + placementKey);
            }
        });
    }

    @Nullable
    public synchronized RewardedAd pollReward(Context context, String placementKey) {
        Queue<Holder<RewardedAd>> queue = rewardQueues.get(placementKey);
        dropExpired(queue, REWARD_TTL_MS);
        Holder<RewardedAd> h = (queue != null) ? queue.poll() : null;
        if (context != null) {
            manualPreloadReward(context, placementKey);
        }
        return h != null ? h.ad : null;
    }

    public boolean hasReward(String placementKey) {
        Queue<Holder<RewardedAd>> queue = rewardQueues.get(placementKey);
        dropExpired(queue, REWARD_TTL_MS);
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
        Queue<Holder<AppOpenAd>> queue = appOpenQueues.get(placementKey);
        if (queue == null) {
            queue = new ConcurrentLinkedQueue<>();
            appOpenQueues.put(placementKey, queue);
        }
        int target = bufferSizes.getOrDefault(placementKey, 1);
        dropExpired(queue, APP_OPEN_TTL_MS);
        if (queue.size() < target) queue.offer(new Holder<>(ad));
    }

    @Nullable
    public synchronized AppOpenAd pollAppOpen(String placementKey) {
        Queue<Holder<AppOpenAd>> queue = appOpenQueues.get(placementKey);
        dropExpired(queue, APP_OPEN_TTL_MS);
        Holder<AppOpenAd> h = (queue != null) ? queue.poll() : null;
        return h != null ? h.ad : null;
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
        blockedUntilMs.clear();
        retryAttempts.clear();

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
