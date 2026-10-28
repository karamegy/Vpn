package io.github.karamegy.Vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ApexVpnService extends VpnService {
    private static final String TAG = "ApexVpnService";
    private static final String CHANNEL_ID = "ApexVpnChannel";
    private static final int NOTIFICATION_ID = 101;

    public static final String ACTION_CONNECT = "io.github.karamegy.Vpn.START_VPN";
    public static final String ACTION_DISCONNECT = "io.github.karamegy.Vpn.STOP_VPN";

    private ParcelFileDescriptor vpnInterface = null;
    private volatile boolean isRunning = false;
    private Thread vpnWorkerThread = null;
    private Thread vpnThread = null;

    private static final String ADGUARD_DNS = "94.140.14.14";
    private static final String ADGUARD_DNS_1 = "94.140.14.14";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : "";
        String serverName = intent != null ? intent.getStringExtra("serverName") : "Apex Shield Node";
        String serverIp = intent != null ? intent.getStringExtra("serverIp") : "162.159.192.1";

        if (ACTION_CONNECT.equals(action) || "START".equals(action) || "START_VPN".equals(action)) {
            startForegroundNotification(serverName);
            startVpnConnection(serverIp, serverName);
        } else if (ACTION_DISCONNECT.equals(action) || "STOP".equals(action) || "STOP_VPN".equals(action)) {
            stopVpnConnection();
        }

        return START_STICKY;
    }

    private void startForegroundNotification(String serverName) {
        createNotificationChannel();
        Notification notification = buildNotification(serverName);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "خطأ في بدء الخدمة الأمامية: ", e);
        }
    }

    private synchronized void startVpnConnection(String serverIp, String serverName) {
        if (isRunning) return;
        isRunning = true;

        vpnThread = new Thread(() -> {
            try {
                Builder builder = new Builder();
                builder.setSession("Apex Shield AdBlock")
                       .addAddress("10.0.0.2", 32)
                       .setMtu(1500)
                       .addDnsServer(ADGUARD_DNS_1)
                       .addRoute(ADGUARD_DNS_1, 32)
                       .addDnsServer(ADGUARD_DNS)
                       .addRoute(ADGUARD_DNS, 32);

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

                Log.i(TAG, "✅ تم تشغيل نفق الـ VPN بنجاح عبر: " + serverName);
                runDnsLoop();

            } catch (Exception ex) {
                Log.e(TAG, "❌ فشل إنشاء واجهة الـ VPN:", ex);
                stopVpnConnection();
            }
        });
        vpnWorkerThread = vpnThread;
        vpnThread.start();
    }

    private void runDnsLoop() {
        DatagramSocket dnsSocket = null;
        try (FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
             FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor())) {

            dnsSocket = new DatagramSocket();
            protect(dnsSocket); // حماية السوكت لمنع توجيه حزمه داخل نفق الـ VPN
            dnsSocket.setSoTimeout(3000);

            byte[] buffer = new byte[32767];
            InetAddress dnsTarget = InetAddress.getByName(ADGUARD_DNS);

            while (isRunning && !Thread.currentThread().isInterrupted()) {
                int length = in.read(buffer);
                if (length > 0) {
                    try {
                        DatagramPacket outPacket = new DatagramPacket(buffer, length, dnsTarget, 53);
                        dnsSocket.send(outPacket);

                        byte[] respBuffer = new byte[32767];
                        DatagramPacket inPacket = new DatagramPacket(respBuffer, respBuffer.length);
                        dnsSocket.receive(inPacket);

                        out.write(inPacket.getData(), 0, inPacket.getLength());
                    } catch (Exception reqErr) {
                        Log.w(TAG, "حزمة غير متوافقة أو تجاوز الوقت المحدد للـ DNS");
                    }
                }
            }
        } catch (Exception e) {
            if (isRunning) {
                Log.e(TAG, "إغلاق حلقة معالجة الحزم:", e);
            }
        } finally {
            if (dnsSocket != null && !dnsSocket.isClosed()) {
                dnsSocket.close();
            }
        }
    }

    private synchronized void stopVpnConnection() {
        if (!isRunning && vpnInterface == null) return;
        isRunning = false;

        if (vpnThread != null) {
            vpnThread.interrupt();
        }
        if (vpnWorkerThread != null) {
            vpnWorkerThread.interrupt();
        }

        try {
            if (vpnInterface != null) {
                vpnInterface.close();
                vpnInterface = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "خطأ أثناء إغلاق الواجهة: ", e);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
        Log.i(TAG, "🔒 تم إيقاف خدمة الـ VPN واستعادة الاتصال العادي.");
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
