package com.adoreapps.ai.ads.billing;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;

import com.adoreapps.ai.ads.event.AdjustEvents;
import com.adoreapps.ai.ads.interfaces.PurchaseCallback;
import com.adoreapps.ai.ads.model.PurchaseModel;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsParams.Product;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class PurchaseManager {
    private static volatile PurchaseManager instance;
    private final List<Purchase> purchaseList = new ArrayList();
    private List<ProductDetails> productDetailsList;
    private PurchaseCallback callback;
    private BillingClient billingClient;
    private boolean isPurchased = false;
    private boolean isEventUpdated = false;
    private final AcknowledgePurchaseResponseListener acknowledgePurchaseResponseListener = (billingResult) -> {
        this.queryPurchase();
    };
    private final PurchasesUpdatedListener purchasesUpdatedListener = (billingResult, list) -> {
        if (billingResult.getResponseCode() == 0 && list != null) {
            for(int i = 0; i < list.size(); ++i) {
                this.handlePurchase((Purchase)list.get(i));
            }
            if(this.callback != null) {
                this.callback.purchaseSuccess();
            }
        } else {
            if(this.callback != null) {
                this.callback.purchaseFail();
            }
        }

    };
    private List<PurchaseModel> purchaseModelList;

    private PurchaseManager() {
    }

    public static synchronized PurchaseManager getInstance() {
        if (instance == null) {
            instance = new PurchaseManager();
        }

        return instance;
    }

    public void setCallback(PurchaseCallback callback) {
        this.callback = callback;
    }

    private void handlePurchase(Purchase purchase) {

        if (purchase.getPurchaseState() == 1 && !purchase.isAcknowledged()) {
            AcknowledgePurchaseParams acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build();
            this.billingClient.acknowledgePurchase(acknowledgePurchaseParams, this.acknowledgePurchaseResponseListener);
        }
        isPurchased = true;
        for (String productId : purchase.getProducts()) {
            Log.d("Billing", "Purchased productId: " + productId);
            updatePurchaseEvent(productId, purchase.getOrderId(), purchase.getPurchaseToken());
        }
    }

    public void updatePurchaseEvent(String productId, String orderId, String purchaseToken) {
        if(isEventUpdated) {
            return;
        }
        isEventUpdated = true;
        QueryProductDetailsParams.Product product =
                QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS) // or INAPP
                        .build();

        QueryProductDetailsParams params =
                QueryProductDetailsParams.newBuilder()
                        .setProductList(Collections.singletonList(product))
                        .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK &&
                    !productDetailsList.getProductDetailsList().isEmpty()) {

                ProductDetails details = productDetailsList.getProductDetailsList().get(0);

                if (details.getSubscriptionOfferDetails() == null
                        || details.getSubscriptionOfferDetails().isEmpty()) return;
                ProductDetails.SubscriptionOfferDetails offer = details.getSubscriptionOfferDetails().get(0);
                if (offer.getPricingPhases().getPricingPhaseList().isEmpty()) return;
                ProductDetails.PricingPhase phase = offer.getPricingPhases().getPricingPhaseList().get(0);

                String formattedPrice = phase.getFormattedPrice();    // "$4.99"
                String currency = phase.getPriceCurrencyCode();       // "USD"
                double amount = phase.getPriceAmountMicros() / 1_000_000.0; // 4.99

                Log.d("Billing", "Product: " + details.getProductId() +
                        " Amount: " + amount + " Currency: " + currency);
                AdjustEvents.getInstance().onTrackPurchaseRevenue(
                        amount,
                        currency,
                        productId,
                        orderId,
                        purchaseToken
                );
            }
        });

    }
    public void init(Context context, List<PurchaseModel> purchaseModelList) {
        this.purchaseModelList = purchaseModelList;
        this.billingClient = BillingClient.newBuilder(context).setListener(this.purchasesUpdatedListener)
                .enablePendingPurchases(
                        PendingPurchasesParams.newBuilder()
                                .enableOneTimeProducts()  // Enables pending one-time purchases
                                .build()
                )
                .build();
        this.connectGooglePlay();
    }

    private void connectGooglePlay() {
        this.billingClient.startConnection(new BillingClientStateListener() {
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == 0) {
                    PurchaseManager.this.queryPurchase();
                    PurchaseManager.this.queryProductDetails();
                }

            }

            public void onBillingServiceDisconnected() {
            }
        });
    }

    private void queryProductDetails() {
        List<QueryProductDetailsParams.Product> productList = new ArrayList();

        for(int i = 0; i < this.purchaseModelList.size(); ++i) {
            QueryProductDetailsParams.Product product = Product.newBuilder().setProductId(((PurchaseModel)this.purchaseModelList.get(i)).getProductId()).setProductType(((PurchaseModel)this.purchaseModelList.get(i)).getType()).build();
            productList.add(product);
        }

        QueryProductDetailsParams queryProductDetailsParams = QueryProductDetailsParams.newBuilder().setProductList(productList).build();
        this.billingClient.queryProductDetailsAsync(queryProductDetailsParams, (billingResult, productDetailsList) -> {
            this.productDetailsList = productDetailsList.getProductDetailsList();
        });
    }

    private void queryPurchase() {
        this.purchaseList.clear();
        final AtomicInteger pendingQueries = new AtomicInteger(2);
        QueryPurchasesParams param = QueryPurchasesParams.newBuilder().setProductType("subs").build();
        this.billingClient.queryPurchasesAsync(param, (billingResult, list) -> {
            this.purchaseList.addAll(list);
            if (pendingQueries.decrementAndGet() == 0) {
                validatePurchase();
            }
        });
        QueryPurchasesParams inappParam = QueryPurchasesParams.newBuilder().setProductType("inapp").build();
        this.billingClient.queryPurchasesAsync(inappParam, (billingResult, list) -> {
            this.purchaseList.addAll(list);
            if (pendingQueries.decrementAndGet() == 0) {
                validatePurchase();
            }
        });
    }

    public void consume(String productId) {
        Purchase purchase = this.getPurchase(productId);
        if (purchase != null) {
            ConsumeParams consumeParams = ConsumeParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build();
            ConsumeResponseListener listener = new ConsumeResponseListener() {
                public void onConsumeResponse(BillingResult billingResult, @NonNull String purchaseToken) {
                    if (billingResult.getResponseCode() == 0) {
                        Log.d("android_log", "onConsumeResponse: OK");
                        PurchaseManager.this.queryPurchase();
                    } else {
                        Log.d("android_log", "onConsumeResponse: Failed");
                    }

                }
            };
            this.billingClient.consumeAsync(consumeParams, listener);
        }

    }

    private Purchase getPurchase(String productId) {
        for(int i = 0; i < this.purchaseList.size(); ++i) {
            try {
                Purchase purchase = this.purchaseList.get(i);
                if (!purchase.getProducts().isEmpty()
                        && purchase.getProducts().get(0).equals(productId)) {
                    return purchase;
                }
            } catch (Exception e) {
                Log.e("PurchaseManager", "getPurchase error: " + e.getMessage());
            }
        }

        return null;
    }

    public void validatePurchase() {
        isPurchased = false;
        for(int i = 0; i < this.purchaseList.size(); ++i) {
            Purchase purchase = (Purchase)this.purchaseList.get(i);
            if (purchase.getPurchaseState() == 1) {
                isPurchased =  true;
            }
        }
    }
    public boolean isPurchased() {
        return isPurchased;
    }

    public void setIsPurchased(boolean isPurchased) {
        this.isPurchased = isPurchased;
    }

    public void launchPurchase(Activity activity, String productId) {
        ProductDetails productDetails = this.getProductDetail(productId);
        if (productDetails == null) {
            if(this.callback != null) {
                this.callback.purchaseFail();
            }
            this.queryPurchase();
            this.queryProductDetails();
        } else {
            BillingFlowParams.ProductDetailsParams productDetailsParams = ProductDetailsParams.newBuilder().setProductDetails(productDetails).build();
            BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(Collections.singletonList(productDetailsParams)).build();
            BillingResult billingResult = this.billingClient.launchBillingFlow(activity, billingFlowParams);
            Log.d("android_log", "launchPurchase: " + billingResult.getDebugMessage());
        }
    }

    private ProductDetails getProductDetail(String productId) {
        if (this.productDetailsList != null) {
            for(int i = 0; i < this.productDetailsList.size(); ++i) {
                if (((ProductDetails)this.productDetailsList.get(i)).getProductId().equals(productId)) {
                    return (ProductDetails)this.productDetailsList.get(i);
                }
            }
        }

        return null;
    }

    public String getPrice(String productId) {
        if (this.productDetailsList != null) {
            for(int i = 0; i < this.productDetailsList.size(); ++i) {
                if (((ProductDetails)this.productDetailsList.get(i)).getProductId().equals(productId)) {
                    ProductDetails.OneTimePurchaseOfferDetails detail = ((ProductDetails)this.productDetailsList.get(i)).getOneTimePurchaseOfferDetails();
                    if (detail != null) {
                        return detail.getFormattedPrice();
                    }
                }
            }
        }

        return "";
    }
}
