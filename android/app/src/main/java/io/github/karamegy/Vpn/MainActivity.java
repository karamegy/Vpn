package io.github.karamegy.Vpn;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AndroidVPNBridgePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
