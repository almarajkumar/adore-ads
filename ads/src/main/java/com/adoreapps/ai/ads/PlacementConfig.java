package com.adoreapps.ai.ads;

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

        public PlacementConfig build() {
            return new PlacementConfig(this);
        }
    }
}
