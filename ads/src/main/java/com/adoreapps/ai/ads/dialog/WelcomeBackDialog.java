package com.adoreapps.ai.ads.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.adoreapps.ai.ads.R;

public class WelcomeBackDialog extends Dialog {

    public WelcomeBackDialog(@NonNull Context context) {
        super(context, R.style.Theme_App_FullscreenDialog); // apply fullscreen theme
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_welcome_back);

        // Force fullscreen programmatically
        if (getWindow() != null) {
            getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        }
    }
}

