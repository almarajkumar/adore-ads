package com.adoreapps.sampleads;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.adoreapps.ai.ads.AdCallback;
import com.adoreapps.ai.ads.AdoreAds;
import com.adoreapps.ai.ads.AdoreLayouts;
import com.adoreapps.ai.ads.consent.ConsentManager;
import com.adoreapps.ai.ads.core.AppOpenAdManager;
import com.adoreapps.ai.ads.manager.DefaultAdPool;
import com.adoreapps.ai.ads.settings.BannerSize;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.util.Arrays;

/**
 * Splash screen demonstrating v1.2.0 features:
 * - UMP consent gathering
 * - Remote config fetch (activate-first: instant cached, background fetch)
 * - Native ad load+show
 * - Default ad pool initialization (depth-2, retry backoff)
 * - App open ad setup
 * - Batch preloading (staggered for network efficiency)
 */
public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    FrameLayout nativeAdContainer;
    ShimmerFrameLayout shimmer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        TextView statusText = findViewById(R.id.tv_status);
        nativeAdContainer = findViewById(R.id.native_ad_container);
        shimmer = findViewById(R.id.shimmer_splash);

        statusText.setText("Gathering consent...");

        // Step 1: Gather consent
        ConsentManager.getInstance(this).gatherConsent(this, error -> {
            if (error != null) {
                Log.w(TAG, "Consent error: " + error.getMessage());
            }

            statusText.setText("Fetching remote config...");

            // Step 2: Fetch remote config
            // Uses activate-first strategy: cached values apply instantly,
            // fresh values fetched in background for next session.

            FirebaseRemoteConfig firebase = AdoreAds.getInstance().remoteConfig().getRemoteConfig();
            boolean firstLaunch = firebase.getInfo().getLastFetchStatus()
                    == FirebaseRemoteConfig.LAST_FETCH_STATUS_NO_FETCH_YET;

            if (firstLaunch) {
                // First launch — block for fresh values
                AdoreAds.getInstance().remoteConfig().fetchAndActivateNow(success -> {
                    Log.i(TAG, "Remote config ready (activate-first): " + success);
                    AdoreAds.getInstance().applyRemoteConfig();
                    statusText.setText("Loading ads...");
                    loadAds();
                });
            } else {
                // Returning user — instant cached values, background fetch
                AdoreAds.getInstance().fetchRemoteConfig(success -> {
                    Log.i(TAG, "Remote config ready (activate-first): " + success);
                    statusText.setText("Loading ads...");
                    loadAds();
                });
            }
        });
    }

    public void loadAds() {
        AdoreAds
                .getInstance()
                .bannerAds()
                .setDefaultBannerSize(BannerSize.ADAPTIVE);
        // Step 3: Initialize app open ads
        AppOpenAdManager.getInstance().init(
                getApplication(),
                AdoreAds.getInstance().getConfig().getAppOpenAdIds()
        );

        AppOpenAdManager.getInstance().disableLoadAtActivity(SplashActivity.class);

        // Step 4: Initialize default ad pool (depth-2 cache with retry backoff)
        DefaultAdPool.getInstance().init(this);

        // Step 5: Show native ad on splash (using bundled large layout)
        AdoreAds.getInstance().nativeAds().loadAndShow(
                this,
                nativeAdContainer,
                shimmer,
                AdoreLayouts.NATIVE_LARGE,
                "NATIVE_SPLASH"
        );

        // Step 6: Batch preload ads for upcoming screens (staggered 500ms apart)
        AdoreAds.getInstance().nativeAds().preloadMultipleStaggered(
                this,
                Arrays.asList("NATIVE_HOME", "NATIVE_SETTINGS"),
                500
        );

        // Navigate to main after delay
        nativeAdContainer.postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }, 3000);
    }
}
