package com.adoreapps.ai.ads.settings;

import android.app.Activity;
import android.util.DisplayMetrics;

import com.google.android.gms.ads.AdSize;

/**
 * Banner ad sizes supported by the Adore Ads library.
 *
 * <h3>Usage:</h3>
 * <pre>
 * // Set globally for all banner placements
 * BannerAdManager.getInstance().setBannerSize(BannerSize.ADAPTIVE);
 *
 * // Or per-placement
 * bannerAds().loadAndShowBannerAd(activity, "BANNER_HOME", container, BannerSize.MEDIUM_RECTANGLE);
 * </pre>
 */
public enum BannerSize {

    /** Standard 320x50 banner. */
    BANNER,

    /** Large 320x100 banner. */
    LARGE_BANNER,

    /** Medium rectangle 300x250 (MREC). */
    MEDIUM_RECTANGLE,

    /** Full-size banner 468x60 (tablet). */
    FULL_BANNER,

    /** Leaderboard 728x90 (tablet). */
    LEADERBOARD,

    /**
     * Anchored adaptive banner — width matches screen, height determined by SDK.
     * Uses {@link AdSize#getCurrentOrientationAnchoredAdaptiveBannerAdSize}.
     * Recommended for most modern apps.
     */
    ADAPTIVE,

    /**
     * Large anchored adaptive banner — same as ADAPTIVE but with larger height.
     * Uses {@link AdSize#getLargeAnchoredAdaptiveBannerAdSize} (SDK 25+).
     * Default for the library.
     */
    ADAPTIVE_LARGE,

    /**
     * Inline adaptive banner — flexible height based on width, good for feeds.
     * Uses {@link AdSize#getCurrentOrientationInlineAdaptiveBannerAdSize}.
     */
    INLINE_ADAPTIVE;

    /**
     * Convert this enum to a Google Mobile Ads {@link AdSize}.
     *
     * @param activity Current activity (needed for adaptive sizes)
     * @return The AdSize to use for the banner
     */
    public AdSize toAdSize(Activity activity) {
        switch (this) {
            case BANNER:
                return AdSize.BANNER;
            case LARGE_BANNER:
                return AdSize.LARGE_BANNER;
            case MEDIUM_RECTANGLE:
                return AdSize.MEDIUM_RECTANGLE;
            case FULL_BANNER:
                return AdSize.FULL_BANNER;
            case LEADERBOARD:
                return AdSize.LEADERBOARD;
            case ADAPTIVE: {
                int adWidth = getAdWidth(activity);
                return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth);
            }
            case INLINE_ADAPTIVE: {
                int adWidth = getAdWidth(activity);
                return AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(activity, adWidth);
            }
            case ADAPTIVE_LARGE:
            default: {
                int adWidth = getAdWidth(activity);
                return AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, adWidth);
            }
        }
    }

    private int getAdWidth(Activity activity) {
        DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
        return (int) (displayMetrics.widthPixels / displayMetrics.density);
    }

    /**
     * Parse a remote config string to BannerSize. Returns ADAPTIVE_LARGE if invalid.
     */
    public static BannerSize fromString(String value) {
        if (value == null || value.isEmpty()) return ADAPTIVE_LARGE;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ADAPTIVE_LARGE;
        }
    }
}
