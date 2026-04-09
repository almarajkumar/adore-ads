package com.adoreapps.ai.ads.wrapper;

import com.google.android.gms.ads.LoadAdError;

public class ApAdError {
    private LoadAdError loadAdError;

    public ApAdError(LoadAdError loadAdError) {
        this.loadAdError = loadAdError;
    }

    public String getMessage() {
        return this.loadAdError != null ? this.loadAdError.getMessage() : "Unknown err";
    }

    public LoadAdError getLoadAdError() {
        return this.loadAdError;
    }
}
