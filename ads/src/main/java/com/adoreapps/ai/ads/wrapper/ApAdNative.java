package com.adoreapps.ai.ads.wrapper;

import com.google.android.gms.ads.nativead.NativeAd;

public final class ApAdNative extends ApAdBase {
    private final NativeAd nativeAd;

    public ApAdNative() {
        this.statusAd = StatusAd.AD_LOADING;
        this.nativeAd = null;
    }

    public ApAdNative(NativeAd nativeAd) {
        this.nativeAd = nativeAd;
        this.statusAd = StatusAd.AD_LOADED;
    }

    public void setStatusAd(StatusAd statusAd) {
        super.setStatusAd(statusAd);
    }

    public NativeAd getNativeAd() {
        return this.nativeAd;
    }
}
