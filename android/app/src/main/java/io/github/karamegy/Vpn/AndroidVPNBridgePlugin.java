package io.github.karamegy.Vpn;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Collections;

@CapacitorPlugin(name = "AndroidVPNBridge")
public class AndroidVPNBridgePlugin extends Plugin implements PurchasesUpdatedListener {
    private static final String TAG = "AndroidVPNBridge";
    private static final int VPN_REQUEST_CODE = 0x9999;
    private PluginCall activeVpnCall = null;
    private BillingClient billingClient;
    private PluginCall activeBillingCall = null;

    @Override
    public void load() {
        super.load();
        Context context = getContext();
        if (context != null) {
            billingClient = BillingClient.newBuilder(context)
                    .setListener(this)
                    .enablePendingPurchases()
                    .build();
            startBillingConnection();
        }
    }

    private void startBillingConnection() {
        if (billingClient == null) return;
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Google Play Billing connected successfully.");
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                Log.w(TAG, "Google Play Billing service disconnected. Retrying...");
            }
        });
    }

    @PluginMethod
    public void checkSubscriptionStatus(PluginCall call) {
        if (billingClient == null || !billingClient.isReady()) {
            JSObject ret = new JSObject();
            ret.put("isPro", false);
            call.resolve(ret);
            return;
        }

        billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
                (billingResult, purchases) -> {
                    boolean hasActiveSub = false;
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                        for (Purchase purchase : purchases) {
                            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                                hasActiveSub = true;
                                break;
                            }
                        }
                    }
                    JSObject ret = new JSObject();
                    ret.put("isPro", hasActiveSub);
                    call.resolve(ret);
                }
        );
    }

    @PluginMethod
    public void launchBilling(PluginCall call) {
        Activity activity = getActivity();
        String productId = call.getString("productId", "");
        if (activity == null || productId.isEmpty()) {
            call.reject("النشاط غير متوفر أو معرف المنتج (productId) مفقود");
            return;
        }

        if (billingClient == null || !billingClient.isReady()) {
            call.reject("خدمة Google Play Billing غير جاهزة حالياً. تأكد من تثبيت التطبيق كـ APK رسمي من المتجر.");
            return;
        }

        activeBillingCall = call;

        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build();

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(product))
                .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && productDetailsList != null && !productDetailsList.isEmpty()) {
                ProductDetails productDetails = productDetailsList.get(0);
                java.util.List<ProductDetails.SubscriptionOfferDetails> offerDetailsList = productDetails.getSubscriptionOfferDetails();
                if (offerDetailsList == null || offerDetailsList.isEmpty()) {
                    if (activeBillingCall != null) {
                        activeBillingCall.reject("لا توجد عروض اشتراك متاحة لهذا المنتج.");
                        activeBillingCall = null;
                    }
                    return;
                }
                String offerToken = offerDetailsList.get(0).getOfferToken();

                BillingFlowParams.ProductDetailsParams productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build();

                BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(Collections.singletonList(productDetailsParams))
                        .build();

                BillingResult result = billingClient.launchBillingFlow(activity, flowParams);
                if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    if (activeBillingCall != null) {
                        activeBillingCall.reject("فشل إطلاق نافذة دفع Google Play: " + result.getDebugMessage());
                        activeBillingCall = null;
                    }
                }
            } else {
                if (activeBillingCall != null) {
                    activeBillingCall.reject("تعذر العثور على تفاصيل المنتج في متجر جوجل بلاي.");
                    activeBillingCall = null;
                }
            }
        });
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable java.util.List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            if (activeBillingCall != null) {
                activeBillingCall.reject("تم إلغاء عملية الشراء من قبل المستخدم.");
                activeBillingCall = null;
            }
        } else {
            if (activeBillingCall != null) {
                activeBillingCall.reject("حدث خطأ في عملية الشراء: " + billingResult.getDebugMessage());
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
                        if (activeBillingCall != null) {
                            JSObject ret = new JSObject();
                            ret.put("success", true);
                            ret.put("orderId", purchase.getOrderId());
                            activeBillingCall.resolve(ret);
                            activeBillingCall = null;
                        }
                    }
                });
            } else {
                if (activeBillingCall != null) {
                    JSObject ret = new JSObject();
                    ret.put("success", true);
                    activeBillingCall.resolve(ret);
                    activeBillingCall = null;
                }
            }
        }
    }

    @PluginMethod
    public void isPrepared(PluginCall call) {
        Context context = getContext();
        if (context == null) {
            call.reject("السياق غير متوفر");
            return;
        }
        Intent intent = VpnService.prepare(context);
        JSObject ret = new JSObject();
        ret.put("prepared", intent == null);
        call.resolve(ret);
    }

    @PluginMethod
    public void prepareVpn(PluginCall call) {
        Activity activity = getActivity();
        if (activity == null) {
            call.reject("النشاط غير متوفر");
            return;
        }

        Intent intent = VpnService.prepare(activity);
        if (intent != null) {
            activeVpnCall = call;
            try {
                activity.startActivityForResult(intent, VPN_REQUEST_CODE);
            } catch (Exception e) {
                activeVpnCall = null;
                call.reject("فشل إطلاق إذن الـ VPN: " + e.getMessage());
            }
        } else {
            JSObject ret = new JSObject();
            ret.put("prepared", true);
            ret.put("success", true);
            call.resolve(ret);
        }
    }

    @PluginMethod
    public void startVpn(PluginCall call) {
        Activity activity = getActivity();
        if (activity == null) {
            call.reject("النشاط غير متوفر");
            return;
        }

        Intent intent = VpnService.prepare(activity);
        if (intent != null) {
            activeVpnCall = call;
            try {
                activity.startActivityForResult(intent, VPN_REQUEST_CODE);
            } catch (Exception e) {
                activeVpnCall = null;
                call.reject("فشل طلب إذن الـ VPN: " + e.getMessage());
            }
            return;
        }

        executeVpnServiceStart(call);
    }

    @Override
    protected void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
        super.handleOnActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE) {
            PluginCall pendingCall = activeVpnCall;
            activeVpnCall = null;

            if (pendingCall != null) {
                if (resultCode == Activity.RESULT_OK) {
                    executeVpnServiceStart(pendingCall);
                } else {
                    pendingCall.reject("تم رفض إذن VPN من قبل المستخدم في نظام أندرويد");
                }
            }
        }
    }

    private void executeVpnServiceStart(PluginCall call) {
        Context context = getContext();
        if (context == null) {
            if (call != null) call.reject("السياق غير متوفر لتشغيل الخدمة");
            return;
        }

        String serverIp = call.getString("serverIp", "");
        String serverName = call.getString("serverName", "");
        String warpClientIp = call.getString("warpClientIp", "");
        String warpEndpoint = call.getString("warpEndpoint", "");
        String warpPrivateKey = call.getString("warpPrivateKey", "");

        try {
            Intent intent = new Intent(context, ApexVpnService.class);
            intent.setAction(ApexVpnService.ACTION_CONNECT);
            intent.putExtra("serverIp", serverIp);
            intent.putExtra("serverName", serverName);
            intent.putExtra("warpClientIp", warpClientIp);
            intent.putExtra("warpEndpoint", warpEndpoint);
            intent.putExtra("warpPrivateKey", warpPrivateKey);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }

            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("status", "connected");
            call.resolve(ret);
        } catch (Exception e) {
            Log.e(TAG, "فشل تشغيل خدمة الـ VPN: ", e);
            if (call != null) call.reject("فشل تشغيل الخدمة: " + e.getMessage());
        }
    }

    @PluginMethod
    public void stopVpn(PluginCall call) {
        Context context = getContext();
        if (context == null) {
            call.reject("السياق غير متوفر");
            return;
        }

        try {
            Intent intent = new Intent(context, ApexVpnService.class);
            intent.setAction(ApexVpnService.ACTION_DISCONNECT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }

            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("status", "disconnected");
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("فشل إيقاف الخدمة: " + e.getMessage());
        }
    }

    @PluginMethod
    public void toggleVpn(PluginCall call) {
        Boolean enable = call.getBoolean("enable", false);
        if (Boolean.TRUE.equals(enable)) {
            startVpn(call);
        } else {
            stopVpn(call);
        }
    }
}
