package com.adoreapps.ai.ads.manager;

import com.adoreapps.ai.ads.AdoreAdsConfig;
import com.adoreapps.ai.ads.PlacementConfig;
import com.adoreapps.ai.ads.settings.AdStatus;
import com.adoreapps.ai.ads.settings.AdUnitsConfig;
import com.adoreapps.ai.ads.settings.AdConstants;
import com.adoreapps.ai.ads.settings.AdSettingsStore;
import com.adoreapps.ai.ads.settings.AdsConfig;
import com.adoreapps.ai.ads.core.AdsMobileAdsManager;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import com.adoreapps.ai.ads.AdCallback;
import com.adoreapps.ai.ads.core.AdsMobileAdsManager;
import com.adoreapps.ai.ads.core.NetworkUtils;

import com.adoreapps.ai.ads.model.AdsResponse;
import com.adoreapps.ai.ads.wrapper.ApAdError;
import com.adoreapps.ai.ads.wrapper.ApAdNative;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.adoreapps.ai.ads.consent.ConsentManager;
import com.adoreapps.ai.ads.billing.PurchaseManager;

import com.facebook.shimmer.ShimmerFrameLayout;

import com.google.android.gms.ads.nativead.NativeAd;


import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class NativeAdManager {

    private static final String TAG = "NativeAdRefresh";

    // Position constants
    public static final String NATIVE_SPLASH = "NATIVE_SPLASH";
    public static final String NATIVE_SPLASH_FULL = "NATIVE_SPLASH_FULL";
    public static final String LANGUAGE_ONE_NATIVE_AD = "LANGUAGE_ONE_NATIVE_AD";
    public static final String LANGUAGE_TWO_NATIVE_AD = "LANGUAGE_TWO_NATIVE_AD";
    public static final String OB_ONE_NATIVE_AD = "OB_ONE_NATIVE_AD";
    public static final String OB_TWO_NATIVE_AD = "OB_TWO_NATIVE_AD";
    public static final String OB_THREE_NATIVE_AD = "OB_THREE_NATIVE_AD";
    public static final String OB_FOUR_NATIVE_AD = "OB_FOUR_NATIVE_AD";
    public static final String NATIVE_ASK = "NATIVE_ASK";
    public static final String NATIVE_OLD_ONB1 = "NATIVE_OLD_ONB1";
    public static final String NATIVE_HOME = "NATIVE_HOME";
    public static final String NATIVE_CATEGORY = "NATIVE_CATEGORY";
    public static final String NATIVE_STORE = "NATIVE_STORE";
    public static final String NATIVE_FACE_SWAP = "NATIVE_FACE_SWAP";
    public static final String NATIVE_FILTER = "NATIVE_FILTER";
    public static final String NATIVE_TEMPLATE = "NATIVE_TEMPLATE";
    public static final String NATIVE_GALLERY = "NATIVE_GALLERY";
    public static final String NATIVE_SHARE = "NATIVE_SHARE";
    public static final String NATIVE_EXIT = "NATIVE_EXIT";
    public static final String NATIVE_EDIT = "NATIVE_EDIT";
    public static final String NATIVE_CROP = "NATIVE_CROP";
    public static final String NATIVE_POPUP_BACK_EDIT = "NATIVE_POPUP_BACK_EDIT";
    public static final String NATIVE_POPUP_BACK_SHARE = "NATIVE_POPUP_BACK_SHARE";
    public static final String NATIVE_GENERATE_CROP = "NATIVE_GENERATE_CROP";
    public static final String NATIVE_GENERATE_PROCESSING = "NATIVE_GENERATE_PROCESSING";
    public static final String NATIVE_GENERATE_BACK = "NATIVE_GENERATE_BACK";
    public static final String NATIVE_REASON_UNINSTALL = "NATIVE_REASON_UNINSTALL";

    // Default refresh interval (seconds), updated from AdSettingsStore
    private static final long DEFAULT_REFRESH_INTERVAL = AdConstants.DEFAULT_REFRESH_INTERVAL_SECONDS;
    private static final long MIN_REFRESH_INTERVAL = AdConstants.MIN_REFRESH_INTERVAL_SECONDS;

    // Auto-refresh toggle (can be disabled via config or remote config)
    private volatile boolean autoRefreshEnabled = true;

    // =========================================================
    // SINGLETON (private constructor)
    // =========================================================

    private static volatile NativeAdManager instance;

    private NativeAdManager() {}

    public static synchronized NativeAdManager getInstance() {
        if (instance == null) {
            instance = new NativeAdManager();
        }
        return instance;
    }

    public void setAutoRefreshEnabled(boolean enabled) {
        this.autoRefreshEnabled = enabled;
        if (!enabled) {
            stopAllAutoRefresh();
        }
    }

    public boolean isAutoRefreshEnabled() {
        return autoRefreshEnabled;
    }

    // =========================================================
    // AD CONFIG & CACHE
    // =========================================================

    private final Map<String, AdUnitsConfig> nativeAdMap = new ConcurrentHashMap<>();
    private final Map<String, AdsResponse<NativeAd>> preloadedAds = new ConcurrentHashMap<>();
    private final Map<String, Long> adLoadTimestamps = new ConcurrentHashMap<>();
    private final Map<String, AdStatus> adStatusMap = new ConcurrentHashMap<>();

    private static final long AD_EXPIRY_MS = AdConstants.AD_EXPIRY_MS;

    private boolean isAdExpired(String position) {
        Long loadTime = adLoadTimestamps.get(position);
        if (loadTime == null) return true;
        return (System.currentTimeMillis() - loadTime) > AD_EXPIRY_MS;
    }

    /**
     * Populates the ad unit map from unified AdSettingsStore config.
     * Call once during app startup (e.g. SplashScreenActivity).
     */
    public void populateAdUnitMap(Activity activity) {
        AdSettingsStore settings = AdSettingsStore.getInstance(activity);

        // SPLASH
        List<String> splashAds = new ArrayList<>();
        if (settings.getBoolean("splash_banner_change")) {
            splashAds.add(AdsConfig.native_splash_1);
            splashAds.add(AdsConfig.native_splash_2);
        }
        nativeAdMap.put(NATIVE_SPLASH, new AdUnitsConfig(splashAds, "splash_ad_native_view", "splash_ad_native_click"));

        // SPLASH FULL Screen
        List<String> splashFullAds = new ArrayList<>();
        if (settings.getBoolean("splash_inter_change")) {
            splashFullAds.add(AdsConfig.native_splash_full_screen_1);
            splashFullAds.add(AdsConfig.native_splash_full_screen_2);
        }
        nativeAdMap.put(NATIVE_SPLASH_FULL, new AdUnitsConfig(splashFullAds, "splash_full_ad_native_view", "splash_full_ad_native_click"));

        // LanguageOne
        List<String> lfo1Ads = new ArrayList<>();
        if (settings.getBoolean("show_201_lfo1_n_native_high_new", true)) lfo1Ads.add(AdsConfig.native_lfo1_1);
        if (settings.getBoolean("show_201_lfo1_n_native_new", true)) lfo1Ads.add(AdsConfig.native_lfo1_2);
        nativeAdMap.put(LANGUAGE_ONE_NATIVE_AD, new AdUnitsConfig(lfo1Ads, "lfo1_ad_native_view", "lfo1_ad_native_click"));

        // LanguageTwo
        List<String> lfo2Ads = new ArrayList<>();
        if (settings.getBoolean("show_202_lfo2_n_native_high_new", true)) lfo2Ads.add(AdsConfig.native_lfo2_1);
        if (settings.getBoolean("show_202_lfo2_n_native_new", true)) lfo2Ads.add(AdsConfig.native_lfo2_2);
        nativeAdMap.put(LANGUAGE_TWO_NATIVE_AD, new AdUnitsConfig(lfo2Ads, "lfo2_ad_native_view", "lfo2_ad_native_click"));

        // ONB1
        List<String> onb1 = new ArrayList<>();
        if (settings.getBoolean("show_301_onb1_n_native_high_new", true)) onb1.add(AdsConfig.native_ob1_1);
        if (settings.getBoolean("show_301_onb1_n_native_new", true)) onb1.add(AdsConfig.native_ob1_2);
        nativeAdMap.put(OB_ONE_NATIVE_AD, new AdUnitsConfig(onb1, "onb1_ad_native_view", "onb1_ad_native_click"));

        // ONB2
        List<String> onb2 = new ArrayList<>();
        if (settings.getBoolean("show_302_onb2_n_native_high_new", true)) onb2.add(AdsConfig.native_ob2_1);
        if (settings.getBoolean("show_302_onb2_n_native_new", true)) onb2.add(AdsConfig.native_ob2_2);
        nativeAdMap.put(OB_TWO_NATIVE_AD, new AdUnitsConfig(onb2, "onb2_ad_native_view", "onb2_ad_native_click"));

        // ONB3
        List<String> onb3 = new ArrayList<>();
        if (settings.getBoolean("show_303_onb3_n_native_high_new", true)) onb3.add(AdsConfig.native_ob3_1);
        if (settings.getBoolean("show_303_onb3_n_native_new", true)) onb3.add(AdsConfig.native_ob3_2);
        nativeAdMap.put(OB_THREE_NATIVE_AD, new AdUnitsConfig(onb3, "onb3_ad_native_view", "onb3_ad_native_click"));

        // ONB4
        List<String> onb4 = new ArrayList<>();
        if (settings.getBoolean("show_304_onb4_n_native_high_new", true)) onb4.add(AdsConfig.native_ob4_1);
        if (settings.getBoolean("show_304_onb4_n_native_new", true)) onb4.add(AdsConfig.native_ob4_2);
        nativeAdMap.put(OB_FOUR_NATIVE_AD, new AdUnitsConfig(onb4, "onb4_ad_native_view", "onb4_ad_native_click"));

        // Question / Ask
        List<String> question = new ArrayList<>();
        if (settings.getBoolean("show_native_ask_1", true)) question.add(AdsConfig.native_ask_1);
        if (settings.getBoolean("show_native_ask_2", true)) question.add(AdsConfig.native_ask_2);
        nativeAdMap.put(NATIVE_ASK, new AdUnitsConfig(question, "question_ad_native_view", "question_ad_native_click"));

        // Home
        List<String> home = new ArrayList<>();
        if (settings.getBoolean("show_401_native_home_1", true)) home.add(AdsConfig.native_home_401_1);
        if (settings.getBoolean("show_401_native_home_2", true)) home.add(AdsConfig.native_home_401_2);
        nativeAdMap.put(NATIVE_HOME, new AdUnitsConfig(home, "home_ad_native_view", "home_ad_native_click"));

        // Edit (own ad unit IDs, configurable via remote config, defaults to home IDs)
        List<String> edit = new ArrayList<>();
        if (settings.getBoolean("show_601_native_edit_1", true)) edit.add(AdsConfig.native_edit_601_1);
        if (settings.getBoolean("show_601_native_edit_2", true)) edit.add(AdsConfig.native_edit_601_2);
        nativeAdMap.put(NATIVE_EDIT, new AdUnitsConfig(edit, "edit_ad_native_view", "edit_ad_native_click"));

        // Category
        List<String> category = new ArrayList<>();
        if (settings.getBoolean("show_402_native_category_1", true)) category.add(AdsConfig.native_category_402_1);
        if (settings.getBoolean("show_402_native_category_2", true)) category.add(AdsConfig.native_category_402_2);
        nativeAdMap.put(NATIVE_CATEGORY, new AdUnitsConfig(category, "category_ad_native_view", "category_ad_native_click"));

        // Store (default: true)
        List<String> store = new ArrayList<>();
        if (settings.getBoolean("show_501_native_store_1", true)) store.add(AdsConfig.native_store_501_1);
        if (settings.getBoolean("show_501_native_store_2", true)) store.add(AdsConfig.native_store_501_2);
        nativeAdMap.put(NATIVE_STORE, new AdUnitsConfig(store, "store_ad_native_view", "store_ad_native_click"));

        // FaceSwap (default: true)
        List<String> faceswap = new ArrayList<>();
        if (settings.getBoolean("show_601_native_faceswap_1", true)) faceswap.add(AdsConfig.native_faceswap_601_1);
        if (settings.getBoolean("show_601_native_faceswap_2", true)) faceswap.add(AdsConfig.native_faceswap_601_2);
        nativeAdMap.put(NATIVE_FACE_SWAP, new AdUnitsConfig(faceswap, "faceswap_ad_native_view", "faceswap_ad_native_click"));

        // Filter (default: true)
        List<String> filter = new ArrayList<>();
        if (settings.getBoolean("show_601_native_filter_1", true)) filter.add(AdsConfig.native_filter_601_1);
        if (settings.getBoolean("show_601_native_filter_2", true)) filter.add(AdsConfig.native_filter_601_2);
        nativeAdMap.put(NATIVE_FILTER, new AdUnitsConfig(filter, "filter_ad_native_view", "filter_ad_native_click"));

        // Template (default: true)
        List<String> template = new ArrayList<>();
        if (settings.getBoolean("show_601_native_template_1", true)) template.add(AdsConfig.native_template_601_1);
        if (settings.getBoolean("show_601_native_template_2", true)) template.add(AdsConfig.native_template_601_2);
        nativeAdMap.put(NATIVE_TEMPLATE, new AdUnitsConfig(template, "template_ad_native_view", "template_ad_native_click"));

        // Gallery (default: true)
        List<String> gallery = new ArrayList<>();
        if (settings.getBoolean("show_602_native_gallery_1", true)) gallery.add(AdsConfig.native_gallery_602_1);
        if (settings.getBoolean("show_602_native_gallery_2", true)) gallery.add(AdsConfig.native_gallery_602_2);
        nativeAdMap.put(NATIVE_GALLERY, new AdUnitsConfig(gallery, "gallery_ad_native_view", "gallery_ad_native_click"));

        // Crop (default: true)
        List<String> crop = new ArrayList<>();
        if (settings.getBoolean("show_native_crop", true)) {
            crop.add(AdsConfig.inapp_native_802_1);
            crop.add(AdsConfig.inapp_native_802_2);
            crop.add(AdsConfig.inapp_native_802_3);
        }
        nativeAdMap.put(NATIVE_CROP, new AdUnitsConfig(crop, "crop_ad_native_view", "crop_ad_native_click"));

        // Share (default: true)
        List<String> share = new ArrayList<>();
        if (settings.getBoolean("show_native_share", true)) {
            share.add(AdsConfig.native_share_801);
        }
        nativeAdMap.put(NATIVE_SHARE, new AdUnitsConfig(share, "share_ad_native_view", "share_ad_native_click"));

        // Exit (default: true)
        List<String> exit = new ArrayList<>();
        if (settings.getBoolean("show_native_exit", true)) {
            exit.add(AdsConfig.native_exit_803);
        }
        nativeAdMap.put(NATIVE_EXIT, new AdUnitsConfig(exit, "exit_ad_native_view", "exit_ad_native_click"));

        // Popup Back Edit (default: true)
        List<String> popupBackEdit = new ArrayList<>();
        if (settings.getBoolean("show_native_popup_back_edit", true)) {
            popupBackEdit.add(AdsConfig.native_popup_801_1);
            popupBackEdit.add(AdsConfig.native_popup_801_2);
            popupBackEdit.add(AdsConfig.native_popup_801_3);
        }
        nativeAdMap.put(NATIVE_POPUP_BACK_EDIT, new AdUnitsConfig(popupBackEdit, "popup_back_edit_ad_native_view", "popup_back_edit_ad_native_click"));

        // Popup Back Share (default: true)
        List<String> popupBackShare = new ArrayList<>();
        if (settings.getBoolean("show_native_popup_back_share", true)) {
            popupBackShare.add(AdsConfig.native_popup_801_1);
            popupBackShare.add(AdsConfig.native_popup_801_2);
            popupBackShare.add(AdsConfig.native_popup_801_3);
        }
        nativeAdMap.put(NATIVE_POPUP_BACK_SHARE, new AdUnitsConfig(popupBackShare, "popup_back_share_ad_native_view", "popup_back_share_ad_native_click"));

        // Old Onboarding 1
        List<String> oldOnb1 = new ArrayList<>();
        if (settings.getBoolean("show_native_old_onb1_1", true)) oldOnb1.add(AdsConfig.native_old_onb1_1);
        if (settings.getBoolean("show_native_old_onb1_2", true)) oldOnb1.add(AdsConfig.native_old_onb1_2);
        nativeAdMap.put(NATIVE_OLD_ONB1, new AdUnitsConfig(oldOnb1, "old_onb1_ad_native_view", "old_onb1_ad_native_click"));

        // Reason Uninstall (default: true)
        List<String> reasonUninstall = new ArrayList<>();
        if (settings.getBoolean("show_native_reason_uninstall_1", true)) reasonUninstall.add(AdsConfig.native_reason_uninstall_1);
        if (settings.getBoolean("show_native_reason_uninstall_2", true)) reasonUninstall.add(AdsConfig.native_reason_uninstall_2);
        nativeAdMap.put(NATIVE_REASON_UNINSTALL, new AdUnitsConfig(reasonUninstall, "reason_uninstall_ad_native_view", "reason_uninstall_ad_native_click"));

        // Generate/Processing — Crop screen
        List<String> genCrop = new ArrayList<>();
        if (settings.getBoolean("show_native_generate_crop_1", true)) genCrop.add(AdsConfig.native_generate_crop_1);
        if (settings.getBoolean("show_native_generate_crop_2", true)) genCrop.add(AdsConfig.native_generate_crop_2);
        nativeAdMap.put(NATIVE_GENERATE_CROP, new AdUnitsConfig(genCrop, "generate_crop_ad_native_view", "generate_crop_ad_native_click"));

        // Generate/Processing — Processing screen
        List<String> genProcessing = new ArrayList<>();
        if (settings.getBoolean("show_native_generate_processing_1", true)) genProcessing.add(AdsConfig.native_generate_processing_1);
        if (settings.getBoolean("show_native_generate_processing_2", true)) genProcessing.add(AdsConfig.native_generate_processing_2);
        nativeAdMap.put(NATIVE_GENERATE_PROCESSING, new AdUnitsConfig(genProcessing, "generate_processing_ad_native_view", "generate_processing_ad_native_click"));

        // Generate/Processing — Back dialog
        List<String> genBack = new ArrayList<>();
        if (settings.getBoolean("show_native_generate_back_1", true)) genBack.add(AdsConfig.native_generate_back_1);
        if (settings.getBoolean("show_native_generate_back_2", true)) genBack.add(AdsConfig.native_generate_back_2);
        nativeAdMap.put(NATIVE_GENERATE_BACK, new AdUnitsConfig(genBack, "generate_back_ad_native_view", "generate_back_ad_native_click"));
    }

    /**
     * Populate native ad placements from AdoreAdsConfig (builder-based).
     * This is the preferred method for the reusable module.
     * Each placement key maps to a PlacementConfig with ad unit IDs and enabled flag.
     */
    public void populateFromConfig(AdoreAdsConfig config) {
        nativeAdMap.clear();
        for (Map.Entry<String, PlacementConfig> entry : config.getNativePlacements().entrySet()) {
            PlacementConfig pc = entry.getValue();
            if (pc.isEnabled() && !pc.getAdUnitIds().isEmpty()) {
                nativeAdMap.put(entry.getKey(), new AdUnitsConfig(
                        pc.getAdUnitIds(),
                        pc.getViewEventName(),
                        pc.getClickEventName()
                ));
            }
        }
    }

    // =========================================================
    // RUNTIME PLACEMENT MANAGEMENT
    // =========================================================

    /**
     * Add or replace a single native ad placement at runtime.
     * Does not affect other existing placements.
     */
    public void addPlacement(String key, PlacementConfig config) {
        if (key == null || config == null) return;
        if (config.isEnabled() && !config.getAdUnitIds().isEmpty()) {
            nativeAdMap.put(key, new AdUnitsConfig(
                    config.getAdUnitIds(),
                    config.getViewEventName(),
                    config.getClickEventName()
            ));
        }
    }

    /**
     * Remove a native ad placement and clean up its cached ad.
     */
    public void removePlacement(String key) {
        if (key == null) return;
        clear(key);
        nativeAdMap.remove(key);
        stopAutoRefresh(key);
    }

    /**
     * Check if a placement is registered.
     */
    public boolean hasPlacement(String key) {
        return nativeAdMap.containsKey(key);
    }

    // =========================================================
    // PRELOAD (for loading ads ahead of time)
    // =========================================================

    public void load(Activity activity, String position, AdCallback adCallback) {
        if (!ConsentManager.getInstance(activity).canRequestAds()) {
            return;
        }
        AdsResponse<NativeAd> response = preloadedAds.get(position);
        if (response != null && !isAdExpired(position)) {
            return; // Already cached and not expired
        }
        if (Objects.equals(adStatusMap.get(position), AdStatus.LOADING)) {
            return; // Already loading
        }
        // Clear expired ad before reloading
        if (response != null) {
            clear(position);
        }
        adStatusMap.put(position, AdStatus.LOADING);
        AdUnitsConfig config = nativeAdMap.get(position);
        List<String> nativeAdIds = new ArrayList<>();
        if (config != null && !config.adUnitIds.isEmpty()) {
            nativeAdIds = config.adUnitIds;
        }
        if (!NetworkUtils.isNetworkAvailable(activity)
                || !AdSettingsStore.getInstance(activity).getBoolean("enable_all_ads", true)
                || nativeAdIds.isEmpty()) {
            adStatusMap.put(position, AdStatus.FAILED);
            return;
        }

        // Parallel preload: fire all floors simultaneously, keep the first (highest priority) success
        parallelLoadNative(activity, nativeAdIds, position, adCallback);
    }

    /**
     * Parallel preload: fires all ad unit IDs simultaneously.
     * The first response wins (index 0 = highest floor = highest eCPM).
     * If a higher-floor ad loads after a lower one already cached, it replaces it
     * (since higher floor = more revenue). All failures = final failure callback.
     */
    private void parallelLoadNative(Activity activity, List<String> adIds,
                                     String position, AdCallback adCallback) {
        if (adIds.size() <= 1) {
            // Single ID — use sequential (no benefit from parallel)
            AdsMobileAdsManager.getInstance().loadAlternateNative(activity, adIds, new AdCallback() {
                @Override
                public void onNativeAds(ApAdNative nativeAd, String unitID) {
                    adStatusMap.put(position, AdStatus.LOADED);
                    preloadedAds.put(position, new AdsResponse<>(nativeAd.getNativeAd(), unitID));
                    adLoadTimestamps.put(position, System.currentTimeMillis());
                    adCallback.onNativeAds(nativeAd, unitID);
                }

                @Override
                public void onAdFailedToLoad(@NonNull ApAdError error) {
                    adStatusMap.put(position, AdStatus.FAILED);
                    preloadedAds.remove(position);
                    adCallback.onAdFailedToLoad(error);
                }
            });
            return;
        }

        // Multiple IDs — fire all in parallel
        final int totalIds = adIds.size();
        final int[] failCount = {0};
        final boolean[] highFloorFilled = {false};
        final boolean[] callbackFired = {false};

        for (int i = 0; i < totalIds; i++) {
            final int priority = i; // 0 = highest floor
            final String adId = adIds.get(i);
            String loadId = AdsMobileAdsManager.getInstance().isUseTestAdIds()
                    ? AdConstants.TEST_NATIVE_AD_ID : adId;

            AdsMobileAdsManager.getInstance().loadUnifiedNativeAd(activity, loadId, new AdCallback() {
                @Override
                public void onNativeAds(ApAdNative nativeAd) {
                    super.onNativeAds(nativeAd);
                    synchronized (failCount) {
                        if (priority == 0) {
                            // Highest floor won — always cache it
                            AdsResponse<NativeAd> prev = preloadedAds.get(position);
                            if (prev != null && prev.getAds() != null) {
                                prev.getAds().destroy();
                            }
                            highFloorFilled[0] = true;
                            adStatusMap.put(position, AdStatus.LOADED);
                            preloadedAds.put(position, new AdsResponse<>(nativeAd.getNativeAd(), adId));
                            adLoadTimestamps.put(position, System.currentTimeMillis());
                            if (!callbackFired[0]) {
                                callbackFired[0] = true;
                                adCallback.onNativeAds(nativeAd, adId);
                            }
                        } else if (!highFloorFilled[0] && !preloadedAds.containsKey(position)) {
                            // Lower floor filled first — use as fallback until high floor responds
                            adStatusMap.put(position, AdStatus.LOADED);
                            preloadedAds.put(position, new AdsResponse<>(nativeAd.getNativeAd(), adId));
                            adLoadTimestamps.put(position, System.currentTimeMillis());
                            if (!callbackFired[0]) {
                                callbackFired[0] = true;
                                adCallback.onNativeAds(nativeAd, adId);
                            }
                        } else {
                            // Already have a higher-priority ad cached — destroy this one
                            nativeAd.getNativeAd().destroy();
                        }
                    }
                }

                @Override
                public void onAdFailedToLoad(@NonNull ApAdError error) {
                    super.onAdFailedToLoad(error);
                    synchronized (failCount) {
                        failCount[0]++;
                        if (priority == 0) {
                            Log.d(TAG, "High floor failed for " + position + ", waiting for fallback");
                        }
                        if (failCount[0] >= totalIds && !callbackFired[0]) {
                            // All floors failed
                            callbackFired[0] = true;
                            adStatusMap.put(position, AdStatus.FAILED);
                            preloadedAds.remove(position);
                            adCallback.onAdFailedToLoad(error);
                        }
                    }
                }
            });
        }
    }

    // =========================================================
    // SHOW WITH AUTO-REFRESH (primary API for activities/fragments)
    // Uses callback-based loading — no retry polling.
    // Checks preloaded cache first, loads fresh if not available.
    // =========================================================

    /**
     * Shows a native ad and registers for automatic refresh.
     * If a preloaded ad exists in cache, shows immediately.
     * Otherwise loads fresh via callback and shows when ready.
     * Call stopAutoRefresh(tag) in onDestroy/onDestroyView.
     *
     * @param tag Unique tag per caller (e.g. "HomeFragment", "SplashActivity")
     */
    public void showWithAutoRefresh(Activity activity, FrameLayout placeHolder,
                                     ShimmerFrameLayout shimmer, int layoutId,
                                     String position, String tag) {
        showWithAutoRefresh(activity, placeHolder, shimmer, layoutId, position, tag, null);
    }

    public void showWithAutoRefresh(Activity activity, FrameLayout placeHolder,
                                     ShimmerFrameLayout shimmer, int layoutId,
                                     String position, String tag,
                                     LifecycleOwner lifecycleOwner) {
        if (activity == null || placeHolder == null) return;
        if (PurchaseManager.getInstance().isPurchased()) {
            hideAdViews(placeHolder, shimmer);
            return;
        }

        // Clear FAILED status so preload retries
        if (Objects.equals(adStatusMap.get(position), AdStatus.FAILED)) {
            adStatusMap.remove(position);
        }

        // Check preloaded cache — show immediately if available and not expired
        AdsResponse<NativeAd> existing = preloadedAds.get(position);
        if (existing != null && !isAdExpired(position)) {
            showNativeAd(activity, existing, placeHolder, layoutId, shimmer);
        } else {
            // Expired or not cached — clear stale ad and load fresh
            if (existing != null) {
                clear(position);
            }
            // Load fresh and show via callback
            AdUnitsConfig config = nativeAdMap.get(position);
            List<String> adIds = (config != null && !config.adUnitIds.isEmpty()) ? config.adUnitIds : new ArrayList<>();
            if (!canLoadAd(activity, adIds)) {
                hideAdViews(placeHolder, shimmer);
                return;
            }
            adStatusMap.put(position, AdStatus.LOADING);
            AdsMobileAdsManager.getInstance().loadAlternateNative(
                    activity, adIds,
                    new AdCallback() {
                        @Override
                        public void onNativeAds(ApAdNative nativeAd, String unitID) {
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            AdsResponse<NativeAd> ad = new AdsResponse<>(nativeAd.getNativeAd(), unitID);
                            preloadedAds.put(position, ad);
                            adLoadTimestamps.put(position, System.currentTimeMillis());
                            adStatusMap.put(position, AdStatus.LOADED);
                            showNativeAd(activity, ad, placeHolder, layoutId, shimmer);
                        }

                        @Override
                        public void onAdFailedToLoad(@NonNull ApAdError error) {
                            adStatusMap.put(position, AdStatus.FAILED);
                            // Try default fallback pool
                            AdsResponse<NativeAd> defaultAd = DefaultAdPool.getInstance().consumeDefaultNativeAd(activity);
                            if (defaultAd != null) {
                                showNativeAd(activity, defaultAd, placeHolder, layoutId, shimmer);
                            } else {
                                hideAdViews(placeHolder, shimmer);
                            }
                        }
                    }
            );
        }

        // Register for auto-refresh
        autoRefreshPlacements.put(tag, new ActiveAdPlacement(activity, placeHolder, shimmer, layoutId, position));
        startGlobalRefreshIfNeeded();
        Log.d(TAG, "Registered auto-refresh: " + tag + " [" + position + "]");

        // Auto-stop refresh when lifecycle is destroyed
        if (lifecycleOwner != null) {
            lifecycleOwner.getLifecycle().addObserver(new DefaultLifecycleObserver() {
                @Override
                public void onDestroy(@NonNull LifecycleOwner owner) {
                    stopAutoRefresh(tag);
                    owner.getLifecycle().removeObserver(this);
                }
            });
        }
    }

    // =========================================================
    // LOAD AND SHOW (for adapters / one-off — no cache, no refresh)
    // Each call loads a fresh ad independently.
    // =========================================================

    public void loadAndShow(Activity activity, FrameLayout placeHolder,
                             ShimmerFrameLayout shimmer, int layoutId, String position) {
        loadAndShow(activity, placeHolder, shimmer, layoutId, position, null);
    }

    public void loadAndShow(Activity activity, FrameLayout placeHolder,
                             ShimmerFrameLayout shimmer, int layoutId,
                             String position, AdCallback externalCallback) {
        if (activity == null || placeHolder == null) return;
        if (PurchaseManager.getInstance().isPurchased()) {
            hideAdViews(placeHolder, shimmer);
            return;
        }

        AdUnitsConfig config = nativeAdMap.get(position);
        List<String> adIds = (config != null && !config.adUnitIds.isEmpty()) ? config.adUnitIds : new ArrayList<>();
        if (!canLoadAd(activity, adIds)) {
            hideAdViews(placeHolder, shimmer);
            return;
        }

        AdsMobileAdsManager.getInstance().loadAlternateNative(
                activity, adIds,
                new AdCallback() {
                    @Override
                    public void onNativeAds(ApAdNative nativeAd, String unitID) {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        AdsResponse<NativeAd> ad = new AdsResponse<>(nativeAd.getNativeAd(), unitID);
                        showNativeAd(activity, ad, placeHolder, layoutId, shimmer);
                        if (externalCallback != null) externalCallback.onNativeAds(nativeAd, unitID);
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull ApAdError error) {
                        // Try default fallback pool
                        AdsResponse<NativeAd> defaultAd = DefaultAdPool.getInstance().consumeDefaultNativeAd(activity);
                        if (defaultAd != null) {
                            showNativeAd(activity, defaultAd, placeHolder, layoutId, shimmer);
                        } else {
                            hideAdViews(placeHolder, shimmer);
                        }
                        if (externalCallback != null) externalCallback.onAdFailedToLoad(error);
                    }
                }
        );
    }

    // =========================================================
    // SHOW / RENDER
    // =========================================================

    public void showNativeAd(Activity activity, AdsResponse<NativeAd> adsResponse,
                              FrameLayout placeHolder, int layoutId) {
        showNativeAd(activity, adsResponse, placeHolder, layoutId, null);
    }

    public void showNativeAd(Activity activity, AdsResponse<NativeAd> adsResponse,
                              FrameLayout placeHolder, int layoutId,
                              ShimmerFrameLayout shimmer) {
        if (PurchaseManager.getInstance().isPurchased()) {
            hideAdViews(placeHolder, shimmer);
            return;
        }
        if (adsResponse != null && adsResponse.getAds() != null) {
            // Check if the ad has expired before showing
            Long loadTime = adLoadTimestamps.get(adsResponse.getUnitID());
            if (loadTime != null && (System.currentTimeMillis() - loadTime) > AD_EXPIRY_MS) {
                Log.d(TAG, "Ad expired at show time, destroying: " + adsResponse.getUnitID());
                adsResponse.getAds().destroy();
                hideAdViews(placeHolder, shimmer);
                return;
            }
            placeHolder.setVisibility(View.VISIBLE);
            if (shimmer != null) {
                shimmer.stopShimmer();
                shimmer.setVisibility(View.GONE);
            }
            AdsMobileAdsManager.getInstance().showNative(activity, adsResponse, placeHolder, layoutId);
        } else {
            placeHolder.setVisibility(View.GONE);
        }
    }

    // =========================================================
    // CACHE MANAGEMENT
    // =========================================================

    /**
     * Returns the ad unit config for a given position.
     * Useful when callers need to load ads directly (e.g. full-screen native on splash).
     */
    public AdUnitsConfig getNativeAdConfig(String position) {
        return nativeAdMap.get(position);
    }

    public void clear(String position) {
        AdsResponse<NativeAd> ad = preloadedAds.get(position);
        if (ad != null && ad.getAds() != null) {
            ad.getAds().destroy();
        }
        preloadedAds.remove(position);
        adLoadTimestamps.remove(position);
        adStatusMap.remove(position);
    }

    public void reload(Activity activity, String position, AdCallback adCallback) {
        clear(position);
        load(activity, position, adCallback);
    }

    // =========================================================
    // GLOBAL AUTO-REFRESH (swap-on-success — old ad stays visible until new one loads)
    // =========================================================

    private static class ActiveAdPlacement {
        final WeakReference<Activity> activityRef;
        final WeakReference<FrameLayout> placeHolderRef;
        final WeakReference<ShimmerFrameLayout> shimmerRef;
        final int layoutId;
        final String position;

        ActiveAdPlacement(Activity activity, FrameLayout placeHolder,
                          ShimmerFrameLayout shimmer, int layoutId, String position) {
            this.activityRef = new WeakReference<>(activity);
            this.placeHolderRef = new WeakReference<>(placeHolder);
            this.shimmerRef = new WeakReference<>(shimmer);
            this.layoutId = layoutId;
            this.position = position;
        }
    }

    private final Map<String, ActiveAdPlacement> autoRefreshPlacements = new ConcurrentHashMap<>();
    private final Handler globalRefreshHandler = new Handler(Looper.getMainLooper());
    private boolean isGlobalRefreshRunning = false;

    private final Runnable globalRefreshRunnable = () -> {
        refreshAllActivePlacements();
        if (!autoRefreshPlacements.isEmpty()) {
            scheduleGlobalRefresh();
        }
    };

    public void stopAutoRefresh(String tag) {
        autoRefreshPlacements.remove(tag);
        Log.d(TAG, "Stopped auto-refresh: " + tag);
        if (autoRefreshPlacements.isEmpty()) {
            stopGlobalRefresh();
        }
    }

    public void stopAllAutoRefresh() {
        autoRefreshPlacements.clear();
        stopGlobalRefresh();
        Log.d(TAG, "Stopped ALL auto-refresh");
    }

    private long getRefreshInterval() {
        long interval = AdSettingsStore.getRefreshInterval();
        return interval > 0 ? interval : DEFAULT_REFRESH_INTERVAL;
    }

    private void startGlobalRefreshIfNeeded() {
        if (!autoRefreshEnabled) {
            Log.d(TAG, "Auto-refresh disabled via config");
            return;
        }
        long interval = getRefreshInterval();
        if (interval < MIN_REFRESH_INTERVAL) {
            Log.d(TAG, "Auto-refresh disabled: interval " + interval + "s < " + MIN_REFRESH_INTERVAL + "s");
            return;
        }
        if (!isGlobalRefreshRunning) {
            isGlobalRefreshRunning = true;
            scheduleGlobalRefresh();
        }
    }

    private void scheduleGlobalRefresh() {
        globalRefreshHandler.removeCallbacks(globalRefreshRunnable);
        globalRefreshHandler.postDelayed(globalRefreshRunnable, getRefreshInterval() * 1000);
    }

    private void stopGlobalRefresh() {
        globalRefreshHandler.removeCallbacks(globalRefreshRunnable);
        isGlobalRefreshRunning = false;
    }

    /**
     * Refreshes all registered ad placements using swap-on-success:
     * - Loads a new ad for each placement
     * - Only replaces the old ad when the new one is ready
     * - If new ad fails to load, old ad stays visible
     */
    private void refreshAllActivePlacements() {
        if (PurchaseManager.getInstance().isPurchased()) {
            stopAllAutoRefresh();
            return;
        }

        Iterator<Map.Entry<String, ActiveAdPlacement>> it = autoRefreshPlacements.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ActiveAdPlacement> entry = it.next();
            ActiveAdPlacement placement = entry.getValue();

            Activity activity = placement.activityRef.get();
            FrameLayout placeHolder = placement.placeHolderRef.get();

            // Remove stale entries
            if (activity == null || activity.isFinishing() || activity.isDestroyed()
                    || placeHolder == null || !placeHolder.isAttachedToWindow()) {
                it.remove();
                Log.d(TAG, "Removed stale: " + entry.getKey());
                continue;
            }

            // Swap-on-success with stagger to avoid thundering herd
            AdUnitsConfig config = nativeAdMap.get(placement.position);
            if (config == null || config.adUnitIds.isEmpty()) continue;

            String entryKey = entry.getKey();
            int layoutId = placement.layoutId;
            String position = placement.position;
            long stagger = (long) (Math.random() * AdConstants.REFRESH_STAGGER_MAX_MS);

            globalRefreshHandler.postDelayed(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                AdsMobileAdsManager.getInstance().loadAlternateNative(
                    activity, config.adUnitIds,
                    new AdCallback() {
                        @Override
                        public void onNativeAds(ApAdNative nativeAd, String unitID) {
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            AdsResponse<NativeAd> newAd = new AdsResponse<>(nativeAd.getNativeAd(), unitID);
                            preloadedAds.put(position, newAd);
                            adLoadTimestamps.put(position, System.currentTimeMillis());
                            adStatusMap.put(position, AdStatus.LOADED);
                            showNativeAd(activity, newAd, placeHolder, layoutId);
                            Log.d(TAG, "Refreshed: " + entryKey + " [" + position + "]");
                        }

                        @Override
                        public void onAdFailedToLoad(@NonNull ApAdError error) {
                            // Keep old ad visible — do nothing
                            Log.d(TAG, "Refresh failed, keeping old: " + entryKey);
                        }
                    }
                );
            }, stagger);
        }

        if (autoRefreshPlacements.isEmpty()) {
            stopGlobalRefresh();
        }
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private boolean canLoadAd(Activity activity, List<String> adIds) {
        return !adIds.isEmpty()
                && NetworkUtils.isNetworkAvailable(activity)
                && ConsentManager.getInstance(activity).canRequestAds();
    }

    private void hideAdViews(FrameLayout placeHolder, ShimmerFrameLayout shimmer) {
        if (placeHolder != null) placeHolder.setVisibility(View.GONE);
        if (shimmer != null) {
            shimmer.stopShimmer();
            shimmer.setVisibility(View.GONE);
        }
    }

}