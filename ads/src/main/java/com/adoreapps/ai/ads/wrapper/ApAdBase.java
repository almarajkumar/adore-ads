package com.adoreapps.ai.ads.wrapper;

public class ApAdBase {
    protected StatusAd statusAd;

    public ApAdBase(StatusAd statusAd) {
        this.statusAd = StatusAd.AD_INIT;
        this.statusAd = statusAd;
    }

    public ApAdBase() {
        this.statusAd = StatusAd.AD_INIT;
    }

    public StatusAd getStatusAd() {
        return this.statusAd;
    }

    public void setStatusAd(StatusAd statusAd) {
        this.statusAd = statusAd;
    }

    public Boolean isReady() {
        return this.statusAd == StatusAd.AD_LOADED;
    }

    public Boolean isLoading() {
        return this.statusAd == StatusAd.AD_LOADING;
    }

    public Boolean isLoadFail() {
        return this.statusAd == StatusAd.AD_LOAD_FAIL;
    }
}
