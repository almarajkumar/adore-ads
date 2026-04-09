package com.adoreapps.ai.ads.settings;

/**
 * Centralized constants for the Adore Ads library.
 * All timing, expiry, and test ad ID values live here.
 */
public final class AdConstants {

    private AdConstants() {}

    // =========================================================
    // TIMING
    // =========================================================

    /** Ad expiry duration — AdMob invalidates ads after ~60 min; we expire at 55 min. */
    public static final long AD_EXPIRY_MS = 55 * 60 * 1000L;

    /** Minimum allowed native ad refresh interval (seconds). */
    public static final long MIN_REFRESH_INTERVAL_SECONDS = 15L;

    /** Default native ad refresh interval (seconds). */
    public static final long DEFAULT_REFRESH_INTERVAL_SECONDS = 20L;

    /** Default cooldown between interstitial ad shows (seconds). */
    public static final long DEFAULT_INTERSTITIAL_COOLDOWN_SECONDS = 30L;

    /** Default loading dialog timeout before auto-dismiss (milliseconds). */
    public static final long LOADING_DIALOG_TIMEOUT_MS = 15_000L;

    /** Max time to wait for an ad load response before timing out (milliseconds). */
    public static final long AD_LOAD_TIMEOUT_MS = 10_000L;

    /** Max retry attempts for DefaultAdPool preload failures. */
    public static final int POOL_MAX_RETRY_ATTEMPTS = 3;

    /** Base retry delay for DefaultAdPool preload (milliseconds). Doubles each attempt: 30s, 60s, 120s. */
    public static final long POOL_RETRY_BASE_DELAY_MS = 30_000L;

    /** Max ads cached per type in DefaultAdPool. */
    public static final int POOL_MAX_CACHE_SIZE = 2;

    /** Max stagger delay (ms) between placement refreshes to avoid thundering herd. */
    public static final long REFRESH_STAGGER_MAX_MS = 5_000L;

    /** SharedPreferences key for interstitial cooldown end timestamp. */
    public static final String PREF_INTERSTITIAL_COOLDOWN_END = "adore_interstitial_cooldown_end";

    // =========================================================
    // TEST AD UNIT IDS (Google-provided sample ads)
    // =========================================================

    public static final String TEST_BANNER_AD_ID = "ca-app-pub-3940256099942544/9214589741";
    public static final String TEST_INTERSTITIAL_AD_ID = "ca-app-pub-3940256099942544/1033173712";
    public static final String TEST_NATIVE_AD_ID = "ca-app-pub-3940256099942544/2247696110";
    public static final String TEST_REWARD_AD_ID = "ca-app-pub-3940256099942544/5224354917";
    public static final String TEST_APP_OPEN_AD_ID = "ca-app-pub-3940256099942544/9257395921";
}
