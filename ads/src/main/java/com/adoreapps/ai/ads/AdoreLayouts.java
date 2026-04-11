package com.adoreapps.ai.ads;

import androidx.annotation.LayoutRes;

/**
 * Predefined native ad layout resources bundled with the Adore Ads library.
 * Use these constants with {@link com.adoreapps.ai.ads.manager.NativeAdManager} methods.
 *
 * <h3>Layout sizes:</h3>
 * <ul>
 *   <li><b>FULL_SCREEN</b> — Full-screen with media, countdown timer, skip/close button</li>
 *   <li><b>LARGE</b> — Card with 16:9 media, icon, headline, body, CTA</li>
 *   <li><b>MEDIUM</b> — Card with compact 120dp media, icon, headline, body, CTA</li>
 *   <li><b>SMALL</b> — Compact horizontal row: icon + text + CTA, no media</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <pre>
 * nativeAds().showWithAutoRefresh(activity, container, shimmer,
 *     AdoreLayouts.NATIVE_LARGE, "NATIVE_HOME", "tag", lifecycleOwner);
 * </pre>
 */
public final class AdoreLayouts {

    private AdoreLayouts() {}

    // =========================================================
    // NATIVE AD LAYOUTS
    // =========================================================

    /** Full-screen native ad with media, countdown, skip/close button. */
    @LayoutRes
    public static final int NATIVE_FULL_SCREEN = R.layout.adore_native_full_screen;

    /** Large native ad card — 16:9 media, icon, headline, body, CTA. */
    @LayoutRes
    public static final int NATIVE_LARGE = R.layout.adore_native_large;

    /** Medium native ad card — compact 120dp media, icon, headline, body, CTA. */
    @LayoutRes
    public static final int NATIVE_MEDIUM = R.layout.adore_native_medium;

    /** Small native ad — horizontal row: icon + headline + body + CTA, no media. */
    @LayoutRes
    public static final int NATIVE_SMALL = R.layout.adore_native_small;

    // =========================================================
    // SHIMMER LAYOUTS (matching each native size)
    // =========================================================

    /** Shimmer placeholder for full-screen native ad. */
    @LayoutRes
    public static final int SHIMMER_FULL_SCREEN = R.layout.adore_shimmer_full_screen;

    /** Shimmer placeholder for large native ad. */
    @LayoutRes
    public static final int SHIMMER_LARGE = R.layout.adore_shimmer_large;

    /** Shimmer placeholder for medium native ad. */
    @LayoutRes
    public static final int SHIMMER_MEDIUM = R.layout.adore_shimmer_medium;

    /** Shimmer placeholder for small native ad. */
    @LayoutRes
    public static final int SHIMMER_SMALL = R.layout.adore_shimmer_small;
}
