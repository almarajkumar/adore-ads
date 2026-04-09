package com.adoreapps.ai.ads.wrapper;

import com.google.android.gms.ads.AdView;

public class ApAdView {
    private AdView adView;

    public ApAdView(AdView adView) {
        this.adView = adView;
    }

    public AdView getAdView() {
        return this.adView;
    }
}
