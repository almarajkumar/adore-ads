package com.adoreapps.ai.ads.manager;

import android.app.Activity;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.adoreapps.ai.ads.AdCallback;
import com.adoreapps.ai.ads.AdoreAdsConfig;
import com.adoreapps.ai.ads.PlacementConfig;
import com.adoreapps.ai.ads.core.AdsMobileAdsManager;
import com.adoreapps.ai.ads.settings.AdConstants;
import com.adoreapps.ai.ads.settings.AdSettingsStore;
import com.adoreapps.ai.ads.settings.AdUnitsConfig;
import com.adoreapps.ai.ads.settings.AdsConfig;
import com.adoreapps.ai.ads.wrapper.ApAdError;
import com.adoreapps.ai.ads.wrapper.ApRewardItem;
import com.adoreapps.ai.ads.consent.ConsentManager;
import com.adoreapps.ai.ads.billing.PurchaseManager;
import com.adoreapps.ai.ads.interfaces.AdFinished;
import com.adoreapps.ai.ads.dialog.LoadingAdsDialog;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RewardAdManager {

    private static volatile RewardAdManager instance;

    private RewardAdManager() {}

    public static RewardAdManager getInstance() {
        if (instance == null) {
            synchronized (RewardAdManager.class) {
                if (instance == null) {
                    instance = new RewardAdManager();
                }
            }
        }
        return instance;
    }

    // Placement enable/disable flags (instance, not static)
    private boolean showRewardStore1 = false;
    private boolean showRewardStore2 = false;
    private boolean showRewardSticker = true;
    private boolean showRewardWtm = true;
    private boolean isUserEarnedReward = false;

    // Placement map for config-based loading by key
    private final Map<String, AdUnitsConfig> rewardAdMap = new ConcurrentHashMap<>();

    // Cached reward ad for instant show
    private RewardedAd cachedRewardAd = null;
    private String cachedAdPlace = null;
    private long cachedRewardLoadTime = 0;

    // =========================================================
    // RUNTIME PLACEMENT MANAGEMENT
    // =========================================================

    /**
     * Add or replace a single reward ad placement at runtime.
     */
    public void addPlacement(String key, PlacementConfig config) {
        if (key == null || config == null) return;
        if (config.isEnabled() && !config.getAdUnitIds().isEmpty()) {
            rewardAdMap.put(key, new AdUnitsConfig(
                    config.getAdUnitIds(),
                    config.getViewEventName(),
                    config.getClickEventName()
            ));
        }
    }

    /**
     * Remove a reward ad placement.
     */
    public void removePlacement(String key) {
        if (key != null) rewardAdMap.remove(key);
    }

    /**
     * Check if a placement is registered.
     */
    public boolean hasPlacement(String key) {
        return rewardAdMap.containsKey(key);
    }

    /**
     * Load and show a reward ad by placement key (registered via addPlacement or config).
     */
    public void loadAndShowByPlacement(Activity activity, String placementKey, AdFinished adFinished) {
        AdUnitsConfig config = rewardAdMap.get(placementKey);
        if (config == null || config.adUnitIds.isEmpty()) {
            adFinished.onAdFailed();
            return;
        }
        isUserEarnedReward = false;
        loadAndShowRewardAdWithIds(activity, new ArrayList<>(config.adUnitIds), adFinished);
    }

    /**
     * Populate reward placements from AdoreAdsConfig.
     */
    public void populateFromConfig(AdoreAdsConfig adoreConfig) {
        rewardAdMap.clear();
        for (Map.Entry<String, PlacementConfig> entry : adoreConfig.getRewardPlacements().entrySet()) {
            PlacementConfig pc = entry.getValue();
            if (pc.isEnabled() && !pc.getAdUnitIds().isEmpty()) {
                rewardAdMap.put(entry.getKey(), new AdUnitsConfig(
                        pc.getAdUnitIds(),
                        pc.getViewEventName(),
                        pc.getClickEventName()
                ));
            }
        }
    }

    // =========================================================
    // SETTERS / GETTERS for placement flags
    // =========================================================

    public void setShowRewardStore1(boolean show) { this.showRewardStore1 = show; }
    public void setShowRewardStore2(boolean show) { this.showRewardStore2 = show; }
    public void setShowRewardSticker(boolean show) { this.showRewardSticker = show; }
    public void setShowRewardWtm(boolean show) { this.showRewardWtm = show; }

    public boolean isShowRewardStore1() { return showRewardStore1; }
    public boolean isShowRewardStore2() { return showRewardStore2; }
    public boolean isShowRewardSticker() { return showRewardSticker; }
    public boolean isShowRewardWtm() { return showRewardWtm; }

    // =========================================================
    // PRELOAD
    // =========================================================

    public void preloadRewardAd(Activity activity, boolean enabledHigh, boolean enabledLow, String adPlace) {
        if (PurchaseManager.getInstance().isPurchased() || cachedRewardAd != null) return;
        if (!ConsentManager.getInstance(activity).canRequestAds()) return;
        if (!enabledHigh && !enabledLow) return;

        ArrayList<String> rewardAdIds = buildAdIdList(enabledHigh, enabledLow, adPlace);
        if (rewardAdIds.isEmpty()) return;

        AdsMobileAdsManager.getInstance().loadAlternateRewardAd(
                activity,
                rewardAdIds,
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                        cachedRewardAd = rewardedAd;
                        cachedAdPlace = adPlace;
                        cachedRewardLoadTime = System.currentTimeMillis();
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        // Silent — preload is best-effort
                    }
                }
        );
    }

    private ArrayList<String> buildAdIdList(boolean enabledHigh, boolean enabledLow, String adPlace) {
        String highAdId;
        String lowAdId;
        switch (adPlace) {
            case "watermark":
                highAdId = AdsConfig.reward_wtm_704;
                lowAdId = AdsConfig.reward_wtm_704;
                break;
            case "sticker":
                highAdId = AdsConfig.reward_sticker_702;
                lowAdId = AdsConfig.reward_sticker_702;
                break;
            case "store":
                highAdId = AdsConfig.reward_store_502_1;
                lowAdId = AdsConfig.reward_store_502_2;
                break;
            case "generate":
                highAdId = AdsConfig.miniature_reward_high_501;
                lowAdId = AdsConfig.miniature_reward_501;
                break;
            default:
                highAdId = AdsConfig.reward_store_502_1;
                lowAdId = AdsConfig.reward_store_502_2;
        }
        ArrayList<String> ids = new ArrayList<>();
        if (enabledHigh) ids.add(highAdId);
        if (enabledLow) ids.add(lowAdId);
        return ids;
    }

    // =========================================================
    // LOAD & SHOW
    // =========================================================

    public void loadAndShowRewardAd(
            Activity activity,
            AdFinished onRewardComplete,
            boolean enabledHighReward,
            boolean enabledLowReward,
            String adPlace) {

        isUserEarnedReward = false;
        if (PurchaseManager.getInstance().isPurchased()) {
            onRewardComplete.onAdFinished();
            return;
        }
        if (!ConsentManager.getInstance(activity).canRequestAds()) {
            onRewardComplete.onAdFailed();
            return;
        }
        if (!enabledHighReward && !enabledLowReward) {
            onRewardComplete.onAdFinished();
            return;
        }

        LoadingAdsDialog loadingAdsDialog = new LoadingAdsDialog(activity);
        loadingAdsDialog.showWithTimeout(() -> onRewardComplete.onAdFailed());
        AdCallback adCallBack = new AdCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                super.onAdDismissedFullScreenContent();
                loadingAdsDialog.dismiss();
                if (!loadingAdsDialog.isTimedOut()) {
                    if (isUserEarnedReward) {
                        onRewardComplete.onAdFinished();
                    } else {
                        onRewardComplete.onAdFailed();
                    }
                }
            }

            @Override
            public void onUserEarnedReward(ApRewardItem rewardItem) {
                super.onUserEarnedReward(rewardItem);
                isUserEarnedReward = true;
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull ApAdError apAdError) {
                super.onAdFailedToShowFullScreenContent(apAdError);
                loadingAdsDialog.dismiss();
                if (!loadingAdsDialog.isTimedOut()) {
                    onRewardComplete.onAdFailed();
                    Toast.makeText(activity, "Reward Ad Not Available", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onAdFailedToLoad(@NonNull ApAdError apAdError) {
                super.onAdFailedToLoad(apAdError);
                loadingAdsDialog.dismiss();
                if (!loadingAdsDialog.isTimedOut()) {
                    Toast.makeText(activity, "Reward Ad Not Available", Toast.LENGTH_SHORT).show();
                }
            }
        };

        // Use cached ad if available, not expired, and matches placement
        boolean isCachedExpired = (System.currentTimeMillis() - cachedRewardLoadTime) > AdConstants.AD_EXPIRY_MS;
        if (cachedRewardAd != null && isCachedExpired) {
            cachedRewardAd = null;
            cachedAdPlace = null;
            cachedRewardLoadTime = 0;
        }
        if (cachedRewardAd != null && adPlace.equals(cachedAdPlace)) {
            RewardedAd readyAd = cachedRewardAd;
            cachedRewardAd = null;
            cachedAdPlace = null;
            cachedRewardLoadTime = 0;
            loadingAdsDialog.dismiss();
            AdsMobileAdsManager.getInstance().showRewardAd(activity, readyAd, adCallBack);
            preloadRewardAd(activity, enabledHighReward, enabledLowReward, adPlace);
            return;
        }

        ArrayList<String> rewardAdIds = buildAdIdList(enabledHighReward, enabledLowReward, adPlace);
        AdsMobileAdsManager.getInstance().loadAlternateRewardAd(
                activity,
                rewardAdIds,
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        super.onAdFailedToLoad(loadAdError);
                        if (loadingAdsDialog.isTimedOut()) return;
                        RewardedAd defaultAd = DefaultAdPool.getInstance().consumeDefaultRewardAd(activity);
                        if (defaultAd != null) {
                            loadingAdsDialog.dismiss();
                            AdsMobileAdsManager.getInstance().showRewardAd(activity, defaultAd, adCallBack);
                        } else {
                            loadingAdsDialog.dismiss();
                            onRewardComplete.onAdFailed();
                            Toast.makeText(activity, "Reward Ad not available", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                        super.onAdLoaded(rewardedAd);
                        if (loadingAdsDialog.isTimedOut()) return;
                        loadingAdsDialog.dismiss();
                        AdsMobileAdsManager.getInstance().showRewardAd(activity, rewardedAd, adCallBack);
                        preloadRewardAd(activity, enabledHighReward, enabledLowReward, adPlace);
                    }
                }
        );
    }

    /**
     * Load and show a reward ad from a list of ad unit IDs (placement-based).
     */
    private void loadAndShowRewardAdWithIds(Activity activity, ArrayList<String> adIds, AdFinished onRewardComplete) {
        if (PurchaseManager.getInstance().isPurchased()) {
            onRewardComplete.onAdFinished();
            return;
        }
        if (!ConsentManager.getInstance(activity).canRequestAds()) {
            onRewardComplete.onAdFailed();
            return;
        }

        LoadingAdsDialog loadingAdsDialog = new LoadingAdsDialog(activity);
        loadingAdsDialog.showWithTimeout(() -> onRewardComplete.onAdFailed());
        AdCallback adCallBack = new AdCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                super.onAdDismissedFullScreenContent();
                loadingAdsDialog.dismiss();
                if (!loadingAdsDialog.isTimedOut()) {
                    if (isUserEarnedReward) {
                        onRewardComplete.onAdFinished();
                    } else {
                        onRewardComplete.onAdFailed();
                    }
                }
            }

            @Override
            public void onUserEarnedReward(ApRewardItem rewardItem) {
                super.onUserEarnedReward(rewardItem);
                isUserEarnedReward = true;
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull ApAdError apAdError) {
                super.onAdFailedToShowFullScreenContent(apAdError);
                loadingAdsDialog.dismiss();
                if (!loadingAdsDialog.isTimedOut()) {
                    onRewardComplete.onAdFailed();
                }
            }
        };

        AdsMobileAdsManager.getInstance().loadAlternateRewardAd(
                activity,
                adIds,
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        super.onAdFailedToLoad(loadAdError);
                        if (loadingAdsDialog.isTimedOut()) return;
                        RewardedAd defaultAd = DefaultAdPool.getInstance().consumeDefaultRewardAd(activity);
                        if (defaultAd != null) {
                            loadingAdsDialog.dismiss();
                            AdsMobileAdsManager.getInstance().showRewardAd(activity, defaultAd, adCallBack);
                        } else {
                            loadingAdsDialog.dismiss();
                            onRewardComplete.onAdFailed();
                        }
                    }

                    @Override
                    public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                        super.onAdLoaded(rewardedAd);
                        if (loadingAdsDialog.isTimedOut()) return;
                        loadingAdsDialog.dismiss();
                        AdsMobileAdsManager.getInstance().showRewardAd(activity, rewardedAd, adCallBack);
                    }
                }
        );
    }

    // =========================================================
    // CONVENIENCE METHODS
    // =========================================================

    public void loadAndShowWatermarkRewardAd(Activity activity, AdFinished onRewardComplete) {
        loadAndShowRewardAd(activity, onRewardComplete, showRewardWtm, showRewardWtm, "watermark");
    }

    public void loadAndShowGenerateRewardAd(Activity activity, AdFinished onRewardComplete) {
        loadAndShowRewardAd(
                activity,
                onRewardComplete,
                AdSettingsStore.getInstance(activity).getBoolean("show_miniature_reward_1", true),
                AdSettingsStore.getInstance(activity).getBoolean("show_miniature_reward_2", true),
                "generate"
        );
    }

    public void loadAndShowStickerRewardAd(Activity activity, AdFinished onRewardComplete) {
        loadAndShowRewardAd(activity, onRewardComplete, showRewardSticker, showRewardSticker, "sticker");
    }

    public void loadAndShowStoreRewardAd(Activity activity, AdFinished onRewardComplete) {
        loadAndShowRewardAd(activity, onRewardComplete, showRewardStore1, showRewardStore2, "store");
    }
}
