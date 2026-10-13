package com.apex.shield;

import android.content.Intent;
import android.net.VpnService;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "AndroidVPNBridge")
public class AndroidVPNBridgePlugin extends Plugin {

    @PluginMethod
    public void toggleVpn(PluginCall call) {
        Boolean enable = call.getBoolean("enable", false);
        String serverIp = call.getString("serverIp", "");
        String serverName = call.getString("serverName", "");

        Intent intent = new Intent(getContext(), ApexVpnService.class);

        if (enable) {
            Intent prepareIntent = VpnService.prepare(getContext());
            if (prepareIntent != null) {
                // طلب إذن الاتصال بالنظام لأول مرة
                getActivity().startActivityForResult(prepareIntent, 0);
            } else {
                intent.setAction("START");
                intent.putExtra("serverIp", serverIp);
                intent.putExtra("serverName", serverName);
                getContext().startService(intent);
            }
        } else {
            intent.setAction("STOP");
            getContext().startService(intent);
        }

        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
    }
}
