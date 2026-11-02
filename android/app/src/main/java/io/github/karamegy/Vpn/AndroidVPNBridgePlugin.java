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

    // مفتاح الترخيص الخاص بـ Google Play Console
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
    private void handleVpnPermissionResult(PluginCall call, ActivityResult result) {
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
        JSObject ret = new JSObject();
        ret.put("isPro", false);
        call.resolve(ret);
    }

    @PluginMethod
    public void getLicenseKey(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("licenseKey", BASE64_LICENSE_KEY);
        call.resolve(ret);
    }

    @PluginMethod
    public void launchBilling(PluginCall call) {
        String productId = call.getString("productId", "");
        Activity activity = getActivity();

        if (activity == null) {
            call.reject("Activity غير متاحة");
            return;
        }

        // إنشاء اتصال مباشر مع خدمات Google Play Billing
        billingClient = BillingClient.newBuilder(getContext())
            .setListener((billingResult, purchases) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (Purchase purchase : purchases) {
                        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                            // تأكيد الشراء لدى متجر جوجل
                            AcknowledgePurchaseParams acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.getPurchaseToken())
                                .build();
                            billingClient.acknowledgePurchase(acknowledgePurchaseParams, ackResult -> {});

                            JSObject ret = new JSObject();
                            ret.put("success", true);
                            ret.put("productId", productId);
                            call.resolve(ret);
                            return;
                        }
                    }
                }
                call.reject("تم إلغاء عملية الشراء أو فشلها");
            })
            .enablePendingPurchases()
            .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build();

                    QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                        .setProductList(Collections.singletonList(product))
                        .build();

                    billingClient.queryProductDetailsAsync(params, (result, productDetailsList) -> {
                        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && !productDetailsList.isEmpty()) {
                            ProductDetails productDetails = productDetailsList.get(0);
                            
                            String offerToken = "";
                            if (productDetails.getSubscriptionOfferDetails() != null && !productDetails.getSubscriptionOfferDetails().isEmpty()) {
                                offerToken = productDetails.getSubscriptionOfferDetails().get(0).getOfferToken();
                            }

                            BillingFlowParams.ProductDetailsParams productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .setOfferToken(offerToken)
                                .build();

                            BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                                .setProductDetailsParamsList(Collections.singletonList(productParams))
                                .build();

                            // إطلاق نافذة الشراء الرسمية لمتجر Google Play
                            billingClient.launchBillingFlow(activity, billingFlowParams);
                        } else {
                            call.reject("المُنتج غير معرف داخل Google Play Console");
                        }
                    });
                } else {
                    call.reject("فشل الاتصال بـ Google Play Billing Services");
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // إعادة الاتصال عند الحاجة
            }
        });
    }
}
