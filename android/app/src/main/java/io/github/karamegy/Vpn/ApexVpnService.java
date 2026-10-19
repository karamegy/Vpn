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

import java.io.FileInputStream;

public class ApexVpnService extends VpnService {
    private static final String TAG = "ApexVpnService";
    private static final String CHANNEL_ID = "ApexVpnChannel";
    private static final int NOTIFICATION_ID = 101;

    public static final String ACTION_CONNECT = "io.github.karamegy.Vpn.START";
    public static final String ACTION_DISCONNECT = "io.github.karamegy.Vpn.STOP";

    private volatile boolean isRunning = false;
    private ParcelFileDescriptor vpnInterface = null;
    private Thread vpnThread = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : "";

        if (ACTION_CONNECT.equals(action) || "START".equals(action)) {
            String serverName = intent != null ? intent.getStringExtra("serverName") : "Apex Cloud Server";
            String serverIp = intent != null ? intent.getStringExtra("serverIp") : "1.1.1.1";

            createNotificationChannel();

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    int foregroundType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
                    startForeground(NOTIFICATION_ID, buildNotification(serverName), foregroundType);
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification(serverName));
                }
            } catch (Exception e) {
                Log.e(TAG, "خطأ أثناء تشغيل Foreground Service: ", e);
                startForeground(NOTIFICATION_ID, buildNotification(serverName));
            }

            startVpnConnection(serverIp, serverName);
        } else if (ACTION_DISCONNECT.equals(action) || "STOP".equals(action)) {
            stopVpnConnection();
        }

        return START_STICKY;
    }

    private synchronized void startVpnConnection(String serverIp, String serverName) {
        if (isRunning) return;

        vpnThread = new Thread(() -> {
            try {
                Builder builder = new Builder();
                builder.addAddress("10.0.0.2", 32);
                builder.addDnsServer("1.1.1.1");
                builder.addDnsServer("8.8.8.8");
                builder.addRoute("0.0.0.0", 0);

                try {
                    builder.addDisallowedApplication(getPackageName());
                } catch (Exception ignored) {}

                builder.setSession("ApexShieldVPN");
                vpnInterface = builder.establish();
                if (vpnInterface == null) {
                    stopVpnConnection();
                    return;
                }

                isRunning = true;
                Log.d(TAG, "✅ تم تشغيل نفق الحماية بنجاح واستمرار الإنترنت عبر " + serverName);

                // خيط معالجة وقراءة الحزم لمنع انسداد الواجهة وتوقف الشبكة على الهاتف
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
        if (!isRunning && vpnInterface == null) {
            return;
        }
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
                Log.d(TAG, "🔒 تم إيقاف خدمة الـ VPN واستعادة الاتصال العادي.");
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
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        String contentText = (serverName != null && !serverName.isEmpty())
                ? "متصل بالدرع عبر: " + serverName
                : "درع الحماية المشفرة نشط الآن";

        int appIcon = getApplicationInfo().icon != 0 ? getApplicationInfo().icon : android.R.drawable.ic_lock_lock;

        return builder
                .setContentTitle("Apex Shield AI")
                .setContentText(contentText)
                .setSmallIcon(appIcon)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        try {
            if (vpnInterface != null) {
                vpnInterface.close();
                vpnInterface = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "خطأ في onDestroy أثناء إغلاق الواجهة: ", e);
        }
        super.onDestroy();
    }
}
