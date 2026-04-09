package com.adoreapps.ai.ads.wrapper;

import com.google.android.gms.ads.rewarded.RewardItem;

public class ApRewardItem {
    private RewardItem rewardItem;

    public ApRewardItem(RewardItem rewardItem) {
        this.rewardItem = rewardItem;
    }

    public RewardItem getRewardItem() {
        return this.rewardItem;
    }
}
