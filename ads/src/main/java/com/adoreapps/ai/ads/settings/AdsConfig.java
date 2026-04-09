package com.adoreapps.ai.ads.settings;

/**
 * Production ad unit IDs for all placements.
 * Test ad IDs are in {@link AdConstants} and selected at runtime via
 * {@link com.adoreapps.ai.ads.core.AdsMobileAdsManager#isUseTestAdIds()}.
 */
public final class AdsConfig {

    private AdsConfig() {}

    // Default fallback IDs (per ad type)
    public static final String nativeDefaultId = "ca-app-pub-4284251692530284/8582802527";
    public static final String interstitialDefaultId = "ca-app-pub-4284251692530284/7893612786";
    public static final String bannerDefaultId = "ca-app-pub-4284251692530284/9477215240";
    public static final String rewardDefaultId = "ca-app-pub-4284251692530284/7269720850";
    public static final String appOpenDefaultId = "ca-app-pub-4284251692530284/1390841662";

    // Banner
    public static final String banner_splash_1 = "ca-app-pub-4284251692530284/7377665513";
    public static final String banner_splash_2 = "ca-app-pub-4284251692530284/7377665513";
    public static final String banner_setting_406 = "ca-app-pub-4284251692530284/7377665513";
    public static final String banner_edit_701 = "ca-app-pub-4284251692530284/4169048244";

    // Interstitial — Splash
    public static final String inter_splash_1 = "ca-app-pub-4284251692530284/3103378585";
    public static final String inter_splash_2 = "ca-app-pub-4284251692530284/3103378585";
    public static final String inter_splash_3 = "ca-app-pub-4284251692530284/3103378585";
    public static final String inter_splash_4 = "ca-app-pub-4284251692530284/3103378585";
    public static final String inter_splash_5 = "ca-app-pub-4284251692530284/3103378585";

    // Interstitial — Onboarding
    public static final String inter_onb_1 = "ca-app-pub-4284251692530284/2398909182";
    public static final String inter_onb_2 = "ca-app-pub-4284251692530284/2398909182";

    // Interstitial — Menu
    public static final String inter_menu_403_1 = "ca-app-pub-4284251692530284/1805339864";
    public static final String inter_menu_403_2 = "ca-app-pub-4284251692530284/1805339864";

    // Interstitial — List
    public static final String inter_list_404_1 = "ca-app-pub-4284251692530284/1805339864";
    public static final String inter_list_404_2 = "ca-app-pub-4284251692530284/1805339864";

    // Interstitial — Picture
    public static final String inter_picture_405_1 = "ca-app-pub-4284251692530284/1805339864";
    public static final String inter_picture_405_2 = "ca-app-pub-4284251692530284/1805339864";

    // Interstitial — Store
    public static final String inter_store_503_1 = "ca-app-pub-4284251692530284/1805339864";
    public static final String inter_store_503_2 = "ca-app-pub-4284251692530284/1805339864";

    // Interstitial — Gallery
    public static final String inter_gallery_603_1 = "ca-app-pub-4284251692530284/9077759991";
    public static final String inter_gallery_603_2 = "ca-app-pub-4284251692530284/9077759991";

    // Interstitial — Save
    public static final String inter_save_703_1 = "ca-app-pub-4284251692530284/2398909182";
    public static final String inter_save_703_2 = "ca-app-pub-4284251692530284/2398909182";

    // Interstitial — Home
    public static final String inter_home_802_1 = "ca-app-pub-4284251692530284/1805339864";
    public static final String inter_home_802_2 = "ca-app-pub-4284251692530284/1805339864";

    // Interstitial — Generate
    public static final String inter_free_generate_1 = "ca-app-pub-4284251692530284/9492258199";
    public static final String inter_free_generate_2 = "ca-app-pub-4284251692530284/9492258199";
    public static final String inter_generate_1 = "ca-app-pub-4284251692530284/1805339864";
    public static final String inter_generate_2 = "ca-app-pub-4284251692530284/1805339864";

    // Interstitial — Uninstall
    public static final String inter_uninstall_1 = "ca-app-pub-4284251692530284/2398909182";
    public static final String inter_uninstall_2 = "ca-app-pub-4284251692530284/2398909182";

    // Interstitial — Other
    public static final String inter_803_1 = "ca-app-pub-4284251692530284/2398909182";
    public static final String inter_803_2 = "ca-app-pub-4284251692530284/2398909182";
    public static final String inter_803_3 = "ca-app-pub-4284251692530284/2398909182";

    // Native — Splash
    public static final String native_splash_1 = "ca-app-pub-4284251692530284/8211267432";
    public static final String native_splash_2 = "ca-app-pub-4284251692530284/8211267432";
    public static final String native_splash_full_screen_1 = "ca-app-pub-4284251692530284/9053575813";
    public static final String native_splash_full_screen_2 = "ca-app-pub-4284251692530284/9053575813";
    public static final String native_splash_full_screen_3 = "ca-app-pub-4284251692530284/9053575813";

    // Native — Language
    public static final String native_lfo1_1 = "ca-app-pub-4284251692530284/8948809620";
    public static final String native_lfo1_2 = "ca-app-pub-4284251692530284/8948809620";
    public static final String native_lfo1_3 = "ca-app-pub-4284251692530284/8948809620";
    public static final String native_lfo1_4 = "ca-app-pub-4284251692530284/8948809620";
    public static final String native_lfo2_1 = "ca-app-pub-4284251692530284/7330763168";
    public static final String native_lfo2_2 = "ca-app-pub-4284251692530284/7330763168";
    public static final String native_lfo2_3 = "ca-app-pub-4284251692530284/7330763168";
    public static final String native_lfo2_4 = "ca-app-pub-4284251692530284/7330763168";

