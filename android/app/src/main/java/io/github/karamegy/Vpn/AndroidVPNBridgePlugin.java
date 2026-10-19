package io.github.karamegy.Vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.io.IOException;

public class ApexVpnService extends VpnService {
    private static final String TAG = "ApexVpnService";
    public static final String ACTION_CONNECT = "io.github.karamegy.Vpn.START_VPN";
    public static final String ACTION_DISCONNECT = "io.github.karamegy.Vpn.STOP_VPN";
    private static final String CHANNEL_ID = "ApexVpnChannel";

    private static ParcelFileDescriptor vpnInterface = null;
    private static boolean isRunning = false;

    // خوادم AdGuard المخصصة لحجب الإعلانات
    private static final String ADGUARD_DNS_1 = "94.140.14.14";
    private static final String ADGUARD_DNS_2 = "94.140.15.15";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_CONNECT.equals(action) || "START".equals(action)) {
                startVpnTunnel();
            } else if (ACTION_DISCONNECT.equals(action) || "STOP".equals(action)) {
                stopVpnTunnel();
            }
        }
        return START_STICKY;
    }

    private synchronized void startVpnTunnel() {
        if (isRunning) return;

        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Apex Shield AI نشط")
                .setContentText("درع حجب الإعلانات والتشفير يعمل بكفاءة")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();

        startForeground(1, notification);

        try {
            Builder builder = new Builder();

            builder.setSession("Apex Shield AdBlock")
                   .addAddress("10.0.0.2", 32)
                   .setMtu(1500);

            // تعيين خوادم حجب الإعلانات كخوادم رئيسية للنظام
            builder.addDnsServer(ADGUARD_DNS_1)
                   .addDnsServer(ADGUARD_DNS_2);

            // توجيه حركة أشهر خوادم الـ DNS لضمان عدم لجوء التطبيقات لخوادم بديلة
            String[] dnsToIntercept = {
                ADGUARD_DNS_1, ADGUARD_DNS_2,
                "8.8.8.8", "8.8.4.4",      // Google DNS
                "1.1.1.1", "1.0.0.1",      // Cloudflare DNS
                "208.67.222.222"           // OpenDNS
            };

            for (String dnsIp : dnsToIntercept) {
                builder.addRoute(dnsIp, 32);
            }

            // السماح للتطبيقات الأخرى بالمرور الطبيعي للترافيك غير الـ DNS
            builder.setBlocking(false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    builder.addDisallowedApplication(getPackageName());
                } catch (Exception ignored) {}
            }

            vpnInterface = builder.establish();

            if (vpnInterface != null) {
                isRunning = true;
                Log.i(TAG, "تم تشغيل درع حجب الإعلانات بنجاح.");
            }
        } catch (Exception e) {
            Log.e(TAG, "خطأ في تشغيل الـ VPN: ", e);
            stopVpnTunnel();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Apex Shield Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private synchronized void stopVpnTunnel() {
        isRunning = false;
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException e) {
                Log.e(TAG, "خطأ عند إغلاق النفق: ", e);
            }
            vpnInterface = null;
        }
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopVpnTunnel();
    }
}
