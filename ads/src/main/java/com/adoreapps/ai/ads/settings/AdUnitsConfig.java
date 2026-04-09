package com.adoreapps.ai.ads.settings;

import java.util.ArrayList;
import java.util.List;

public class AdUnitsConfig {
    public List<String> adUnitIds = new ArrayList<>();
    public String viewEventName;
    public String clickEventName;

    public AdUnitsConfig(List<String> adUnitIds, String viewEventName, String clickEventName) {
        this.adUnitIds = adUnitIds;
        this.viewEventName = viewEventName;
        this.clickEventName = clickEventName;
    }

    public AdUnitsConfig(List<String> adUnitIds) {
        this.adUnitIds = adUnitIds;
    }

    public AdUnitsConfig() {}
}
