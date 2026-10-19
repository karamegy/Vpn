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

    // خوادم DNS المخصصة لحجب الإعلانات والبرمجيات الخبيثة وتشفير الطلبات
    private static final String PRIMARY_ADBLOCK_DNS = "94.140.14.14";   // AdGuard Default DNS
    private static final String SECONDARY_ADBLOCK_DNS = "94.140.15.15"; // AdGuard Secondary DNS
    private static final String CLOUDFLARE_SECURE_DNS = "1.1.1.2";     // Cloudflare Malware & Ad Block

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_CONNECT.equals(action) || "START".equals(action)) {
                String serverIp = intent.getStringExtra("serverIp");
                String warpClientIp = intent.getStringExtra("warpClientIp");
                startVpnTunnel(serverIp, warpClientIp);
            } else if (ACTION_DISCONNECT.equals(action) || "STOP".equals(action)) {
                stopVpnTunnel();
            }
        }
        return START_STICKY;
    }

    private synchronized void startVpnTunnel(String customServerIp, String warpClientIp) {
        if (isRunning) return;

        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Apex Shield AI نشط")
                .setContentText("حماية DNS وحجب الإعلانات مفعّلة بنجاح")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();

        startForeground(1, notification);

        try {
            Builder builder = new Builder();

            // 1. تحديد عنوان IP افتراضي لواجهة النفق المحلية
            String localIp = (warpClientIp != null && !warpClientIp.isEmpty()) ? warpClientIp : "10.0.0.2";
            builder.setSession("Apex Shield DNS Guard")
                   .addAddress(localIp, 32)
                   .setMtu(1500);

            // 2. إسناد خوادم DNS المشفرة المحصنة ضد الإعلانات
            builder.addDnsServer(PRIMARY_ADBLOCK_DNS)
                   .addDnsServer(SECONDARY_ADBLOCK_DNS)
                   .addDnsServer(CLOUDFLARE_SECURE_DNS);

            // 3. توجيه حركة طلبات الـ DNS فقط عبر الواجهة لضمان عدم انقطاع الإنترنت
            builder.addRoute(PRIMARY_ADBLOCK_DNS, 32);
            builder.addRoute(SECONDARY_ADBLOCK_DNS, 32);
            builder.addRoute(CLOUDFLARE_SECURE_DNS, 32);

            if (customServerIp != null && !customServerIp.isEmpty() && !customServerIp.equals("0.0.0.0")) {
                try {
                    builder.addRoute(customServerIp, 32);
                } catch (Exception e) {
                    Log.w(TAG, "تعذر إضافة مسار السيرفر المخصص: " + customServerIp);
                }
            }

            // 4. استثناء تطبيق Apex Shield نفسه من النفق لمنع التكرار اللانهائي (Loopback)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    builder.addDisallowedApplication(getPackageName());
                } catch (Exception ignored) {}
            }

            builder.setBlocking(false);
            vpnInterface = builder.establish();

            if (vpnInterface != null) {
                isRunning = true;
                Log.i(TAG, "تم تشغيل نفق Apex Shield وحجب الإعلانات بنجاح.");
            } else {
                Log.e(TAG, "فشل إنشاء واجهة الـ VPN.");
            }
        } catch (Exception e) {
            Log.e(TAG, "خطأ أثناء تشغيل خدمة الـ VPN: ", e);
            stopVpnTunnel();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Apex Shield VPN Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("إشعارات حالة الاتصال والتشفير لتطبيق Apex Shield");
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
                Log.e(TAG, "خطأ أثناء إغلاق واجهة الـ VPN: ", e);
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
