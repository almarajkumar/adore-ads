package com.adoreapps.ai.ads.consent;

import android.app.Activity;
import android.content.Context;
import com.google.android.ump.ConsentDebugSettings;
import com.google.android.ump.ConsentForm.OnConsentFormDismissedListener;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.FormError;
import com.google.android.ump.UserMessagingPlatform;

/**
 * The Google Mobile Ads SDK provides the User Messaging Platform (Google's
 * IAB Certified consent management platform) as one solution to capture
 * consent for users in GDPR impacted countries. This is an example and
 * you can choose another consent management platform to capture consent.
 */
public class ConsentManager {
    private static volatile ConsentManager instance;
    private final ConsentInformation consentInformation;
    private String testDeviceHashedId = "";

    private ConsentManager(Context context) {
        this.consentInformation = UserMessagingPlatform.getConsentInformation(context);
    }

    public static ConsentManager getInstance(Context context) {
        if (instance == null) {
            synchronized (ConsentManager.class) {
                if (instance == null) {
                    instance = new ConsentManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * Set the test device hashed ID for UMP debug settings.
     * Call this after initialization, typically from AdoreAds.init().
     */
    public void setTestDeviceHashedId(String hashedId) {
        this.testDeviceHashedId = hashedId != null ? hashedId : "";
    }

    /** Interface definition for a callback to be invoked when consent gathering is complete. */
    public interface OnConsentGatheringCompleteListener {
        void consentGatheringComplete(FormError error);
    }

    /** Helper variable to determine if the app can request ads. */
    public boolean canRequestAds() {
        return consentInformation.canRequestAds();
    }

    /** Helper variable to determine if the privacy options form is required. */
    public boolean isPrivacyOptionsRequired() {
        return consentInformation.getPrivacyOptionsRequirementStatus()
                == PrivacyOptionsRequirementStatus.REQUIRED;
    }

    /**
     * Helper method to call the UMP SDK methods to request consent information and load/present a
     * consent form if necessary.
     */
    public void gatherConsent(
            Activity activity, OnConsentGatheringCompleteListener onConsentGatheringCompleteListener) {

        ConsentRequestParameters.Builder paramsBuilder = new ConsentRequestParameters.Builder();

        if (!testDeviceHashedId.isEmpty()) {
            ConsentDebugSettings debugSettings = new ConsentDebugSettings.Builder(activity)
                    .addTestDeviceHashedId(testDeviceHashedId)
                    .build();
            paramsBuilder.setConsentDebugSettings(debugSettings);
        }

        ConsentRequestParameters params = paramsBuilder.build();

        consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                () ->
                        UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                                activity,
                                formError ->
                                        onConsentGatheringCompleteListener.consentGatheringComplete(formError)),
                requestConsentError ->
                        onConsentGatheringCompleteListener.consentGatheringComplete(requestConsentError)
        );
    }

    /** Helper method to call the UMP SDK method to present the privacy options form. */
    public void showPrivacyOptionsForm(
            Activity activity,
            OnConsentFormDismissedListener onConsentFormDismissedListener) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, onConsentFormDismissedListener);
    }
}
