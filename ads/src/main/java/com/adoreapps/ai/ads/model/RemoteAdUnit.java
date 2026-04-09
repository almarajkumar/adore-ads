package com.adoreapps.ai.ads.model;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Model for a single ad unit entry in Firebase Remote Config JSON.
 *
 * <pre>
 * [
 *   { "ad_id": "ca-app-pub-.../high", "priority": "high", "is_enabled": true },
 *   { "ad_id": "ca-app-pub-.../low",  "priority": "low",  "is_enabled": true }
 * ]
 * </pre>
 */
public class RemoteAdUnit {

    @SerializedName("ad_id")
    private String adId;

    @SerializedName("priority")
    private String priority;

    @SerializedName("is_enabled")
    private boolean isEnabled;

    public RemoteAdUnit() {}

    public RemoteAdUnit(String adId, String priority, boolean isEnabled) {
        this.adId = adId;
        this.priority = priority;
        this.isEnabled = isEnabled;
    }

    public String getAdId() { return adId; }
    public String getPriority() { return priority; }
    public boolean isEnabled() { return isEnabled; }

    public void setAdId(String adId) { this.adId = adId; }
    public void setPriority(String priority) { this.priority = priority; }
    public void setEnabled(boolean enabled) { isEnabled = enabled; }

    /**
     * Returns the sort order for a priority string.
     * high=0, medium=1, low=2, default=3, unknown=4
     */
    public static int priorityOrder(String priority) {
        if (priority == null) return 4;
        switch (priority.toLowerCase()) {
            case "high":    return 0;
            case "medium":  return 1;
            case "low":     return 2;
            case "default": return 3;
            default:        return 4;
        }
    }

    /**
     * Parse a JSON array string into a list of RemoteAdUnit.
     * Returns empty list on parse failure.
     */
    public static List<RemoteAdUnit> fromJson(String json) {
        if (json == null || json.trim().isEmpty()) return new ArrayList<>();
        try {
            Gson gson = new Gson();
            Type listType = new TypeToken<List<RemoteAdUnit>>() {}.getType();
            List<RemoteAdUnit> units = gson.fromJson(json, listType);
            return units != null ? units : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Parse JSON, filter enabled units, sort by priority (high first),
     * and return the ordered list of ad unit IDs.
     */
    public static List<String> toSortedAdIds(String json) {
        List<RemoteAdUnit> units = fromJson(json);
        List<RemoteAdUnit> enabled = new ArrayList<>();
        for (RemoteAdUnit unit : units) {
            if (unit.isEnabled() && unit.getAdId() != null && !unit.getAdId().isEmpty()) {
                enabled.add(unit);
            }
        }
        Collections.sort(enabled, (a, b) ->
                Integer.compare(priorityOrder(a.getPriority()), priorityOrder(b.getPriority())));
        List<String> ids = new ArrayList<>();
        for (RemoteAdUnit unit : enabled) {
            ids.add(unit.getAdId());
        }
        return ids;
    }
}
