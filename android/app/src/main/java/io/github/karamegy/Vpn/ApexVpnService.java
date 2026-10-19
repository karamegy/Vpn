package io.github.karamegy.Vpn;

import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramSocket;

public class ApexVpnService extends VpnService {
    private static final String TAG = "ApexVpnService";
    private ParcelFileDescriptor vpnInterface = null;
    private volatile boolean isRunning = false;
    private Thread vpnWorkerThread;

    private static final String ADGUARD_DNS_1 = "94.140.14.14";

    private synchronized void startVpnConnection(String serverIp, String serverName) {
        if (isRunning) return;

        vpnWorkerThread = new Thread(() -> {
            try {
                Builder builder = new Builder();
                builder.setSession("Apex Shield AdBlock")
                       .addAddress("10.0.0.2", 32)
                       .setMtu(1500)
                       .addDnsServer(ADGUARD_DNS_1)
                       .addRoute(ADGUARD_DNS_1, 32); // اقتصار التوجيه على سيرفر الـ DNS فقط

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        builder.addDisallowedApplication(getPackageName());
                    } catch (Exception ignored) {}
                }

                vpnInterface = builder.establish();
                if (vpnInterface == null) {
                    stopVpnConnection();
                    return;
                }

                isRunning = true;
                runDnsLoop();

            } catch (Exception ex) {
                Log.e(TAG, "فشل إنشاء واجهة الـ VPN:", ex);
                stopVpnConnection();
            }
        });
        vpnWorkerThread.start();
    }

    private void runDnsLoop() {
        try (FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
             FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor())) {

            DatagramSocket dnsSocket = new DatagramSocket();
            protect(dnsSocket); // حماية السوكت لمنع توجيه حزمه داخل نفق الـ VPN

            byte[] buffer = new byte[32767];
            while (isRunning) {
                int length = in.read(buffer);
                if (length > 0) {
                    // قراءة حزمة الـ DNS وإعادة توجيهها للخادم عبر dnsSocket
                    // ثم كتابة الرد إلى out.write()
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "خطأ في حلقة معالجة الحزم:", e);
        }
    }

    private synchronized void stopVpnConnection() {
        isRunning = false;
        try {
            if (vpnInterface != null) {
                vpnInterface.close();
                vpnInterface = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "خطأ أثناء إغلاق الواجهة: ", e);
        }
        stopSelf();
    }
}
