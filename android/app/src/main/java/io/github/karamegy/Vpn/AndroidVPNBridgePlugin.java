package io.github.karamegy.Vpn;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;

import androidx.activity.result.ActivityResult;
import androidx.core.content.ContextCompat;

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
}
