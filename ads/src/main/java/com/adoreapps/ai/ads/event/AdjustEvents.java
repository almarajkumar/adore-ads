package com.adoreapps.ai.ads.event;

import android.util.Log;

import com.adjust.sdk.Adjust;
import com.adjust.sdk.AdjustAdRevenue;
import com.adjust.sdk.AdjustEvent;
import com.google.android.gms.ads.AdValue;

public class AdjustEvents {

    private static final String TAG = "AdjustEvents";

    private String tokenAdImpression = "";
    private String tokenPurchase = "";
    private boolean enabled = false;

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
     * Enable Adjust tracking and set event tokens.
     * If not called, all Adjust calls are silently skipped.
     */
    public void setTokens(String adImpressionToken, String purchaseToken) {
        if (adImpressionToken != null && !adImpressionToken.isEmpty()) {
            this.tokenAdImpression = adImpressionToken;
        }
        if (purchaseToken != null && !purchaseToken.isEmpty()) {
            this.tokenPurchase = purchaseToken;
        }
        this.enabled = !this.tokenAdImpression.isEmpty() || !this.tokenPurchase.isEmpty();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void pushTrackEventAdmob(AdValue adValue) {
        if (!enabled) return;
        try {
            AdjustAdRevenue adRevenue = new AdjustAdRevenue("admob_sdk");
            adRevenue.setRevenue(adValue.getValueMicros() / 1000000.0, adValue.getCurrencyCode());
            Adjust.trackAdRevenue(adRevenue);
            onTrackAdRevenue((float) (adValue.getValueMicros() / 1000000.0), adValue.getCurrencyCode());
        } catch (Exception e) {
            Log.e(TAG, "pushTrackEventAdmob error: " + e.getMessage());
        }
    }

    public void onTrackAdRevenue(float revenue, String currency) {
        if (!enabled || tokenAdImpression.isEmpty()) return;
        try {
            AdjustEvent event = new AdjustEvent(tokenAdImpression);
            event.setRevenue(revenue, currency);
            Adjust.trackEvent(event);
        } catch (Exception e) {
            Log.e(TAG, "onTrackAdRevenue error: " + e.getMessage());
        }
    }

    public void onTrackPurchaseRevenue(double revenue, String currency,
                                       String productId, String orderId,
                                       String purchaseToken) {
        if (!enabled || tokenPurchase.isEmpty()) return;
        try {
            AdjustEvent event = new AdjustEvent(tokenPurchase);
            event.setRevenue(revenue, currency);
            event.setOrderId(orderId);
            event.setPurchaseToken(purchaseToken);
            event.setProductId(productId);
            Adjust.trackEvent(event);
        } catch (Exception e) {
            Log.e(TAG, "onTrackPurchaseRevenue error: " + e.getMessage());
        }
    }
}
