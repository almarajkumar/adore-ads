package com.adoreapps.ai.ads;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for a single ad placement (native, interstitial, reward, or banner).
 * Each placement has a list of ad unit IDs (waterfall order: high floor first)
 * and analytics event names.
 */
public class PlacementConfig {
    private final List<String> adUnitIds;
    private boolean enabled;
    private final String viewEventName;
    private final String clickEventName;

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

    public List<String> getAdUnitIds() { return adUnitIds; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getViewEventName() { return viewEventName; }
    public String getClickEventName() { return clickEventName; }
}
