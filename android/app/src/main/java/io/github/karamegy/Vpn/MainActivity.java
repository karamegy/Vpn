package io.github.karamegy.Vpn;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // تسجيل إضافة الجسر أولاً قبل إنشاء الجسر التابع لـ Capacitor
        registerPlugin(AndroidVPNBridgePlugin.class);
        
        super.onCreate(savedInstanceState);
    }
}
