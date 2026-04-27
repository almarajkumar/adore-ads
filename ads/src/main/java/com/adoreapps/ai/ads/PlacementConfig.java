package com.adoreapps.ai.ads;

import com.adoreapps.ai.ads.settings.CollapsibleAnchor;
import com.adoreapps.ai.ads.settings.LoadMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for a single ad placement (native, interstitial, reward, or banner).
 *
 * <h3>Simple usage:</h3>
 * <pre>
 * new PlacementConfig(Arrays.asList("ca-app-pub-.../high"), true)
 * </pre>
 *
 * <h3>Full configuration (v1.5.1):</h3>
 * <pre>
 * new PlacementConfig.Builder()
 *     .setAdUnitIds(Arrays.asList("high_floor", "low_floor"))
 *     .setLoadMode(LoadMode.WATERFALL)              // or SINGLE
 *     .setBackupNativePlacementKey("NATIVE_BACKUP") // fallback native for interstitial
 *     .setBackupCountdownSeconds(5)
 *     .setEnabled(true)
 *     .build()
 * </pre>
 */
public class PlacementConfig {
    private List<String> adUnitIds;
    private boolean enabled;
    private final String viewEventName;
    private final String clickEventName;

    // v1.5.1 — loading strategy + fallback
    private LoadMode loadMode = LoadMode.WATERFALL;
    private String backupNativePlacementKey = "";
    private int backupCountdownSeconds = 5;

    // v1.5.2 — native ad carousel (slides through multiple ads)
    private int carouselSlideIntervalSeconds = 5;
    private boolean carouselCircular = true;
    private boolean carouselAutoRefreshEnabled = false;

    // v1.5.5 — collapsible banner anchor (banner placements only)
    private CollapsibleAnchor collapsibleAnchor = CollapsibleAnchor.NONE;

    public PlacementConfig(List<String> adUnitIds, boolean enabled,
                           String viewEventName, String clickEventName) {
        this.adUnitIds = adUnitIds != null ? new ArrayList<>(adUnitIds) : new ArrayList<>();
        this.enabled = enabled;
        this.viewEventName = viewEventName;
        this.clickEventName = clickEventName;
    }

    public PlacementConfig(List<String> adUnitIds, boolean enabled) {
        this(adUnitIds, enabled, "", "");
    }

    public PlacementConfig(List<String> adUnitIds) {
        this(adUnitIds, true, "", "");
    }

    private PlacementConfig(Builder b) {
        this.adUnitIds = b.adUnitIds != null ? new ArrayList<>(b.adUnitIds) : new ArrayList<>();
        this.enabled = b.enabled;
        this.viewEventName = b.viewEventName;
        this.clickEventName = b.clickEventName;
        this.loadMode = b.loadMode != null ? b.loadMode : LoadMode.WATERFALL;
        this.backupNativePlacementKey = b.backupNativePlacementKey != null ? b.backupNativePlacementKey : "";
        this.backupCountdownSeconds = b.backupCountdownSeconds;
        this.carouselSlideIntervalSeconds = b.carouselSlideIntervalSeconds;
        this.carouselCircular = b.carouselCircular;
        this.carouselAutoRefreshEnabled = b.carouselAutoRefreshEnabled;
        this.collapsibleAnchor = b.collapsibleAnchor != null ? b.collapsibleAnchor : CollapsibleAnchor.NONE;
    }

    // =========================================================
    // GETTERS
    // =========================================================

    public List<String> getAdUnitIds() { return adUnitIds; }
    public boolean isEnabled() { return enabled; }
    public String getViewEventName() { return viewEventName; }
    public String getClickEventName() { return clickEventName; }
    public LoadMode getLoadMode() { return loadMode; }
    public String getBackupNativePlacementKey() { return backupNativePlacementKey; }
    public int getBackupCountdownSeconds() { return backupCountdownSeconds; }
    public int getCarouselSlideIntervalSeconds() { return carouselSlideIntervalSeconds; }
    public boolean isCarouselCircular() { return carouselCircular; }
    public boolean isCarouselAutoRefreshEnabled() { return carouselAutoRefreshEnabled; }
    public CollapsibleAnchor getCollapsibleAnchor() { return collapsibleAnchor; }

