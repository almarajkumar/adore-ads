package com.adoreapps.sampleads;

import android.app.Application;
import android.util.Log;

import com.adoreapps.ai.ads.AdoreAds;
import com.adoreapps.ai.ads.AdoreAdsConfig;
import com.adoreapps.ai.ads.PlacementConfig;
import com.adoreapps.ai.ads.event.AdjustEvents;
import com.adoreapps.ai.ads.model.PurchaseModel;
import com.adoreapps.ai.ads.settings.AdConstants;
import com.adoreapps.ai.ads.settings.LoadMode;

import java.util.Arrays;
import java.util.Collections;

/**
 * Sample application demonstrating full AdoreAds v1.2.0 initialization.
 *
 * All ad unit IDs below are Google's official test IDs.
 * Replace with your production IDs before releasing.
 */
public class SampleAdsApp extends Application {

    private static final String TAG = "SampleAdsApp";

    @Override
    public void onCreate() {
        super.onCreate();

        AdoreAdsConfig config = new AdoreAdsConfig.Builder(this)
                .setAdsEnabled(true)
                .setUseTestAdIds(true)

                // --- Native placements ---
                .addNativePlacement("NATIVE_SPLASH", new PlacementConfig(
                        Arrays.asList(AdConstants.TEST_NATIVE_AD_ID),
                        true, "splash_native_view", "splash_native_click"))
                .addNativePlacement("NATIVE_HOME", new PlacementConfig(
                        Arrays.asList(AdConstants.TEST_NATIVE_AD_ID),
                        true, "home_native_view", "home_native_click"))
                .addNativePlacement("NATIVE_SETTINGS", new PlacementConfig(
                        Arrays.asList(AdConstants.TEST_NATIVE_AD_ID),
                        true, "settings_native_view", "settings_native_click"))

                // Carousel: 3 ads, slides every 4 seconds
                .addNativePlacement("NATIVE_CAROUSEL", new PlacementConfig.Builder()
                        .setAdUnitIds(Arrays.asList(
                                AdConstants.TEST_NATIVE_AD_ID,
                                AdConstants.TEST_NATIVE_AD_ID,
                                AdConstants.TEST_NATIVE_AD_ID))
                        .setCarouselSlideIntervalSeconds(4)
                        .setViewEventName("carousel_native_view")
                        .setClickEventName("carousel_native_click")
                        .setEnabled(true)
                        .build())

                // --- Interstitial placements ---
                // INTER_HOME: WATERFALL with full-screen native backup
                .addInterstitialPlacement("INTER_HOME", new PlacementConfig.Builder()
                        .setAdUnitIds(Arrays.asList(AdConstants.TEST_INTERSTITIAL_AD_ID))
                        .setLoadMode(LoadMode.WATERFALL)
                        .setBackupNativePlacementKey("NATIVE_INTER_BACKUP")
                        .setBackupCountdownSeconds(5)
                        .setViewEventName("home_inter_view")
                        .setClickEventName("home_inter_click")
                        .setEnabled(true)
                        .build())
                // INTER_SAVE: SINGLE mode (no waterfall, fail fast)
                .addInterstitialPlacement("INTER_SAVE", new PlacementConfig.Builder()
                        .setAdUnitIds(Arrays.asList(AdConstants.TEST_INTERSTITIAL_AD_ID))
                        .setLoadMode(LoadMode.SINGLE)
                        .setViewEventName("save_inter_view")
                        .setClickEventName("save_inter_click")
                        .setEnabled(true)
                        .build())

                // Full-screen native backup used when INTER_HOME fails
                .addNativePlacement("NATIVE_INTER_BACKUP", new PlacementConfig(
                        Arrays.asList(AdConstants.TEST_NATIVE_AD_ID),
                        true, "inter_backup_view", "inter_backup_click"))

                // --- Reward placements ---
                .addRewardPlacement("REWARD_BONUS", new PlacementConfig(
                        Arrays.asList(AdConstants.TEST_REWARD_AD_ID),
                        true, "bonus_reward_view", "bonus_reward_click"))
                .addRewardPlacement("REWARD_UNLOCK", new PlacementConfig(
                        Arrays.asList(AdConstants.TEST_REWARD_AD_ID),
                        true, "unlock_reward_view", "unlock_reward_click"))

                // --- Banner placements ---
                .addBannerPlacement("BANNER_HOME", new PlacementConfig(
                        Arrays.asList(AdConstants.TEST_BANNER_AD_ID),
                        true, "home_banner_view", "home_banner_click"))

                // --- Default fallback pool ---
                .setDefaultNativeAdId(AdConstants.TEST_NATIVE_AD_ID)
                .setDefaultInterstitialAdId(AdConstants.TEST_INTERSTITIAL_AD_ID)
                .setDefaultRewardAdId(AdConstants.TEST_REWARD_AD_ID)
                .setDefaultBannerAdId(AdConstants.TEST_BANNER_AD_ID)

                // --- App Open ads ---
                .setAppOpenAdIds(Collections.singletonList(AdConstants.TEST_APP_OPEN_AD_ID))

                // --- Timing ---
                .setInterstitialCooldownSeconds(30)
                .setNativeRefreshIntervalSeconds(20)
                .setNativeAutoRefreshEnabled(true)

                // --- Ad Preloading (v1.5.1) ---
                .setPreloadingEnabled(true)
                .setPreloadBufferSize(2)

                // --- Remote Config ---
                .setRemoteConfigEnabled(true)
                .setRemoteConfigDefaultsResId(R.xml.remote_config_defaults)

                // --- Facebook SDK (optional) ---
                .setFacebookEnabled(false)

                // --- Billing ---
                .setPurchaseProducts(Arrays.asList(
                        new PurchaseModel("premium_monthly", PurchaseModel.ProductType.SUBS)
                ))

                .build();

        AdoreAds.init(this, config);

        // Configure Adjust event tokens (optional)
        AdjustEvents.getInstance().setTokens("your_ad_impression_token", "your_purchase_token");

        Log.i(TAG, "AdoreAds v1.2.0 initialized");
    }
}
