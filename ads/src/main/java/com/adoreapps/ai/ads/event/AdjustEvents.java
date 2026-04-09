package com.adoreapps.ai.ads.event;
import com.adjust.sdk.Adjust;
import com.adjust.sdk.AdjustAdRevenue;
import com.adjust.sdk.AdjustEvent;
import com.google.android.gms.ads.AdValue;

public class AdjustEvents {

    private static String tokenAdImpression = "fy5v20";
    private static String tokenPurchase = "wq89ou";

    private static AdjustEvents instance;
    private AdjustEvents() {}

    public static synchronized AdjustEvents getInstance() {
        if (instance == null) {
            instance = new AdjustEvents();
        }
        return instance;
    }



    //Default tracking Adjust
    public  void pushTrackEventAdmob(AdValue adValue) {

        AdjustAdRevenue adRevenue = new AdjustAdRevenue("admob_sdk");
        adRevenue.setRevenue(adValue.getValueMicros() / 1000000.0, adValue.getCurrencyCode());
        Adjust.trackAdRevenue(adRevenue);
        onTrackAdRevenue((float) (adValue.getValueMicros() / 1000000.0), adValue.getCurrencyCode());

    }

    // Custom event ad impression to tracking revenue
    public  void onTrackAdRevenue(  float revenue, String currency) {
        AdjustEvent event = new AdjustEvent(tokenAdImpression);
        // Add revenue 1 cent of an euro.
        event.setRevenue(revenue, currency);
        Adjust.trackEvent(event);
    }

    public  void onTrackPurchaseRevenue(  double revenue,
                                          String currency,
                                          String productId,
                                          String orderId,
                                          String purchaseToken
    ) {
        AdjustEvent event = new AdjustEvent(tokenPurchase);
        // Add revenue 1 cent of an euro.
        event.setRevenue(revenue, currency);
        event.setOrderId(orderId);
        event.setPurchaseToken(purchaseToken);
        event.setProductId(productId);
        Adjust.trackEvent(event);
    }
}
