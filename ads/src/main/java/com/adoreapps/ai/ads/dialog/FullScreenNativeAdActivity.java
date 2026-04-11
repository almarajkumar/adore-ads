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
import com.adoreapps.ai.ads.AdoreAds;
import com.adoreapps.ai.ads.R;
import com.adoreapps.ai.ads.manager.NativeAdManager;
import com.adoreapps.ai.ads.model.AdsResponse;
import com.adoreapps.ai.ads.settings.AdConstants;
import com.google.android.gms.ads.nativead.NativeAd;

/**
 * Full-screen Activity for displaying a native ad with countdown timer and skip button.
 *
 * <h3>Usage:</h3>
 * <pre>
 * FullScreenNativeAdActivity.show(activity, "NATIVE_SPLASH", 5, true);
 * </pre>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Countdown timer (e.g. "5", "4", "3"...) before skip button appears</li>
 *   <li>Optional skip button (can be replaced with close button)</li>
 *   <li>Auto-close after countdown if no skip button</li>
 *   <li>Uses preloaded ad from NativeAdManager cache if available</li>
 * </ul>
 */
public class FullScreenNativeAdActivity extends Activity {

    private static final String EXTRA_PLACEMENT = "placement";
    private static final String EXTRA_COUNTDOWN = "countdown";
    private static final String EXTRA_SHOW_SKIP = "show_skip";

    public static final int RESULT_AD_SHOWN = 1;
    public static final int RESULT_AD_SKIPPED = 2;
    public static final int RESULT_AD_FAILED = 3;

    private CountDownTimer countDownTimer;

    /**
     * Launch the full-screen native ad activity.
     *
     * @param activity        Host activity
     * @param placementKey    Native placement key (must be preloaded or registered)
     * @param countdownSeconds Seconds before skip button appears (0 = skip immediately visible)
     * @param showSkipButton  If true, shows skip button after countdown. If false, auto-closes after countdown.
     */
    public static void show(Activity activity, String placementKey, int countdownSeconds, boolean showSkipButton) {
        Intent intent = new Intent(activity, FullScreenNativeAdActivity.class);
        intent.putExtra(EXTRA_PLACEMENT, placementKey);
        intent.putExtra(EXTRA_COUNTDOWN, countdownSeconds);
        intent.putExtra(EXTRA_SHOW_SKIP, showSkipButton);
        activity.startActivityForResult(intent, 100);
    }

    /**
     * Launch with default countdown (5 seconds) and skip button enabled.
     */
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

        setContentView(R.layout.adore_native_full_screen);

        String placement = getIntent().getStringExtra(EXTRA_PLACEMENT);
        int countdown = getIntent().getIntExtra(EXTRA_COUNTDOWN, AdConstants.FULL_SCREEN_COUNTDOWN_SECONDS);
        boolean showSkip = getIntent().getBooleanExtra(EXTRA_SHOW_SKIP, true);

        TextView tvCountdown = findViewById(R.id.tv_countdown);
        TextView btnSkip = findViewById(R.id.btn_skip);

        // Load or use preloaded native ad
        FrameLayout container = findViewById(android.R.id.content);
        NativeAdManager nativeManager = NativeAdManager.getInstance();

        nativeManager.loadAndShow(
                this,
                container,
                null,
                R.layout.adore_native_full_screen,
                placement != null ? placement : "NATIVE_SPLASH",
                new AdCallback() {
                    @Override
                    public void onNativeAds(com.adoreapps.ai.ads.wrapper.ApAdNative nativeAd, String unitID) {
                        super.onNativeAds(nativeAd, unitID);
                        startCountdown(countdown, showSkip, tvCountdown, btnSkip);
                    }

                    @Override
                    public void onAdFailedToLoad(com.adoreapps.ai.ads.wrapper.ApAdError error) {
                        super.onAdFailedToLoad(error);
                        setResult(RESULT_AD_FAILED);
                        finish();
                    }
                }
        );

        // Skip/close button action
        btnSkip.setOnClickListener(v -> {
            setResult(RESULT_AD_SKIPPED);
            finish();
        });
    }

    private void startCountdown(int seconds, boolean showSkipAfter,
                                 TextView tvCountdown, TextView btnSkip) {
        if (seconds <= 0) {
            if (showSkipAfter) {
                btnSkip.setText("Skip");
                btnSkip.setVisibility(View.VISIBLE);
            } else {
                setResult(RESULT_AD_SHOWN);
                finish();
            }
            return;
        }

        tvCountdown.setVisibility(View.VISIBLE);
        tvCountdown.setText(String.valueOf(seconds));

        countDownTimer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int remaining = (int) (millisUntilFinished / 1000) + 1;
                tvCountdown.setText(String.valueOf(remaining));
            }

            @Override
            public void onFinish() {
                tvCountdown.setVisibility(View.GONE);
                if (showSkipAfter) {
                    btnSkip.setText("Skip");
                    btnSkip.setVisibility(View.VISIBLE);
                } else {
                    btnSkip.setText("Close");
                    btnSkip.setVisibility(View.VISIBLE);
                }
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
    }

    @Override
    public void onBackPressed() {
        // Block back press during countdown
    }
}
