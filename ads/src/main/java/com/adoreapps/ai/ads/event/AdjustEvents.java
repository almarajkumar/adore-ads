package com.adoreapps.ai.ads.event;

import com.adjust.sdk.Adjust;
import com.adjust.sdk.AdjustAdRevenue;
import com.adjust.sdk.AdjustEvent;
import com.google.android.gms.ads.AdValue;

public class AdjustEvents {

    private String tokenAdImpression = "fy5v20";
    private String tokenPurchase = "wq89ou";

    private static volatile AdjustEvents instance;

    private AdjustEvents() {}

    public static AdjustEvents getInstance() {
        if (instance == null) {
            synchronized (AdjustEvents.class) {
                if (instance == null) {
                    instance = new AdjustEvents();
                }
            }
        }
        return instance;
    }

    /**
     * Set Adjust event tokens from AdoreAdsConfig.
     */
    public void setTokens(String adImpressionToken, String purchaseToken) {
        if (adImpressionToken != null && !adImpressionToken.isEmpty()) {
            this.tokenAdImpression = adImpressionToken;
        }
        if (purchaseToken != null && !purchaseToken.isEmpty()) {
            this.tokenPurchase = purchaseToken;
        }
    }

    public void pushTrackEventAdmob(AdValue adValue) {
        AdjustAdRevenue adRevenue = new AdjustAdRevenue("admob_sdk");
        adRevenue.setRevenue(adValue.getValueMicros() / 1000000.0, adValue.getCurrencyCode());
        Adjust.trackAdRevenue(adRevenue);
        onTrackAdRevenue((float) (adValue.getValueMicros() / 1000000.0), adValue.getCurrencyCode());
    }

    public void onTrackAdRevenue(float revenue, String currency) {
        AdjustEvent event = new AdjustEvent(tokenAdImpression);
        event.setRevenue(revenue, currency);
        Adjust.trackEvent(event);
    }

    public void onTrackPurchaseRevenue(double revenue, String currency,
                                       String productId, String orderId,
                                       String purchaseToken) {
        AdjustEvent event = new AdjustEvent(tokenPurchase);
        event.setRevenue(revenue, currency);
        event.setOrderId(orderId);
        event.setPurchaseToken(purchaseToken);
        event.setProductId(productId);
        Adjust.trackEvent(event);
    }
}
