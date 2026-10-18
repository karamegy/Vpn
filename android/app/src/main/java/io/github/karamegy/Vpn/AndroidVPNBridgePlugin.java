package io.github.karamegy.Vpn;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "AndroidVPNBridge")
public class AndroidVPNBridgePlugin extends Plugin {
    private static final String TAG = "AndroidVPNBridge";
    private static final int VPN_REQUEST_CODE = 0x9999;
    private PluginCall activeVpnCall = null;

    @PluginMethod
    public void checkSubscriptionStatus(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("isPro", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void launchBilling(PluginCall call) {
        String productId = call.getString("productId", "");
        JSObject ret = new JSObject();
        ret.put("success", true);
        ret.put("productId", productId);
        call.resolve(ret);
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
