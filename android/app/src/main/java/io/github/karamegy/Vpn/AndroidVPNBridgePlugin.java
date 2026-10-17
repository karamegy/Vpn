package io.github.karamegy.Vpn;

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

    /**
     * فحص ما إذا كان إذن الـ VPN ممنوحاً مسبقاً من النظام
     */
    @PluginMethod
    public void isPrepared(PluginCall call) {
        Context context = getContext();
        if (context == null) {
            call.reject("السياق (Context) غير متوفر");
            return;
        }

        Intent prepareIntent = VpnService.prepare(context);
        JSObject ret = new JSObject();
        ret.put("prepared", prepareIntent == null);
        call.resolve(ret);
    }

    /**
     * طلب إذن إعداد نفق الـ VPN من المستخدم عبر نافذة أندرويد الرسمية
     */
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
            ret.put("success", true);
            call.resolve(ret);
        }
    }

    @ActivityCallback
    public void vpnPermissionResult(PluginCall call, ActivityResult result) {
        if (call == null) return;

        if (result.getResultCode() == Activity.RESULT_OK) {
            JSObject ret = new JSObject();
            ret.put("prepared", true);
            ret.put("success", true);
            call.resolve(ret);
        } else {
            call.reject("تم رفض إذن VPN من قبل المستخدم");
        }
    }

    /**
     * تشغيل خدمة الـ VPN وتمرير كافة بيانات السيرفر والتشفير
     */
    @PluginMethod
    public void startVpn(PluginCall call) {
        Context context = getContext();
        if (context == null) {
            call.reject("السياق (Context) غير متوفر");
            return;
        }

        // 1. التحقق الذاتي من الإذن؛ إذا لم يكن ممنوحاً يُطلب فوراً
        Intent prepareIntent = VpnService.prepare(context);
        if (prepareIntent != null) {
            startActivityForResult(call, prepareIntent, "vpnPermissionResult");
            return;
        }

        // 2. استخراج المعلمات من الواجهة الأمامية (JS)
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

            // 3. تشغيل الخدمة كـ Foreground Service لضمان عدم إغلاقها في الخلفية
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

    /**
     * إيقاف خدمة الـ VPN وإغلاق النفق
     */
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
            call.reject("فشل إيقاف خدمة الـ VPN: " + e.getMessage());
        }
    }

    /**
     * تبديل حالة الخدمة (تشغيل / إيقاف) بشكل موحد
     */
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
