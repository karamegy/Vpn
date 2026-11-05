package io.github.karamegy.Vpn;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.StatFs;

import androidx.activity.result.ActivityResult;
import androidx.core.content.ContextCompat;

import com.android.billingclient.api.*;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.util.Collections;
import java.util.List;

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
        String serverIp = call.getString("serverIp", "162.159.192.1");
        String serverName = call.getString("serverName", "Apex WireGuard Shield");
        String warpPrivateKey = call.getString("warpPrivateKey", "");
        String warpClientIp = call.getString("warpClientIp", "10.0.0.2/32");
        String warpEndpoint = call.getString("warpEndpoint", "");

        Intent intent = new Intent(getContext(), ApexVpnService.class);

        if (enable) {
            intent.setAction(ApexVpnService.ACTION_CONNECT);
            intent.putExtra("serverIp", serverIp);
            intent.putExtra("serverName", serverName);
            intent.putExtra("warpPrivateKey", warpPrivateKey);
            intent.putExtra("warpClientIp", warpClientIp);
            intent.putExtra("warpEndpoint", warpEndpoint);
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

    // ✅ دالة الفحص الحقيقي للعتاد والمساحة وذاكرة RAM (بدون أي أرقام محاكاة)
    @PluginMethod
    public void performNativeScan(PluginCall call) {
        try {
            ActivityManager actManager = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            if (actManager != null) {
                actManager.getMemoryInfo(memInfo);
            }

            File path = Environment.getDataDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSizeLong();
            long totalBlocks = stat.getBlockCountLong();
            long availableBlocks = stat.getAvailableBlocksLong();

            double totalStorageMB = (totalBlocks * blockSize) / (1024.0 * 1024.0);
            double usedStorageMB = ((totalBlocks - availableBlocks) * blockSize) / (1024.0 * 1024.0);

            int appsCount = getContext().getPackageManager().getInstalledApplications(0).size();

            JSObject ret = new JSObject();
            ret.put("scannedMB", String.format("%.1f", usedStorageMB));
            ret.put("totalMB", String.format("%.0f", totalStorageMB));
            ret.put("ramAvailableMB", String.format("%.1f", memInfo.availMem / (1024.0 * 1024.0)));
            ret.put("scannedApps", appsCount);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("خطأ في قراءة بيانات الهاتف الحقيقية", e);
        }
    }

    // ✅ دالة قراءة معلومات شبكة الواي فاي الحقيقية
    @PluginMethod
    public void getWifiInfo(PluginCall call) {
        try {
            WifiManager wifiManager = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wifiManager != null ? wifiManager.getConnectionInfo() : null;

            JSObject ret = new JSObject();
            if (info != null && info.getNetworkId() != -1) {
                ret.put("ssid", info.getSSID().replace("\"", ""));
                ret.put("rssi", info.getRssi() + " dBm");
                ret.put("linkSpeed", info.getLinkSpeed() + " Mbps");
                ret.put("isConnected", true);
            } else {
                ret.put("ssid", "غير متصل شبكة لاسلكية");
                ret.put("rssi", "N/A");
                ret.put("linkSpeed", "N/A");
                ret.put("isConnected", false);
            }
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("خطأ في قراءة بيانات الواي فاي", e);
        }
    }

    // ✅ دالة الفحص الحقيقي للتطبيقات وصلاحيات التجسس (Stalkerware & App Inspection)
    @PluginMethod
    public void auditInstalledApps(PluginCall call) {
        try {
            PackageManager pm = getContext().getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            JSArray highRiskApps = new JSArray();
            JSArray installedAppsList = new JSArray();

            for (ApplicationInfo app : apps) {
                String appLabel = pm.getApplicationLabel(app).toString();
                String pkgName = app.packageName;

                JSObject appItem = new JSObject();
                appItem.put("name", appLabel);
                appItem.put("packageName", pkgName);
                appItem.put("isSystem", (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                installedAppsList.put(appItem);

                // فحص تطبيقات الطرف الثالث المرتفعة الصلاحيات
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    try {
                        PackageInfo pkgInfo = pm.getPackageInfo(pkgName, PackageManager.GET_PERMISSIONS);
                        if (pkgInfo.requestedPermissions != null) {
                            boolean hasCam = false, hasMic = false, hasLoc = false, hasAccessibility = false;
                            for (String p : pkgInfo.requestedPermissions) {
                                if (p.contains("CAMERA")) hasCam = true;
                                if (p.contains("RECORD_AUDIO")) hasMic = true;
                                if (p.contains("ACCESS_FINE_LOCATION")) hasLoc = true;
                                if (p.contains("BIND_ACCESSIBILITY_SERVICE")) hasAccessibility = true;
                            }
                            if ((hasCam && hasMic) || hasAccessibility) {
                                JSObject riskApp = new JSObject();
                                riskApp.put("appName", appLabel);
                                riskApp.put("packageName", pkgName);
                                riskApp.put("hasCam", hasCam);
                                riskApp.put("hasMic", hasMic);
                                riskApp.put("hasLoc", hasLoc);
                                riskApp.put("hasAccessibility", hasAccessibility);
                                highRiskApps.put(riskApp);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            JSObject ret = new JSObject();
            ret.put("highRiskApps", highRiskApps);
            ret.put("installedApps", installedAppsList);
            ret.put("totalApps", apps.size());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("خطأ في فحص صلاحيات التطبيقات", e);
        }
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

        billingClient.queryProductDetailsAsync(params, (result, queryProductDetailsResult) -> {
            List<ProductDetails> productDetailsList = (queryProductDetailsResult != null) 
                ? queryProductDetailsResult.getProductDetailsList() 
                : null;

            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK 
                    && productDetailsList != null 
                    && !productDetailsList.isEmpty()) {
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
