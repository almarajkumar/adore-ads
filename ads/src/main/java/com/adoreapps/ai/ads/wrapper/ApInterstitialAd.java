package com.adoreapps.ai.ads.wrapper;

import com.google.android.gms.ads.interstitial.InterstitialAd;

public class ApInterstitialAd extends ApAdBase {
    private InterstitialAd interstitialAd;

    public ApInterstitialAd(InterstitialAd interstitialAd) {
        this.interstitialAd = interstitialAd;
        this.statusAd = StatusAd.AD_LOADED;
    }

    public InterstitialAd getInterstitialAd() {
        return this.interstitialAd;
    }
}
