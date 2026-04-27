package com.adoreapps.ai.ads.settings;

/**
 * Anchor position for AdMob collapsible banner ads.
 *
 * <p>Collapsible banners render at a larger size on first impression then
 * collapse back to the standard anchored size. The anchor controls where
 * the expanded portion grows relative to the placeholder view.
 *
 * <ul>
 *     <li>{@link #NONE} — standard banner (no collapsible behaviour)</li>
 *     <li>{@link #TOP} — anchored at top; expanded area grows downward</li>
 *     <li>{@link #BOTTOM} — anchored at bottom; expanded area grows upward (typical)</li>
 * </ul>
 *
 * Reference: <a href="https://developers.google.com/ad-manager/mobile-ads-sdk/android/banner/collapsible">Collapsible banner ads</a>
 */
public enum CollapsibleAnchor {
    NONE(null),
    TOP("top"),
    BOTTOM("bottom");

    private final String value;

    CollapsibleAnchor(String value) {
        this.value = value;
    }

    /** Returns the AdMob extras value or null if not collapsible. */
    public String value() {
        return value;
    }

    public boolean isCollapsible() {
        return value != null;
    }
}
