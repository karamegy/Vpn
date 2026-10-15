package com.apex.shield;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "AndroidVPNBridge")
public class AndroidVPNBridgePlugin extends Plugin {

    @PluginMethod
    public void prepareVpn(PluginCall call) {
        Context context = getContext();
        if (context == null) {
            call.reject("السياق (Context) غير متوفر");
            return;
        }

        Intent intent = VpnService.prepare(context);
        if (intent != null) {
            startActivityForResult(call, intent, "vpnPermissionResult");
        } else {
            JSObject ret = new JSObject();
            ret.put("prepared", true);
            call.resolve(ret);
        }
    }

    @ActivityCallback
    public void vpnPermissionResult(PluginCall call, ActivityResult result) {
        if (call == null) return;

        if (result.getResultCode() == Activity.RESULT_OK) {
            JSObject ret = new JSObject();
            ret.put("prepared", true);
            call.resolve(ret);
        } else {
            call.reject("تم رفض إذن VPN من قبل المستخدم");
        }
    }

    @PluginMethod
    public void toggleVpn(PluginCall call) {
        Context context = getContext();
        if (context == null) {
            call.reject("السياق (Context) غير متوفر");
            return;
        }

        Boolean enable = call.getBoolean("enable", false);
        String serverIp = call.getString("serverIp", "");
        String serverName = call.getString("serverName", "");

        Intent intent = new Intent(context, ApexVpnService.class);

        if (Boolean.TRUE.equals(enable)) {
            Intent prepareIntent = VpnService.prepare(context);
            if (prepareIntent != null) {
                // طلب الإذن باستخدام أداة Capacitor المعتمدة
                startActivityForResult(call, prepareIntent, "vpnPermissionResult");
            } else {
                try {
                    intent.setAction(ApexVpnService.ACTION_CONNECT);
                    intent.putExtra("serverIp", serverIp);
                    intent.putExtra("serverName", serverName);

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
                    call.reject("فشل تشغيل خدمة الـ VPN: " + e.getMessage());
                }
            }
        } else {
            try {
                intent.setAction(ApexVpnService.ACTION_DISCONNECT);
                context.startService(intent);

                JSObject ret = new JSObject();
                ret.put("success", true);
                ret.put("status", "disconnected");
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("فشل إيقاف خدمة الـ VPN: " + e.getMessage());
            }
        }
    }

    @PluginMethod
    public void startVpn(PluginCall call) {
        Context context = getContext();
        if (context == null) {
            call.reject("السياق (Context) غير متوفر");
            return;
        }

        String serverIp = call.getString("serverIp", "");
        String serverName = call.getString("serverName", "");

        try {
            Intent intent = new Intent(context, ApexVpnService.class);
            intent.setAction(ApexVpnService.ACTION_CONNECT);
            intent.putExtra("serverIp", serverIp);
            intent.putExtra("serverName", serverName);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }

            JSObject ret = new JSObject();
            ret.put("status", "connected");
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("فشل تشغيل خدمة الـ VPN: " + e.getMessage());
        }
    }

    @PluginMethod
    public void stopVpn(PluginCall call) {
        Context context = getContext();
        if (context == null) {
            call.reject("السياق (Context) غير متوفر");
            return;
        }

        try {
            Intent intent = new Intent(context, ApexVpnService.class);
            intent.setAction(ApexVpnService.ACTION_DISCONNECT);
            context.startService(intent);

            JSObject ret = new JSObject();
            ret.put("status", "disconnected");
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("فشل إيقاف خدمة الـ VPN: " + e.getMessage());
        }
    }
}
