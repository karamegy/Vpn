package io.github.karamegy.Vpn;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Environment;
import android.os.StatFs;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@CapacitorPlugin(name = "AndroidVPNBridge")
public class AndroidVPNBridgePlugin extends Plugin implements PurchasesUpdatedListener {

    private static final String TAG = "AndroidVPNBridge";
    public static final String BASE64_LICENSE_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAmxgdl9pF2Wa6OpHD7jQnmAKZ+1Z/TgyCo5WhWAcd1yVbsKW6/MeEk3wHECm/j8RNdqVTSpa5Gwle0uuiT+yNaIjZgVwg1CdjkPzu6F3TYiazdGTxTvK3bb5a+TYQB2B6di30vTP6BAVqRSFOiVuULEDH/DcLjWuqVGMgWRe4ni37yoHnBtRh5Lyj/Dg3jq01AeUg/xNlK7kUi/xY1zqCH3oXtou4uxTLBTWGrIvisrKVyj3Xx6npCrwAQRiVGC0rjUiYUltkqx5r2ZAWGOxOpH0ia6HqNveJjMbY9icRLrmfiemky107HCqEjB+QTGCR7Lw+5QjIFD1DARb1E/YJbwIDAQAB";

    private BillingClient billingClient;
    private PluginCall savedBillingCall;

    @Override
    public void load() {
        super.load();
        initBillingClient();
    }

    private void initBillingClient() {
        try {
            PendingPurchasesParams pendingPurchasesParams = PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build();

            billingClient = BillingClient.newBuilder(getContext())
                    .setListener(this)
                    .enablePendingPurchases(pendingPurchasesParams)
                    .build();

            startBillingConnection(null);
        } catch (Exception e) {
            Log.e(TAG, "خطأ تهيئة BillingClient: " + e.getMessage());
        }
    }

    private void startBillingConnection(@Nullable Runnable onConnected) {
        if (billingClient != null && billingClient.isReady()) {
            if (onConnected != null) onConnected.run();
            return;
        }

        if (billingClient == null) {
            initBillingClient();
            if (billingClient == null) return;
        }

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "✅ تم الاتصال بخدمة Google Play Billing بنجاح");
                    if (onConnected != null) onConnected.run();
                } else {
                    Log.e(TAG, "❌ فشل الاتصال بـ Google Play Billing: " + billingResult.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                Log.w(TAG, "⚠️ انقطع الاتصال بخدمة Google Play Billing");
            }
        });
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
        String serverIp = call.getString("serverIp", "94.140.14.14");
        String serverName = call.getString("serverName", "Apex Shield Core Node");

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

        try {
            File path = Environment.getDataDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSizeLong();
            long totalBlocks = stat.getBlockCountLong();
            long availableBlocks = stat.getAvailableBlocksLong();

            double totalMB = (double) (totalBlocks * blockSize) / (1024 * 1024);
            double freeMB = (double) (availableBlocks * blockSize) / (1024 * 1024);
            double usedMB = totalMB - freeMB;

            PackageManager pm = getContext().getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            int installedAppsCount = apps != null ? apps.size() : 0;

            ret.put("scannedMB", String.format("%.1f", usedMB));
            ret.put("totalMB", String.format("%.0f", totalMB));
            ret.put("scannedApps", installedAppsCount);

        } catch (Exception e) {
            ret.put("scannedMB", "512.0");
            ret.put("totalMB", "64000");
            ret.put("scannedApps", 30);
        }

        call.resolve(ret);
    }

    @PluginMethod
    public void checkSubscriptionStatus(PluginCall call) {
        startBillingConnection(() -> {
            QueryPurchasesParams subsParams = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build();

            billingClient.queryPurchasesAsync(subsParams, (billingResult, purchases) -> {
                boolean isPro = false;
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (Purchase purchase : purchases) {
                        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                            isPro = true;
                            break;
                        }
                    }
                }

                getContext().getSharedPreferences("ApexPrefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("isPro", isPro).apply();

                JSObject ret = new JSObject();
                ret.put("isPro", isPro);
                call.resolve(ret);
            });
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

        if (productId == null || productId.trim().isEmpty()) {
            call.reject("معرف المنتج غير صالح");
            return;
        }

        savedBillingCall = call;

        startBillingConnection(() -> {
            queryAndLaunchProduct(productId, BillingClient.ProductType.SUBS, () -> {
                queryAndLaunchProduct(productId, BillingClient.ProductType.INAPP, () -> {
                    rejectSavedCall("لم يتم العثور على المنتج في Google Play Console: " + productId);
                });
            });
        });
    }

    private void queryAndLaunchProduct(String productId, String productType, Runnable onNotFound) {
        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(productType)
                .build();

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(product))
                .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK 
                    && productDetailsList != null && !productDetailsList.isEmpty()) {

                ProductDetails productDetails = productDetailsList.get(0);
                List<BillingFlowParams.ProductDetailsParams> productDetailsParamsList = new ArrayList<>();

                if (BillingClient.ProductType.SUBS.equals(productType)) {
                    List<ProductDetails.SubscriptionOfferDetails> offerDetailsList = productDetails.getSubscriptionOfferDetails();
                    if (offerDetailsList == null || offerDetailsList.isEmpty()) {
                        if (onNotFound != null) onNotFound.run();
                        return;
                    }
                    String offerToken = offerDetailsList.get(0).getOfferToken();
                    productDetailsParamsList.add(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(productDetails)
                                    .setOfferToken(offerToken)
                                    .build()
                    );
                } else {
                    productDetailsParamsList.add(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(productDetails)
                                    .build()
                    );
                }

                BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(productDetailsParamsList)
                        .build();

                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        BillingResult flowResult = billingClient.launchBillingFlow(activity, billingFlowParams);
                        if (flowResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                            rejectSavedCall("تعذر إطلاق واجهة الشراء: " + flowResult.getDebugMessage());
                        }
                    });
                } else {
                    rejectSavedCall("النشاط الرئيسي غير متاح لاستكمال عملية الدفع");
                }

            } else {
                if (onNotFound != null) {
                    onNotFound.run();
                } else {
                    rejectSavedCall("تعذر جلب تفاصيل المنتج من Google Play");
                }
            }
        });
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            rejectSavedCall("تم إلغاء عملية الشراء من قبل المستخدم");
        } else {
            rejectSavedCall("فشلت عملية الشراء: " + billingResult.getDebugMessage());
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
                        completePurchaseSuccess();
                    } else {
                        rejectSavedCall("تعذر تأكيد الشراء من الخادم");
                    }
                });
            } else {
                completePurchaseSuccess();
            }
        }
    }

    private void completePurchaseSuccess() {
        getContext().getSharedPreferences("ApexPrefs", Context.MODE_PRIVATE)
                .edit().putBoolean("isPro", true).apply();

        if (savedBillingCall != null) {
            JSObject ret = new JSObject();
            ret.put("success", true);
            savedBillingCall.resolve(ret);
            savedBillingCall = null;
        }
    }

    private void rejectSavedCall(String reason) {
        if (savedBillingCall != null) {
            savedBillingCall.reject(reason);
            savedBillingCall = null;
        }
    }
}
