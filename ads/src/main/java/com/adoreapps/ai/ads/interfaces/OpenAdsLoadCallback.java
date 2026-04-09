package com.adoreapps.ai.ads.interfaces;

import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.appopen.AppOpenAd;

public interface OpenAdsLoadCallback {
    void onAdsLoaded(AppOpenAd var1);

    void onFail(LoadAdError var1);
}