    public boolean hasBackupNative() {
        return backupNativePlacementKey != null && !backupNativePlacementKey.isEmpty();
    }

    // =========================================================
    // SETTERS
    // =========================================================

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setLoadMode(LoadMode loadMode) { this.loadMode = loadMode != null ? loadMode : LoadMode.WATERFALL; }
    public void setBackupNativePlacementKey(String key) { this.backupNativePlacementKey = key != null ? key : ""; }
    public void setBackupCountdownSeconds(int seconds) { this.backupCountdownSeconds = Math.max(0, seconds); }
    public void setCarouselSlideIntervalSeconds(int seconds) { this.carouselSlideIntervalSeconds = Math.max(1, seconds); }
    public void setCarouselCircular(boolean circular) { this.carouselCircular = circular; }
    public void setCarouselAutoRefreshEnabled(boolean enabled) { this.carouselAutoRefreshEnabled = enabled; }
    public void setCollapsibleAnchor(CollapsibleAnchor anchor) {
        this.collapsibleAnchor = anchor != null ? anchor : CollapsibleAnchor.NONE;
    }

    /**
     * Replace the ad unit IDs list (e.g. from remote config override).
     */
    public void setAdUnitIds(List<String> ids) {
        this.adUnitIds.clear();
        if (ids != null) {
            this.adUnitIds.addAll(ids);
        }
    }

    // =========================================================
    // BUILDER
    // =========================================================

    public static class Builder {
        private List<String> adUnitIds = new ArrayList<>();
        private boolean enabled = true;
        private String viewEventName = "";
        private String clickEventName = "";
        private LoadMode loadMode = LoadMode.WATERFALL;
        private String backupNativePlacementKey = "";
        private int backupCountdownSeconds = 5;
        private int carouselSlideIntervalSeconds = 5;
        private boolean carouselCircular = true;
        private boolean carouselAutoRefreshEnabled = false;
        private CollapsibleAnchor collapsibleAnchor = CollapsibleAnchor.NONE;

        public Builder setAdUnitIds(List<String> ids) {
            this.adUnitIds = ids != null ? new ArrayList<>(ids) : new ArrayList<>();
            return this;
        }
        public Builder setEnabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder setViewEventName(String name) { this.viewEventName = name != null ? name : ""; return this; }
        public Builder setClickEventName(String name) { this.clickEventName = name != null ? name : ""; return this; }
        public Builder setLoadMode(LoadMode mode) { this.loadMode = mode; return this; }
        public Builder setBackupNativePlacementKey(String key) { this.backupNativePlacementKey = key; return this; }
        public Builder setBackupCountdownSeconds(int seconds) { this.backupCountdownSeconds = seconds; return this; }
        public Builder setCarouselSlideIntervalSeconds(int seconds) { this.carouselSlideIntervalSeconds = Math.max(1, seconds); return this; }
        public Builder setCarouselCircular(boolean circular) { this.carouselCircular = circular; return this; }
        public Builder setCarouselAutoRefreshEnabled(boolean enabled) { this.carouselAutoRefreshEnabled = enabled; return this; }
        /**
         * Enable collapsible banner mode (banner placements only).
         * <p>Collapsible banners render at a larger expanded size on the first
         * impression then collapse to the standard anchored size. Set the anchor
         * to control which edge the expanded portion grows from.
         * <p>See: <a href="https://developers.google.com/ad-manager/mobile-ads-sdk/android/banner/collapsible">Collapsible banners</a>
         */
        public Builder setCollapsibleAnchor(CollapsibleAnchor anchor) {
            this.collapsibleAnchor = anchor != null ? anchor : CollapsibleAnchor.NONE;
            return this;
        }

        public PlacementConfig build() {
            return new PlacementConfig(this);
        }
    }
}
