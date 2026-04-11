package com.adoreapps.ai.ads;

import com.adoreapps.ai.ads.billing.PurchaseManager;
import com.adoreapps.ai.ads.core.AppOpenAdManager;
import com.adoreapps.ai.ads.interfaces.PurchaseCallback;

import com.adoreapps.ai.ads.core.AdsMobileAdsManager;

import android.app.Application;

import com.adjust.sdk.Adjust;
import com.adjust.sdk.AdjustConfig;
import com.adoreapps.ai.ads.event.FirebaseAnalyticsEvents;
import com.adoreapps.ai.ads.interfaces.PurchaseCallback;
import com.adoreapps.ai.ads.model.PurchaseModel;
import com.adoreapps.ai.ads.utils.SharePrefUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class AdoreAppsAdsApplication extends Application {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize lightweight components first
        SharePrefUtils.getInstance().init(this);
        FirebaseAnalyticsEvents.getInstance().init(this);

        // ⚡ Initialize Ads asynchronously (non-blocking)
        initAdsAsync();

        // Other setup (independent)
        AdsMobileAdsManager.getInstance().setAdsEnabled(setAdsEnabled());
        AdsMobileAdsManager.getInstance().setUseTestAdIds(isUseTestAds());
        AdsMobileAdsManager.getInstance().setShowLoadingDialog(isShowDialogLoadingAd());

        // Allow subclasses to run custom startup logic
        onApplicationCreate();

        // Initialize billing asynchronously (if enabled)
        if (isInitBilling()) {
            executor.execute(() -> {
                PurchaseManager.getInstance().init(this, getPurchaseList());
                PurchaseManager.getInstance().setCallback(new PurchaseCallback() {
                    @Override
                    public void purchaseSuccess() {

                    }

                    @Override
                    public void purchaseFail() {

                    }
                });
            });
        }
    }

    /**
     * Initialize ads asynchronously to avoid blocking the UI thread.
     */
    private void initAdsAsync() {
        if(PurchaseManager.getInstance().isPurchased()) {
            setAdsInitialized(true);
        }
        AdsMobileAdsManager.getInstance().init(
                this,
                isShowAdsTest() ? AdsMobileAdsManager.getInstance().getDeviceId(this) : "",
                isShowAdsTest(),
                () -> {
                    setAdsInitialized(true);
                    // Continue with secondary ad networks after AdMob is ready
                    executor.execute(() -> AdsMobileAdsManager.getInstance().initRemainingNetworksAsync(this, hasUserConsent()));
                }
        );
    }

    /**
     * Initialize App Open Ads safely.
     */
    public void initAppOpenAds() {
        AppOpenAdManager.getInstance().init(this, getOpenAppAdIds());
    }

    /**
     * Initialize Adjust SDK safely.
     */
    public void initAdjust(boolean isDebug) {
        String adjustToken = getAdjustToken();
        if (!adjustToken.isEmpty()) {
            String environment = isDebug
                    ? AdjustConfig.ENVIRONMENT_SANDBOX
                    : AdjustConfig.ENVIRONMENT_PRODUCTION;
            AdjustConfig adjustConfig = new AdjustConfig(this, adjustToken, environment);
            adjustConfig.setLogLevel(com.adjust.sdk.LogLevel.VERBOSE);
            Adjust.initSdk(adjustConfig);
            com.adoreapps.ai.ads.event.AdjustEvents.getInstance().setEnabled(true);
        }
    }

    // === Abstract methods for subclasses (Pictyle, etc.) ===

    protected abstract void onApplicationCreate();
    protected abstract boolean setAdsEnabled();
    protected abstract boolean isShowDialogLoadingAd();
    public abstract boolean isAdsInitialized();
    public abstract void setAdsInitialized(boolean isAdsInitialized);
    protected abstract boolean isShowAdsTest();
    protected abstract boolean isUseTestAds();
    protected abstract boolean enableAdsResume();
    protected abstract String getOpenAppAdId();
    protected abstract String getAdjustToken();
    protected abstract boolean isInitBilling();
    protected abstract List<PurchaseModel> getPurchaseList();

    /**
     * Override to provide multiple app open ad IDs for waterfall loading.
     * By default, wraps the single ID from getOpenAppAdId().
     */
    protected List<String> getOpenAppAdIds() {
        List<String> ids = new ArrayList<>();
        ids.add(getOpenAppAdId());
        return ids;
    }

    /**
     * Override to return actual user consent status from UMP/CMP.
     * Defaults to false (no consent) for GDPR/CCPA safety.
     */
    public boolean hasUserConsent() {
        return false;
    }
}
