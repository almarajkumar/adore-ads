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

    // =========================================================
    // TEST AD UNIT IDS (Google-provided sample ads)
    // =========================================================

    public static final String TEST_BANNER_AD_ID = "ca-app-pub-3940256099942544/9214589741";
    public static final String TEST_INTERSTITIAL_AD_ID = "ca-app-pub-3940256099942544/1033173712";
    public static final String TEST_NATIVE_AD_ID = "ca-app-pub-3940256099942544/2247696110";
    public static final String TEST_REWARD_AD_ID = "ca-app-pub-3940256099942544/5224354917";
    public static final String TEST_APP_OPEN_AD_ID = "ca-app-pub-3940256099942544/9257395921";
}
