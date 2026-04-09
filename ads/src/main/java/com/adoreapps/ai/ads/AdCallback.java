package com.adoreapps.ai.ads;

import androidx.annotation.NonNull;

import com.adoreapps.ai.ads.wrapper.ApAdError;
import com.adoreapps.ai.ads.wrapper.ApAdNative;
import com.adoreapps.ai.ads.wrapper.ApAdView;
import com.adoreapps.ai.ads.wrapper.ApInterstitialAd;
import com.adoreapps.ai.ads.wrapper.ApRewardItem;

public class AdCallback {

    public AdCallback() {
    }

    public void onAdClosed() {
    }

    public void onNextScreen() {
    }

    public void onAdShowedFullScreenContent() {
    }

    public void onAdFailedToLoad(@NonNull ApAdError apAdError) {
    }

    public void onAdLoaded() {
    }

    public void onAdLoaded(ApAdView adView) {
    }

    public void onResultInterstitialAd(ApInterstitialAd interstitialAd) {
    }

    public void onNativeAds(ApAdNative nativeAd) {
    }

    public void onNativeAds(ApAdNative nativeAd, String unitID) {
    }

    public void onAdFailedToShowFullScreenContent(@NonNull ApAdError apAdError) {
    }

    public void onUserEarnedReward(ApRewardItem rewardItem) {
    }

    public void onAdClicked() {
    }

    public void onAdImpression() {
    }

    public void onAdDismissedFullScreenContent() {
    }
}
