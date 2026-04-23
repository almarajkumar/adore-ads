package com.adoreapps.sampleads;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.adoreapps.ai.ads.AdoreAds;
import com.adoreapps.ai.ads.interfaces.AdFinished;

/**
 * Demonstrates reward ads (v1.2.0):
 * - loadAndShowByPlacement (config-based, no hardcoded switch-case)
 * - Preload for instant show
 * - DefaultAdPool fallback with shared native + retry backoff
 */
public class RewardActivity extends AppCompatActivity {

    private static final String TAG = "RewardActivity";
    private int rewardCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reward);

        Button btnWatchAd = findViewById(R.id.btn_watch_reward);
        Button btnPreload = findViewById(R.id.btn_preload_reward);
        TextView tvRewardCount = findViewById(R.id.tv_reward_count);

        updateRewardCount(tvRewardCount);

        // --- Preload reward ad (cached for instant show) ---
        btnPreload.setOnClickListener(v -> {
            AdoreAds.getInstance().rewardAds().preloadRewardAd(
                    this, true, true, "REWARD_BONUS"
            );
            Toast.makeText(this, "Preloading reward ad...", Toast.LENGTH_SHORT).show();
        });

        // --- Show reward ad by placement key ---
        btnWatchAd.setOnClickListener(v -> {
            AdoreAds.getInstance().rewardAds().loadAndShowByPlacement(
                    this,
                    "REWARD_BONUS",
                    new AdFinished() {
                        @Override
                        public void onAdFinished() {
                            rewardCount++;
                            updateRewardCount(tvRewardCount);
                            Log.i(TAG, "Reward earned! Total: " + rewardCount);
                            Toast.makeText(RewardActivity.this,
                                    "Reward earned! Total: " + rewardCount,
                                    Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onAdFailed() {
                            Log.w(TAG, "Reward ad failed");
                            Toast.makeText(RewardActivity.this,
                                    "Reward ad not available",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
            );
        });
    }

    private void updateRewardCount(TextView tv) {
        tv.setText("Rewards earned: " + rewardCount);
    }
}
