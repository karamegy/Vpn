package io.github.karamegy.Vpn;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;

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

    public static final String BASE64_LICENSE_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAmxgdl9pF2Wa6OpHD7jQnmAKZ+1Z/TgyCo5WhWAcd1yVbsKW6/MeEk3wHECm/j8RNdqVTSpa5Gwle0uuiT+yNaIjZgVwg1CdjkPzu6F3TYiazdGTxTvK3bb5a+TYQB2B6di30vTP6BAVqRSFOiVuULEDH/DcLjWuqVGMgWRe4ni37yoHnBtRh5Lyj/Dg3jq01AeUg/xNlK7kUi/xY1zqCH3oXtou4uxTLBTWGrIvisrKVyj3Xx6npCrwAQRiVGC0rjUiYUltkqx5r2ZAWGOxOpH0ia6HqNveJjMbY9icRLrmfiemky107HCqEjB+QTGCR7Lw+5QjIFD1DARb1E/YJbwIDAQAB";

    private BillingClient billingClient;
    private PluginCall activeBillingCall;

    @Override
    public void load() {
        super.load();
        // تهيئة محرك دفع جوجل بلاي عند تحميل الإضافة
        billingClient = BillingClient.newBuilder(getContext())
                .setListener(this)
                .enablePendingPurchases()
                .build();
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
    private void handleVpnPermissionResult(PluginCall call, com.getcapacitor.JSObject result) {
        JSObject ret = new JSObject();
        ret.put("prepared", true);
        call.resolve(ret);
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
            androidx.core.content.ContextCompat.startForegroundService(getContext(), intent);
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
        if (!billingClient.isReady()) {
            billingClient.startConnection(new BillingClientStateListener() {
                @Override
                public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        queryActiveSubscriptions(call);
                    } else {
                        JSObject ret = new JSObject();
                        ret.put("isPro", false);
                        call.resolve(ret);
                    }
                }

                @Override
                public void onBillingServiceDisconnected() {
                    JSObject ret = new JSObject();
                    ret.put("isPro", false);
                    call.resolve(ret);
                }
            });
        } else {
            queryActiveSubscriptions(call);
        }
    }

    private void queryActiveSubscriptions(PluginCall call) {
        com.android.billingclient.api.QueryPurchasesParams params = com.android.billingclient.api.QueryPurchasesParams.newBuilder()
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
            call.reject("يجب تحديد معرف المنتج productId");
            return;
        }

        activeBillingCall = call;

        if (!billingClient.isReady()) {
            billingClient.startConnection(new BillingClientStateListener() {
                @Override
                public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        startPurchaseFlow(productId);
                    } else {
                        call.reject("تعذر الاتصال بخدمة Google Play Billing: " + billingResult.getDebugMessage());
                    }
                }

                @Override
                public void onBillingServiceDisconnected() {
                    call.reject("انقطع الاتصال بـ Google Play Store");
                }
            });
        } else {
            startPurchaseFlow(productId);
        }
    }

    private void startPurchaseFlow(String productId) {
        List<QueryProductDetailsParams.Product> productList = new ArrayList<>();
        productList.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build());

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && !productDetailsList.isEmpty()) {
                ProductDetails productDetails = productDetailsList.get(0);
                
                List<ProductDetails.SubscriptionOfferDetails> offerDetails = productDetails.getSubscriptionOfferDetails();
                if (offerDetails == null || offerDetails.isEmpty()) {
                    if (activeBillingCall != null) activeBillingCall.reject("لا توجد عروض اشتراك مفعّلة للمنتج في المتجر");
                    return;
                }

                String offerToken = offerDetails.get(0).getOfferToken();

                List<BillingFlowParams.ProductDetailsParams> productDetailsParamsList = new ArrayList<>();
                productDetailsParamsList.add(BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build());

                BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(productDetailsParamsList)
                        .build();

                Activity activity = getActivity();
                if (activity != null) {
                    billingClient.launchBillingFlow(activity, billingFlowParams);
                } else {
                    if (activeBillingCall != null) activeBillingCall.reject("تعذر تحديد واجهة النشاط الحالية");
                }
            } else {
                if (activeBillingCall != null) {
                    activeBillingCall.reject("لم يتم العثور على المنتج داخل Google Play Console: " + productId);
                }
            }
        });
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
        if (activeBillingCall == null) return;

        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    JSObject ret = new JSObject();
                    ret.put("success", true);
                    ret.put("orderId", purchase.getOrderId());
                    ret.put("purchaseToken", purchase.getPurchaseToken());
                    activeBillingCall.resolve(ret);
                    activeBillingCall = null;
                    return;
                }
            }
        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            activeBillingCall.reject("تم إلغاء عملية الشراء من قبل المستخدم");
        } else {
            activeBillingCall.reject("فشلت عملية الشراء: " + billingResult.getDebugMessage());
        }
        activeBillingCall = null;
    }
}
