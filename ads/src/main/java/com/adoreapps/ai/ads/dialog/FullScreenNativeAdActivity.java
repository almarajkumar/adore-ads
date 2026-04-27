package com.adoreapps.ai.ads.dialog;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.adoreapps.ai.ads.AdCallback;
import com.adoreapps.ai.ads.R;
import com.adoreapps.ai.ads.core.AppOpenAdManager;
import com.adoreapps.ai.ads.event.AdType;
import com.adoreapps.ai.ads.event.FirebaseAnalyticsEvents;
import com.adoreapps.ai.ads.manager.NativeAdManager;
import com.adoreapps.ai.ads.settings.AdConstants;

/**
 * Full-screen Activity for displaying a native ad with countdown timer and skip/close button.
 *
 * <h3>Usage:</h3>
 * <pre>
 * FullScreenNativeAdActivity.show(activity, "NATIVE_SPLASH", 5, true);
 * </pre>
 */
public class FullScreenNativeAdActivity extends Activity {

    private static final String EXTRA_PLACEMENT = "placement";
    private static final String EXTRA_COUNTDOWN = "countdown";
    private static final String EXTRA_SHOW_SKIP = "show_skip";

    public static final int RESULT_AD_SHOWN = 1;
    public static final int RESULT_AD_SKIPPED = 2;
    public static final int RESULT_AD_FAILED = 3;

    private CountDownTimer countDownTimer;
    private String placementKey;
    private boolean countdownCompleted = false;
    private long countdownStartMs = 0L;
    private int initialCountdown = 0;

    /**
     * Launch the full-screen native ad activity.
     *
     * @param activity         Host activity
     * @param placementKey     Native placement key (must be preloaded or registered)
     * @param countdownSeconds Seconds before skip/close button appears (0 = immediately visible)
     * @param showSkipButton   true = "Skip" button after countdown, false = "Close" button
     */
    public static void show(Activity activity, String placementKey, int countdownSeconds, boolean showSkipButton) {
        Intent intent = new Intent(activity, FullScreenNativeAdActivity.class);
        intent.putExtra(EXTRA_PLACEMENT, placementKey);
        intent.putExtra(EXTRA_COUNTDOWN, countdownSeconds);
        intent.putExtra(EXTRA_SHOW_SKIP, showSkipButton);
        activity.startActivityForResult(intent, 100);
    }

    /** Launch with default countdown (5 seconds) and skip button. */
    public static void show(Activity activity, String placementKey) {
        show(activity, placementKey, AdConstants.FULL_SCREEN_COUNTDOWN_SECONDS, true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        // Prevent app open ad from showing over this activity
        if (AppOpenAdManager.getInstance().isInitialized()) {
            AppOpenAdManager.getInstance().disableAppResume();
        }

        // Use the wrapper layout (ad container + overlay countdown/skip)
        setContentView(R.layout.adore_activity_full_screen_native);

        String placement = getIntent().getStringExtra(EXTRA_PLACEMENT);
        this.placementKey = placement != null ? placement : "NATIVE_SPLASH";
        int countdown = getIntent().getIntExtra(EXTRA_COUNTDOWN, AdConstants.FULL_SCREEN_COUNTDOWN_SECONDS);
        boolean showSkip = getIntent().getBooleanExtra(EXTRA_SHOW_SKIP, true);

        FrameLayout adContainer = findViewById(R.id.ad_container);
        TextView tvCountdown = findViewById(R.id.tv_countdown);
        TextView btnSkip = findViewById(R.id.btn_skip);

        // Load and show native ad in the container, using the full-screen native layout
        NativeAdManager.getInstance().loadAndShow(
                this,
                adContainer,
                null,
                R.layout.adore_native_full_screen,
                placement != null ? placement : "NATIVE_SPLASH",
                new AdCallback() {
                    @Override
                    public void onNativeAds(com.adoreapps.ai.ads.wrapper.ApAdNative nativeAd, String unitID) {
                        super.onNativeAds(nativeAd, unitID);
                        // Ad loaded — start countdown overlay
                        startCountdown(countdown, showSkip, tvCountdown, btnSkip);
                    }

                    @Override
                    public void onAdFailedToLoad(com.adoreapps.ai.ads.wrapper.ApAdError error) {
                        super.onAdFailedToLoad(error);
                        FirebaseAnalyticsEvents.getInstance().logShowFailed(
                                FullScreenNativeAdActivity.this,
                                placementKey, null, AdType.NATIVE,
                                -1, "fullscreen_native_load_failed");
                        setResult(RESULT_AD_FAILED);
                        finish();
                    }
                }
        );

        // Skip/close button action
        btnSkip.setOnClickListener(v -> {
            if (countDownTimer != null) countDownTimer.cancel();
            long elapsed = countdownStartMs > 0
                    ? (System.currentTimeMillis() - countdownStartMs) / 1000L
                    : initialCountdown;
            FirebaseAnalyticsEvents.getInstance().logSkipped(
                    FullScreenNativeAdActivity.this,
                    placementKey, null, AdType.NATIVE, elapsed);
            setResult(RESULT_AD_SKIPPED);
            finish();
        });
    }

    private void startCountdown(int seconds, boolean showSkipAfter,
                                 TextView tvCountdown, TextView btnSkip) {
        if (seconds <= 0) {
            btnSkip.setText(showSkipAfter ? "Skip" : "Close");
            btnSkip.setVisibility(View.VISIBLE);
            setResult(RESULT_AD_SHOWN);
            return;
        }

        tvCountdown.setVisibility(View.VISIBLE);
        tvCountdown.setText(String.valueOf(seconds));
        countdownStartMs = System.currentTimeMillis();
        initialCountdown = seconds;

        countDownTimer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int remaining = (int) (millisUntilFinished / 1000) + 1;
                tvCountdown.setText(String.valueOf(remaining));
            }

            @Override
            public void onFinish() {
                tvCountdown.setVisibility(View.GONE);
                btnSkip.setText(showSkipAfter ? "Skip" : "Close");
                btnSkip.setVisibility(View.VISIBLE);
                countdownCompleted = true;
                setResult(RESULT_AD_SHOWN);
            }
        };
        countDownTimer.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        // Re-enable app open ads
        if (AppOpenAdManager.getInstance().isInitialized()) {
            AppOpenAdManager.getInstance().enableAppResume();
        }
    }

    @Override
    public void onBackPressed() {
        // Block back press during countdown
    }
}
