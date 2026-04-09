package com.adoreapps.ai.ads.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.adoreapps.ai.ads.R;
import com.adoreapps.ai.ads.settings.AdConstants;
import com.airbnb.lottie.LottieAnimationView;

public class LoadingAdsDialog extends Dialog {
    private static final long DEFAULT_TIMEOUT_MS = AdConstants.LOADING_DIALOG_TIMEOUT_MS;

    LottieAnimationView animationView;
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;
    private boolean isResolved = false;
    private boolean timedOut = false;

    public LoadingAdsDialog(Context context) {
        super(context);

        WindowManager.LayoutParams wlmp = getWindow().getAttributes();
        wlmp.gravity = Gravity.CENTER;
        getWindow().setAttributes(wlmp);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        setTitle(null);
        setCancelable(false);
        setOnCancelListener(null);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        View view = LayoutInflater.from(context).inflate(
                R.layout.loading_ads, null);
        animationView = view.findViewById(R.id.lottie_animation_view);
        animationView.setVisibility(View.VISIBLE);
        setContentView(view);
    }

    public void showWithTimeout(Runnable onTimeout) {
        showWithTimeout(DEFAULT_TIMEOUT_MS, onTimeout);
    }

    public void startTimeoutOnly(Runnable onTimeout) {
        startTimeoutOnly(DEFAULT_TIMEOUT_MS, onTimeout);
    }

    public void startTimeoutOnly(long timeoutMs, Runnable onTimeout) {
        isResolved = false;
        timedOut = false;
        timeoutRunnable = () -> {
            if (!isResolved) {
                isResolved = true;
                timedOut = true;
                if (onTimeout != null) {
                    onTimeout.run();
                }
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, timeoutMs);
    }

    public void showWithTimeout(long timeoutMs, Runnable onTimeout) {
        isResolved = false;
        timedOut = false;
        show();
        timeoutRunnable = () -> {
            if (!isResolved) {
                isResolved = true;
                timedOut = true;
                dismissSafely();
                if (onTimeout != null) {
                    onTimeout.run();
                }
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, timeoutMs);
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    @Override
    public void dismiss() {
        isResolved = true;
        cancelTimeout();
        dismissSafely();
    }

    private void dismissSafely() {
        try {
            if (isShowing()) {
                super.dismiss();
            }
        } catch (Exception ignored) {
        }
    }

    private void cancelTimeout() {
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }
}