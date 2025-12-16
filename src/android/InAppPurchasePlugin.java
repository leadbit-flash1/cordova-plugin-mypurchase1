package com.acrobaticgames.mypurchase1;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.android.billingclient.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import android.util.Log;

public class InAppPurchasePlugin extends CordovaPlugin {

    private static final String TAG = "InAppPurchasePlugin";
    private BillingClient billingClient;
    private CallbackContext callbackContext;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        Log.d(TAG, "execute called with action: " + action);
        this.callbackContext = callbackContext;

        if (action.equals("initialize")) {
            Log.d(TAG, "Initializing billing client");
            this.initialize();
            return true;
        } else if (action.equals("purchase")) {
            String productId = args.getString(0);
            Log.d(TAG, "Attempting to purchase product: " + productId);
            this.purchase(productId);
            return true;
        } else if (action.equals("close")) {
            Log.d(TAG, "Closing all purchase dialogs");
            closePurchaseDialogs();
            return true;
        }

        Log.e(TAG, "Invalid action: " + action);
        callbackContext.error("Invalid action");
        return false;
    }

    private void closePurchaseDialogs() {
        // Close any open purchase dialogs if needed
    }

    private void initialize() {
        Log.d(TAG, "Starting billing client initialization");
        
        // For Billing 7.0.0, enablePendingPurchases requires PendingPurchasesParams
        PendingPurchasesParams pendingPurchasesParams = PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build();
        
        billingClient = BillingClient.newBuilder(cordova.getActivity())
                .setListener(new PurchasesUpdatedListener() {
                    @Override
                    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
                        Log.d(TAG, "onPurchasesUpdated: " + billingResult.getResponseCode());
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                            for (Purchase purchase : purchases) {
                                handlePurchase(purchase);
                            }
                        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                            Log.d(TAG, "User cancelled the purchase");
                            callbackContext.error("USER_CANCELED");
                        } else {
                            Log.e(TAG, "onPurchasesUpdated error: " + billingResult.getDebugMessage());
                            callbackContext.error("Purchase failed: " + billingResult.getDebugMessage());
                        }
                    }
                })
                .enablePendingPurchases(pendingPurchasesParams)  // Updated for 7.0.0
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                Log.d(TAG, "onBillingSetupFinished: " + billingResult.getResponseCode());
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client ready");
                    callbackContext.success("Billing client ready");
                } else {
                    Log.e(TAG, "Billing client setup failed: " + billingResult.getDebugMessage());
                    callbackContext.error("Billing client setup failed: " + billingResult.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                Log.e(TAG, "Billing service disconnected");
                callbackContext.error("Billing service disconnected");
            }
        });
    }

    private void purchase(String productId) {
        Log.d(TAG, "Starting purchase process for: " + productId);

        closePurchaseDialogs();

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            new PurchasesResponseListener() {
                @Override
                public void onQueryPurchasesResponse(BillingResult billingResult, List<Purchase> purchases) {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        for (Purchase purchase : purchases) {
                            if (purchase.getProducts().contains(productId)) {
                                consumePurchase(purchase, () -> initiateNewPurchase(productId));
                                return;
                            }
                        }
                        initiateNewPurchase(productId);
                    } else {
                        Log.e(TAG, "Failed to query purchases: " + billingResult.getDebugMessage());
                        callbackContext.error("Failed to query purchases: " + billingResult.getDebugMessage());
                    }
                }
            }
        );
    }

    private void initiateNewPurchase(String productId) {
        List<QueryProductDetailsParams.Product> productList = new ArrayList<>();
        productList.add(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        );

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build();

        billingClient.queryProductDetailsAsync(
                params,
                new ProductDetailsResponseListener() {
                    public void onProductDetailsResponse(BillingResult billingResult, List<ProductDetails> productDetailsList) {
                        Log.d(TAG, "onProductDetailsResponse: " + billingResult.getResponseCode());
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && productDetailsList != null && !productDetailsList.isEmpty()) {
                            for (ProductDetails productDetails : productDetailsList) {
                                Log.d(TAG, "Launching billing flow for: " + productDetails.getProductId());
                                
                                List<BillingFlowParams.ProductDetailsParams> productDetailsParamsList = new ArrayList<>();
                                productDetailsParamsList.add(
                                    BillingFlowParams.ProductDetailsParams.newBuilder()
                                        .setProductDetails(productDetails)
                                        .build()
                                );

                                BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                                        .setProductDetailsParamsList(productDetailsParamsList)
                                        .build();

                                BillingResult result = billingClient.launchBillingFlow(cordova.getActivity(), flowParams);
                                Log.d(TAG, "Billing flow launch result: " + result.getResponseCode());
                            }
                        } else {
                            Log.e(TAG, "Failed to get product details: " + billingResult.getDebugMessage());
                            callbackContext.error("Failed to get product details: " + billingResult.getDebugMessage());
                        }
                    }
                }
        );
    }

    private void consumePurchase(Purchase purchase, Runnable onConsumeComplete) {
        ConsumeParams consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();

        billingClient.consumeAsync(consumeParams, new ConsumeResponseListener() {
            @Override
            public void onConsumeResponse(BillingResult billingResult, String purchaseToken) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Purchase consumed successfully");
                    onConsumeComplete.run();
                } else {
                    Log.e(TAG, "Error consuming purchase: " + billingResult.getDebugMessage());
                    callbackContext.error("Error consuming purchase: " + billingResult.getDebugMessage());
                }
            }
        });
    }

    private void handlePurchase(Purchase purchase) {
        Log.d(TAG, "Handling purchase: " + purchase.getProducts().get(0));
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            Log.d(TAG, "Purchase successful: " + purchase.getProducts().get(0));

            // Consume the purchase immediately for consumable products
            consumePurchase(purchase, () -> {
                Log.d(TAG, "Purchase consumed successfully");
                callbackContext.success("Purchase successful and consumed");
            });
        } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
            Log.d(TAG, "Purchase pending: " + purchase.getProducts().get(0));
            callbackContext.error("PURCHASE_PENDING");
        } else {
            Log.e(TAG, "Purchase not successful. State: " + purchase.getPurchaseState());
            callbackContext.error("PURCHASE_FAILED");
        }
    }

    @Override
    public void onDestroy() {
        if (billingClient != null) {
            billingClient.endConnection();
        }
        super.onDestroy();
    }
}
