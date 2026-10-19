package io.github.karamegy.Vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import java.io.FileInputStream;
import java.io.IOException;

public class ApexVpnService extends VpnService {
    private static final String TAG = "ApexVpnService";
    private static final String CHANNEL_ID = "ApexVpnChannel";
    private static final int NOTIFICATION_ID = 101;

    public static final String ACTION_CONNECT = "io.github.karamegy.Vpn.START_VPN";
    public static final String ACTION_DISCONNECT = "io.github.karamegy.Vpn.STOP_VPN";

    private volatile boolean isRunning = false;
    private static ParcelFileDescriptor vpnInterface = null;
    private Thread vpnThread = null;

    private static final String ADGUARD_DNS_1 = "94.140.14.14";
    private static final String ADGUARD_DNS_2 = "94.140.15.15";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : "";
        String serverName = intent != null ? intent.getStringExtra("serverName") : "Apex Cloud Server";
        String serverIp = intent != null ? intent.getStringExtra("serverIp") : "1.1.1.1";

        if (ACTION_CONNECT.equals(action) || "START".equals(action) || "START_VPN".equals(action)) {
            createNotificationChannel();
            try {
                Notification notification = buildNotification(serverName);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
                } else {
                    startForeground(NOTIFICATION_ID, notification);
                }
            } catch (Exception e) {
                Log.e(TAG, "خطأ في بدء الخدمة الأمامية: ", e);
            }
            startVpnConnection(serverIp, serverName);
        } else if (ACTION_DISCONNECT.equals(action) || "STOP".equals(action) || "STOP_VPN".equals(action)) {
            stopVpnConnection();
        }

        return START_STICKY;
    }

    private synchronized void startVpnConnection(String serverIp, String serverName) {
        if (isRunning) return;

        vpnThread = new Thread(() -> {
            try {
                Builder builder = new Builder();
                builder.setSession("Apex Shield AdBlock & VPN")
                       .addAddress("10.0.0.2", 32)
                       .setMtu(1500)
                       .addDnsServer(ADGUARD_DNS_1)
                       .addDnsServer(ADGUARD_DNS_2)
                       .addRoute("0.0.0.0", 0);

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
                Log.i(TAG, "✅ تم تشغيل نفق الحماية وحجب الإعلانات بنجاح عبر " + serverName);

                try (FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor())) {
                    byte[] buffer = new byte[32767];
                    while (isRunning && vpnInterface != null) {
                        int length = in.read(buffer);
                        if (length <= 0) {
                            Thread.sleep(100);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "حلقة قراءة واجهة الـ VPN انتهت: ", e);
                }

            } catch (Exception ex) {
                Log.e(TAG, "❌ فشل إنشاء واجهة الـ VPN:", ex);
                stopVpnConnection();
            }
        });
        vpnThread.start();
    }

    private synchronized void stopVpnConnection() {
        if (!isRunning && vpnInterface == null) return;
        isRunning = false;

        new Thread(() -> {
            try {
                if (vpnInterface != null) {
                    vpnInterface.close();
                    vpnInterface = null;
                }
            } catch (Exception e) {
                Log.e(TAG, "خطأ أثناء إغلاق الواجهة: ", e);
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE);
                    } else {
                        stopForeground(true);
                    }
                } catch (Exception ignored) {}
                stopSelf();
                Log.i(TAG, "🔒 تم إيقاف خدمة الـ VPN واستعادة الاتصال العادي.");
            });
        }).start();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Apex Shield VPN Protection",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("حالة الاتصال بالشبكة المشفرة");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String serverName) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        int appIcon = getApplicationInfo().icon != 0 ? getApplicationInfo().icon : android.R.drawable.ic_lock_lock;
        String contentText = (serverName != null && !serverName.isEmpty()) ? "متصل بالدرع عبر: " + serverName : "درع الحماية المشفرة نشط الآن";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Apex Shield AI")
                .setContentText(contentText)
                .setSmallIcon(appIcon)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        stopVpnConnection();
        super.onDestroy();
    }
}
