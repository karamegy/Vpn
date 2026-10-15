package com.apex.shield;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

public class ApexVpnService extends VpnService {
    private static final String TAG = "ApexVpnService";
    private static final String CHANNEL_ID = "ApexVpnChannel";
    private static final int NOTIFICATION_ID = 101;

    // ثوابت الأوامر لمطابقة الجسر AndroidVPNBridgePlugin
    public static final String ACTION_CONNECT = "com.apex.shield.START";
    public static final String ACTION_DISCONNECT = "com.apex.shield.STOP";

    private ParcelFileDescriptor vpnInterface = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : "";

        // قبول الأوامر بجميع صيغها (العامة والمباشرة)
        if (ACTION_CONNECT.equals(action) || "START".equals(action)) {
            createNotificationChannel();
            Notification notification = buildNotification();
            startForeground(NOTIFICATION_ID, notification);
            startVpn();
        } else if (ACTION_DISCONNECT.equals(action) || "STOP".equals(action)) {
            stopVpn();
        }

        return START_STICKY;
    }

    private void startVpn() {
        if (vpnInterface != null) return;

        try {
            Builder builder = new Builder();
            // توجيه حركة المرور وتعيين خوادم DNS المانعة للإعلانات والبرمجيات الخبيثة
            builder.addAddress("10.1.10.1", 24)
                   .addRoute("0.0.0.0", 0)
                   .addDnsServer("1.1.1.3") // Cloudflare DNS الآمن
                   .addDnsServer("1.0.0.3")
                   .setSession("ApexShieldVPN");

            vpnInterface = builder.establish();
            Log.d(TAG, "✅ تم إنشاء نفق VPN وتأمين اتصالات الهاتف بالكامل!");
        } catch (Exception e) {
            Log.e(TAG, "❌ خطأ في إنشاء نفق VPN:", e);
        }
    }

    private void stopVpn() {
        try {
            if (vpnInterface != null) {
                vpnInterface.close();
                vpnInterface = null;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }

            stopSelf();
            Log.d(TAG, "🔒 تم إيقاف خدمة VPN بنجاح.");
        } catch (Exception e) {
            Log.e(TAG, "❌ خطأ عند إيقاف خدمة VPN:", e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Apex Shield VPN Protection",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("حالة الاتصال بالشبكة الافتراضية المشفرة");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        // فتح MainActivity عند النقر على الإشعار
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
            .setContentTitle("Apex Shield AI")
            .setContentText("درع الحماية وخدمة VPN المشفرة نشطة الآن")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }
}