    // Native — Onboarding
    public static final String native_ob1_1 = "ca-app-pub-4284251692530284/7514941470";
    public static final String native_ob1_2 = "ca-app-pub-4284251692530284/7514941470";
    public static final String native_ob1_3 = "ca-app-pub-4284251692530284/7514941470";
    public static final String native_ob1_4 = "ca-app-pub-4284251692530284/7514941470";
    public static final String native_ob2_1 = "ca-app-pub-4284251692530284/7987373329";
    public static final String native_ob2_2 = "ca-app-pub-4284251692530284/7987373329";
    public static final String native_ob3_1 = "ca-app-pub-4284251692530284/8478762687";
    public static final String native_ob3_2 = "ca-app-pub-4284251692530284/8478762687";
    public static final String native_ob3_3 = "ca-app-pub-4284251692530284/8478762687";
    public static final String native_ob3_4 = "ca-app-pub-4284251692530284/8478762687";
    public static final String native_ob4_1 = "ca-app-pub-4284251692530284/1895783283";
    public static final String native_ob4_2 = "ca-app-pub-4284251692530284/1895783283";

    // Native — Home / Category
    public static final String native_home_401_1 = "ca-app-pub-4284251692530284/7635727959";
    public static final String native_home_401_2 = "ca-app-pub-4284251692530284/7635727959";
    public static final String native_category_402_1 = "ca-app-pub-4284251692530284/7635727959";
    public static final String native_category_402_2 = "ca-app-pub-4284251692530284/7635727959";

    // Native — Store
    public static final String native_store_501_1 = "ca-app-pub-4284251692530284/7635727959";
    public static final String native_store_501_2 = "ca-app-pub-4284251692530284/3402228657";

    // Native — Edit / FaceSwap / Filter / Template
    public static final String native_edit_601_1 = "ca-app-pub-4284251692530284/7635727959";
    public static final String native_edit_601_2 = "ca-app-pub-4284251692530284/7635727959";
    public static final String native_faceswap_601_1 = "ca-app-pub-4284251692530284/7635727959";
    public static final String native_faceswap_601_2 = "ca-app-pub-4284251692530284/7635727959";
    public static final String native_filter_601_1 = "ca-app-pub-4284251692530284/7635727959";
    public static final String native_filter_601_2 = "ca-app-pub-4284251692530284/7635727959";
    public static final String native_template_601_1 = "ca-app-pub-4284251692530284/7635727959";
    public static final String native_template_601_2 = "ca-app-pub-4284251692530284/7635727959";

    // Native — Gallery
    public static final String native_gallery_602_1 = "ca-app-pub-4284251692530284/7796334463";
    public static final String native_gallery_602_2 = "ca-app-pub-4284251692530284/7796334463";

    // Native — Share / Exit
    public static final String native_share_801 = "ca-app-pub-4284251692530284/9704312503";
    public static final String native_exit_803 = "ca-app-pub-4284251692530284/8582802527";

    // Native — Popups
    public static final String native_popup_801_1 = "ca-app-pub-4284251692530284/8582802527";
    public static final String native_popup_801_2 = "ca-app-pub-4284251692530284/8582802527";
    public static final String native_popup_801_3 = "ca-app-pub-4284251692530284/8582802527";

    // Native — In-app
    public static final String inapp_native_802_1 = "ca-app-pub-4284251692530284/8582802527";
    public static final String inapp_native_802_2 = "ca-app-pub-4284251692530284/8582802527";
    public static final String inapp_native_802_3 = "ca-app-pub-4284251692530284/8582802527";

    // Native — Ask / Question
    public static final String native_ask_1 = "ca-app-pub-4284251692530284/8582802527";
    public static final String native_ask_2 = "ca-app-pub-4284251692530284/8582802527";

    // Native — Old Onboarding
    public static final String native_old_onb1_1 = "ca-app-pub-4284251692530284/8582802527";
    public static final String native_old_onb1_2 = "ca-app-pub-4284251692530284/8582802527";

    // Native — Uninstall
    public static final String native_reason_uninstall_1 = "ca-app-pub-4284251692530284/8582802527";
    public static final String native_reason_uninstall_2 = "ca-app-pub-4284251692530284/8582802527";

    // Native — Generate/Processing
    public static final String native_generate_crop_1 = "ca-app-pub-4284251692530284/7635727959";
    public static final String native_generate_crop_2 = "ca-app-pub-4284251692530284/7635727959";
    public static final String native_generate_processing_1 = "ca-app-pub-4284251692530284/7635727959";
    public static final String native_generate_processing_2 = "ca-app-pub-4284251692530284/7635727959";
    public static final String native_generate_back_1 = "ca-app-pub-4284251692530284/7635727959";
    public static final String native_generate_back_2 = "ca-app-pub-4284251692530284/7635727959";

    // Reward
    public static final String reward_store_502_1 = "ca-app-pub-4284251692530284/7269720850";
    public static final String reward_store_502_2 = "ca-app-pub-4284251692530284/7269720850";
    public static final String miniature_reward_high_501 = "ca-app-pub-4284251692530284/7269720850";
    public static final String miniature_reward_501 = "ca-app-pub-4284251692530284/7269720850";
    public static final String reward_sticker_702 = "ca-app-pub-4284251692530284/7269720850";
    public static final String reward_wtm_704 = "ca-app-pub-4284251692530284/7269720850";

    // App Open
    public static final String aoa_all_high_508 = "ca-app-pub-4284251692530284/1390841662";
    public static final String aoa_all_508 = "ca-app-pub-4284251692530284/1390841662";
}
