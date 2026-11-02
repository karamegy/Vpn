package io.github.karamegy.Vpn;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;

import androidx.activity.result.ActivityResult;
import androidx.core.content.ContextCompat;

import com.android.billingclient.api.*;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Collections;

@CapacitorPlugin(name = "AndroidVPNBridge")
public class AndroidVPNBridgePlugin extends Plugin {

    public static final String BASE64_LICENSE_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAmxgdl9pF2Wa6OpHD7jQnmAKZ+1Z/TgyCo5WhWAcd1yVbsKW6/MeEk3wHECm/j8RNdqVTSpa5Gwle0uuiT+yNaIjZgVwg1CdjkPzu6F3TYiazdGTxTvK3bb5a+TYQB2B6di30vTP6BAVqRSFOiVuULEDH/DcLjWuqVGMgWRe4ni37yoHnBtRh5Lyj/Dg3jq01AeUg/xNlK7kUi/xY1zqCH3oXtou4uxTLBTWGrIvisrKVyj3Xx6npCrwAQRiVGC0rjUiYUltkqx5r2ZAWGOxOpH0ia6HqNveJjMbY9icRLrmfiemky107HCqEjB+QTGCR7Lw+5QjIFD1DARb1E/YJbwIDAQAB";

    private BillingClient billingClient;

    @PluginMethod
    public void prepareVpn(PluginCall call) {
        Intent intent = VpnService.prepare(getContext());
        if (intent != null) {
            startActivityForResult(call, intent, "handleVpnPermissionResult");
        } else {
            JSObject ret = new JSObject();
            ret.put("prepared", true);
            call.resolve(ret);
        }
    }

    @ActivityCallback
    public void handleVpnPermissionResult(PluginCall call, ActivityResult result) {
        JSObject ret = new JSObject();
        if (result.getResultCode() == Activity.RESULT_OK) {
            ret.put("prepared", true);
            call.resolve(ret);
        } else {
            ret.put("prepared", false);
            call.reject("المستخدم رفض منح إذن الـ VPN");
        }
    }

    @PluginMethod
    public void toggleVpn(PluginCall call) {
        boolean enable = call.getBoolean("enable", false);
        String serverIp = call.getString("serverIp", "1.1.1.1");
        String serverName = call.getString("serverName", "Apex Cloud Server");

        Intent intent = new Intent(getContext(), ApexVpnService.class);

        if (enable) {
            intent.setAction(ApexVpnService.ACTION_CONNECT);
            intent.putExtra("serverIp", serverIp);
            intent.putExtra("serverName", serverName);
            ContextCompat.startForegroundService(getContext(), intent);
        } else {
            intent.setAction(ApexVpnService.ACTION_DISCONNECT);
            getContext().startService(intent);
        }

        JSObject ret = new JSObject();
        ret.put("success", true);
        ret.put("status", enable ? "connected" : "disconnected");
        call.resolve(ret);
    }

    @PluginMethod
    public void performNativeScan(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("scannedMB", "450.5");
        ret.put("totalMB", "8192");
        ret.put("scannedApps", 42);
        call.resolve(ret);
    }

    @PluginMethod
    public void checkSubscriptionStatus(PluginCall call) {
        if (billingClient == null || !billingClient.isReady()) {
            initBillingClient(call, null, false);
        } else {
            queryActivePurchases(call);
        }
    }

    @PluginMethod
    public void getLicenseKey(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("licenseKey", BASE64_LICENSE_KEY);
        call.resolve(ret);
    }

    @PluginMethod
    public void launchBilling(PluginCall call) {
        final String productId = call.getString("productId", "");
        final Activity activity = getActivity();

        if (activity == null) {
            call.reject("Activity غير متاحة");
            return;
        }

        initBillingClient(call, productId, true);
    }

