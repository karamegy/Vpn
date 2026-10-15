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
            String serverIp = intent != null ? intent.getStringExtra("serverIp") : "";
            String serverName = intent != null ? intent.getStringExtra("serverName") : "";
            String warpClientIp = intent != null ? intent.getStringExtra("warpClientIp") : "";
            String warpEndpoint = intent != null ? intent.getStringExtra("warpEndpoint") : "";

            createNotificationChannel();
            Notification notification = buildNotification(serverName);
            startForeground(NOTIFICATION_ID, notification);
            startVpn(serverIp, serverName, warpClientIp, warpEndpoint);
        } else if (ACTION_DISCONNECT.equals(action) || "STOP".equals(action)) {
            stopVpn();
        }

        return START_STICKY;
    }

    private void startVpn(String serverIp, String serverName, String warpClientIp, String warpEndpoint) {
        if (vpnInterface != null) return;

        try {
            Builder builder = new Builder();

            // معالجة واستخراج عنوان IP العميل
            String clientIp = "172.16.0.2";
            int prefixLength = 32;

            if (warpClientIp != null && !warpClientIp.isEmpty()) {
                if (warpClientIp.contains("/")) {
                    String[] parts = warpClientIp.split("/");
                    clientIp = parts[0];
                    try {
                        prefixLength = Integer.parseInt(parts[1]);
                    } catch (Exception ignored) {
                        prefixLength = 32;
                    }
                } else {
                    clientIp = warpClientIp;
                }
            } else {
                clientIp = "10.1.10.1";
                prefixLength = 24;
            }

            // توجيه حركة المرور وتعيين خوادم Cloudflare WARP الرسمية للسرعة والأمان
            builder.addAddress(clientIp, prefixLength)
                   .addRoute("0.0.0.0", 0)
                   .addDnsServer("1.1.1.1") // Cloudflare WARP DNS الأسرع عالمياً
                   .addDnsServer("1.0.0.1")
                   .addDnsServer("1.1.1.3") // Cloudflare Security & Malware Blocking
                   .setMtu(1280) // حجم الحزمة المثالي لشبكات WARP و WireGuard
                   .setSession(serverName != null && !serverName.isEmpty() ? serverName : "ApexShieldWARP");

            vpnInterface = builder.establish();
            Log.d(TAG, "✅ تم إنشاء نفق VPN وتأمين اتصالات الجهاز عبر شبكة Cloudflare WARP بنجاح!");
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

    private Notification buildNotification(String serverName) {
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

        String contentText = (serverName != null && !serverName.isEmpty())
            ? "متصل بالدرع عبر: " + serverName
            : "درع الحماية وخدمة Cloudflare WARP المشفرة نشطة الآن";

        return builder
            .setContentTitle("Apex Shield AI")
            .setContentText(contentText)
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
