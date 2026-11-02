package io.github.karamegy.Vpn;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.ArrayList;
import java.util.List;

@CapacitorPlugin(name = "AndroidVPNBridge")
public class AndroidVPNBridgePlugin extends Plugin implements PurchasesUpdatedListener {

    private static final String TAG = "AndroidVPNBridgePlugin";

    public static final String BASE64_LICENSE_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAmxgdl9pF2Wa6OpHD7jQnmAKZ+1Z/TgyCo5WhWAcd1yVbsKW6/MeEk3wHECm/j8RNdqVTSpa5Gwle0uuiT+yNaIjZgVwg1CdjkPzu6F3TYiazdGTxTvK3bb5a+TYQB2B6di30vTP6BAVqRSFOiVuULEDH/DcLjWuqVGMgWRe4ni37yoHnBtRh5Lyj/Dg3jq01AeUg/xNlK7kUi/xY1zqCH3oXtou4uxTLBTWGrIvisrKVyj3Xx6npCrwAQRiVGC0rjUiYUltkqx5r2ZAWGOxOpH0ia6HqNveJjMbY9icRLrmfiemky107HCqEjB+QTGCR7Lw+5QjIFD1DARb1E/YJbwIDAQAB";

    private BillingClient billingClient;
    private PluginCall activeBillingCall;

    @Override
    public void load() {
        super.load();
        initBillingClient();
    }

    private synchronized void initBillingClient() {
        if (billingClient == null) {
            BillingClient.Builder builder = BillingClient.newBuilder(getContext())
                    .setListener(this);

            try {
                PendingPurchasesParams pendingParams = PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts()
                        .build();
                builder.enablePendingPurchases(pendingParams);
            } catch (Exception e) {
                builder.enablePendingPurchases();
            }

            billingClient = builder.build();
        }
    }

    private void startBillingConnection(Runnable onSuccess, Runnable onFailure) {
        initBillingClient();
        if (billingClient.isReady()) {
            if (onSuccess != null) onSuccess.run();
            return;
        }

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    if (onSuccess != null) onSuccess.run();
                } else {
                    Log.e(TAG, "فشل الاتصال بخدمة Billing: " + billingResult.getDebugMessage());
                    if (onFailure != null) onFailure.run();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                Log.w(TAG, "انقطع الاتصال بخدمة Billing");
            }
        });
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        } else {
            if (activeBillingCall != null) {
                JSObject ret = new JSObject();
                ret.put("success", false);
                ret.put("message", "تم إلغاء أو فشل عملية الشراء: " + billingResult.getDebugMessage());
                activeBillingCall.resolve(ret);
                activeBillingCall = null;
            }
        }
    }

    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged()) {
                AcknowledgePurchaseParams acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.getPurchaseToken())
                        .build();

                billingClient.acknowledgePurchase(acknowledgePurchaseParams, billingResult -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        notifyPurchaseSuccess(purchase);
                    } else if (activeBillingCall != null) {
                        JSObject ret = new JSObject();
                        ret.put("success", false);
                        ret.put("message", "فشل تأكيد الشراء من جوجل");
                        activeBillingCall.resolve(ret);
                        activeBillingCall = null;
                    }
                });
            } else {
                notifyPurchaseSuccess(purchase);
            }
        }
    }

    private void notifyPurchaseSuccess(Purchase purchase) {
        if (activeBillingCall != null) {
            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("orderId", purchase.getOrderId());
            ret.put("purchaseToken", purchase.getPurchaseToken());
            ret.put("products", purchase.getProducts().toString());
            activeBillingCall.resolve(ret);
            activeBillingCall = null;
        }
    }

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
        startBillingConnection(() -> {
            QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build();

            billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
                boolean isPro = false;
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (Purchase purchase : purchases) {
                        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                            isPro = true;
                            break;
                        }
                    }
                }
                JSObject ret = new JSObject();
                ret.put("isPro", isPro);
                call.resolve(ret);
            });
        }, () -> {
            JSObject ret = new JSObject();
            ret.put("isPro", false);
            call.resolve(ret);
        });
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
        if (productId == null || productId.isEmpty()) {
            call.reject("يجب تحديد productId لشراء الاشتراكات");
            return;
        }

        this.activeBillingCall = call;

        startBillingConnection(() -> {
            List<QueryProductDetailsParams.Product> productList = new ArrayList<>();
            productList.add(
                    QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(productId)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
            );

            QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                    .setProductList(productList)
                    .build();

            billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && !productDetailsList.isEmpty()) {
                    ProductDetails productDetails = productDetailsList.get(0);

                    List<BillingFlowParams.ProductDetailsParams> productDetailsParamsList = new ArrayList<>();

                    BillingFlowParams.ProductDetailsParams.Builder builder = BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails);

                    if (productDetails.getSubscriptionOfferDetails() != null && !productDetails.getSubscriptionOfferDetails().isEmpty()) {
                        String offerToken = productDetails.getSubscriptionOfferDetails().get(0).getOfferToken();
                        builder.setOfferToken(offerToken);
                    }

                    productDetailsParamsList.add(builder.build());

                    BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                            .setProductDetailsParamsList(productDetailsParamsList)
                            .build();

                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            BillingResult launchResult = billingClient.launchBillingFlow(activity, billingFlowParams);
                            if (launchResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                                if (activeBillingCall != null) {
                                    JSObject ret = new JSObject();
                                    ret.put("success", false);
                                    ret.put("message", "فشل إطلاق نافذة الشراء: " + launchResult.getDebugMessage());
                                    activeBillingCall.resolve(ret);
                                    activeBillingCall = null;
                                }
                            }
                        });
                    } else {
                        call.reject("تعذر الحصول على نشاط الشاشة (Activity)");
                    }
                } else {
                    JSObject ret = new JSObject();
                    ret.put("success", false);
                    ret.put("message", "لم يتم العثور على المنتج في متجر Google Play: " + productId);
                    call.resolve(ret);
                }
            });
        }, () -> {
            JSObject ret = new JSObject();
            ret.put("success", false);
            ret.put("message", "فشل الاتصال بخدمة Google Play Billing");
            call.resolve(ret);
        });
    }
}