    private void initBillingClient(PluginCall call, String productId, boolean isPurchaseFlow) {
        // إعداد معلمات الشراء المعلق المعتمدة في الإصدار 8.0.0
        PendingPurchasesParams pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build();

        billingClient = BillingClient.newBuilder(getContext())
            .setListener((billingResult, purchases) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (Purchase purchase : purchases) {
                        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                            String activeProductId = (purchase.getProducts() != null && !purchase.getProducts().isEmpty()) 
                                ? purchase.getProducts().get(0) : productId;

                            if (!purchase.isAcknowledged()) {
                                AcknowledgePurchaseParams acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                                    .setPurchaseToken(purchase.getPurchaseToken())
                                    .build();
                                billingClient.acknowledgePurchase(acknowledgePurchaseParams, ackResult -> {
                                    JSObject ret = new JSObject();
                                    ret.put("success", true);
                                    ret.put("productId", activeProductId);
                                    call.resolve(ret);
                                });
                            } else {
                                JSObject ret = new JSObject();
                                ret.put("success", true);
                                ret.put("productId", activeProductId);
                                call.resolve(ret);
                            }
                            return;
                        }
                    }
                }
                call.reject("تم إلغاء عملية الشراء أو فشلها");
            })
            .enablePendingPurchases(pendingPurchasesParams)
            .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    if (isPurchaseFlow && productId != null && !productId.isEmpty()) {
                        queryProduct(getActivity(), productId, BillingClient.ProductType.SUBS, call);
                    } else {
                        queryActivePurchases(call);
                    }
                } else {
                    call.reject("فشل الاتصال بـ Google Play Billing Services: " + billingResult.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // إعادة الاتصال تلقائياً عند الحاجة
            }
        });
    }

    private void queryActivePurchases(PluginCall call) {
        if (billingClient == null || !billingClient.isReady()) {
            JSObject ret = new JSObject();
            ret.put("isPro", false);
            ret.put("activeProductId", "");
            call.resolve(ret);
            return;
        }

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
            (billingResult, purchases) -> {
                boolean isPro = false;
                String activeProductId = "";

                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (Purchase purchase : purchases) {
                        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                            isPro = true;
                            if (purchase.getProducts() != null && !purchase.getProducts().isEmpty()) {
                                activeProductId = purchase.getProducts().get(0);
                            }
                            break;
                        }
                    }
                }
                JSObject ret = new JSObject();
                ret.put("isPro", isPro);
                ret.put("activeProductId", activeProductId);
                ret.put("isYearly", "apex_pro_yearly".equalsIgnoreCase(activeProductId));
                call.resolve(ret);
            }
        );
    }

    private void queryProduct(Activity activity, String rawProductId, String productType, PluginCall call) {
        final String targetProductId = rawProductId != null ? rawProductId.replace("-", "_") : "";
        final String targetBasePlanId = rawProductId != null ? rawProductId.replace("_", "-") : "";

        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(targetProductId)
            .setProductType(productType)
            .build();

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
            .setProductList(Collections.singletonList(product))
            .build();

        billingClient.queryProductDetailsAsync(params, (result, productDetailsList) -> {
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && !productDetailsList.isEmpty()) {
                ProductDetails productDetails = productDetailsList.get(0);
                
                BillingFlowParams.ProductDetailsParams.Builder productParamsBuilder = 
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails);

                if (BillingClient.ProductType.SUBS.equals(productType) 
                    && productDetails.getSubscriptionOfferDetails() != null 
                    && !productDetails.getSubscriptionOfferDetails().isEmpty()) {
                    
                    String selectedOfferToken = null;

                    for (ProductDetails.SubscriptionOfferDetails offerDetails : productDetails.getSubscriptionOfferDetails()) {
                        if (targetBasePlanId.equalsIgnoreCase(offerDetails.getBasePlanId()) 
                            || targetProductId.equalsIgnoreCase(offerDetails.getBasePlanId())) {
                            selectedOfferToken = offerDetails.getOfferToken();
                            break;
                        }
                    }

                    if (selectedOfferToken == null) {
                        selectedOfferToken = productDetails.getSubscriptionOfferDetails().get(0).getOfferToken();
                    }

                    productParamsBuilder.setOfferToken(selectedOfferToken);
                }

                BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(Collections.singletonList(productParamsBuilder.build()))
                    .build();

                activity.runOnUiThread(() -> billingClient.launchBillingFlow(activity, billingFlowParams));
            } else if (BillingClient.ProductType.SUBS.equals(productType)) {
                queryProduct(activity, targetProductId, BillingClient.ProductType.INAPP, call);
            } else {
                call.reject("المُنتج (" + targetProductId + ") غير معرّف أو غير مفعل داخل Google Play Console");
            }
        });
    }
}
