package com.adoreapps.sampleads;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.adoreapps.ai.ads.AdoreAds;
import com.adoreapps.ai.ads.AdoreLayouts;
import com.adoreapps.ai.ads.PlacementConfig;
import com.adoreapps.ai.ads.dialog.FullScreenNativeAdActivity;
import com.adoreapps.ai.ads.interfaces.AdFinished;
import com.adoreapps.ai.ads.settings.AdConstants;
import com.facebook.shimmer.ShimmerFrameLayout;

import java.util.Arrays;

/**
 * Main screen demonstrating all ad types with bundled layouts:
 * - Native ad: Large, Medium, Small layouts with matching shimmers
 * - Full-screen native ad with countdown + skip
 * - Banner, Interstitial, Reward
 * - Runtime placement management
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FrameLayout nativeAdContainer = findViewById(R.id.native_ad_container);
        ShimmerFrameLayout shimmer = findViewById(R.id.shimmer_home);
        FrameLayout bannerContainer = findViewById(R.id.banner_ad_container);
        FrameLayout carouselContainer = findViewById(R.id.carousel_container);
        Button btnInterstitial = findViewById(R.id.btn_show_interstitial);
        Button btnFullScreen = findViewById(R.id.btn_show_fullscreen);
        Button btnReward = findViewById(R.id.btn_show_reward);
        Button btnAddPlacement = findViewById(R.id.btn_add_placement);
        Button btnRemovePlacement = findViewById(R.id.btn_remove_placement);
        TextView tvRemoteConfig = findViewById(R.id.tv_remote_config);
        TextView tvPremium = findViewById(R.id.tv_premium_status);

        // --- Premium status ---
        tvPremium.setText("Premium: " + AdoreAds.getInstance().isPremium());

        // --- Remote config status ---
        boolean adsEnabled = AdoreAds.getInstance().remoteConfig()
                .getBoolean("ad_enabled", true);
        boolean autoRefreshOn = AdoreAds.getInstance().remoteConfig()
                .getBoolean("ad_native_auto_refresh_enabled", true);
        tvRemoteConfig.setText("Ads: " + adsEnabled + " | AutoRefresh: " + autoRefreshOn);

        // --- Native Ad with MEDIUM layout + auto-refresh ---
        AdoreAds.getInstance().nativeAds().showWithAutoRefresh(
                this,
                nativeAdContainer,
                shimmer,
                AdoreLayouts.NATIVE_MEDIUM,  // Bundled medium layout (120dp media)
                "NATIVE_HOME",
                "MainActivity",
                this
        );

        // --- Banner Ad ---
        AdoreAds.getInstance().bannerAds().loadAndShowBannerAd(
                this, "BANNER_HOME", bannerContainer
        );

        // --- Native Ad Carousel (v1.5.2) ---
        // Loads all 3 ad IDs in parallel, shows in ViewPager2 with auto-slide
        AdoreAds.getInstance().nativeAds().showCarousel(
                this,
                carouselContainer,
                AdoreLayouts.NATIVE_MEDIUM,
                "NATIVE_CAROUSEL",
                "MainActivity_Carousel",
                this   // LifecycleOwner
        );

        // --- Interstitial Ad ---
        btnInterstitial.setOnClickListener(v -> {
            AdoreAds.getInstance().interstitialAds().loadAndShowByPlacement(
                    this, "INTER_HOME",
                    new AdFinished() {
                        @Override
                        public void onAdFinished() {
                            Toast.makeText(MainActivity.this, "Interstitial done!", Toast.LENGTH_SHORT).show();
                        }
                        @Override
                        public void onAdFailed() {
                            Toast.makeText(MainActivity.this, "Interstitial failed", Toast.LENGTH_SHORT).show();
                        }
                    }
            );
        });

        // --- Full-Screen Native Ad with countdown ---
        btnFullScreen.setOnClickListener(v -> {
            // Show full-screen native with 5s countdown then skip button
            FullScreenNativeAdActivity.show(this, "NATIVE_HOME", 5, true);
        });

        // --- Reward Ad ---
        btnReward.setOnClickListener(v ->
                startActivity(new Intent(this, RewardActivity.class)));

        // --- Runtime Placement Add (using SMALL layout) ---
        btnAddPlacement.setOnClickListener(v -> {
            AdoreAds.getInstance().addNativePlacement("NATIVE_DYNAMIC",
                    new PlacementConfig(
                            Arrays.asList(AdConstants.TEST_NATIVE_AD_ID),
                            true, "dynamic_view", "dynamic_click"));
            Toast.makeText(this, "Added NATIVE_DYNAMIC", Toast.LENGTH_SHORT).show();
        });

        // --- Runtime Placement Remove ---
        btnRemovePlacement.setOnClickListener(v -> {
            AdoreAds.getInstance().removePlacement("NATIVE_DYNAMIC");
            Toast.makeText(this, "Removed NATIVE_DYNAMIC", Toast.LENGTH_SHORT).show();
        });
    }
}
