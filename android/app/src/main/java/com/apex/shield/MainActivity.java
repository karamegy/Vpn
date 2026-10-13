package com.apex.shield;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // تسجيل إضافة الجسر المباشر لدى Capacitor
        registerPlugin(AndroidVPNBridgePlugin.class);
    }
}
