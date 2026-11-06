package io.github.karamegy.Vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import com.wireguard.android.backend.Backend;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;
import com.wireguard.config.InetNetwork;
import com.wireguard.config.Interface;
import com.wireguard.config.Peer;
import com.wireguard.crypto.KeyPair;

public class ApexVpnService extends VpnService {
    private static final String TAG = "ApexVpnService";
    private static final String CHANNEL_ID = "ApexVpnChannel";
    private static final int NOTIFICATION_ID = 101;

    public static final String ACTION_CONNECT = "io.github.karamegy.Vpn.START_VPN";
    public static final String ACTION_DISCONNECT = "io.github.karamegy.Vpn.STOP_VPN";

    private Backend backend;
    private ApexTunnel currentTunnel;
    private volatile boolean isRunning = false;

    // كلاس تمثيل النفق المطلوب لمكتبة WireGuard
    private static class ApexTunnel implements Tunnel {
        private final String name;

        public ApexTunnel(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void onStateChange(State newState) {
            Log.i(TAG, "تغيرت حالة النفق إلى: " + newState);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // تهيئة محرك WireGuard عبر GoBackend
        backend = new GoBackend(getApplicationContext());
        currentTunnel = new ApexTunnel("ApexWarpTunnel");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : "";
        String serverName = intent != null ? intent.getStringExtra("serverName") : "Cloudflare WARP Node";
        String serverIp = intent != null ? intent.getStringExtra("serverIp") : "162.159.192.1";
        String warpPrivateKey = intent != null ? intent.getStringExtra("warpPrivateKey") : "";
        String warpClientIp = intent != null ? intent.getStringExtra("warpClientIp") : "172.16.0.2/32";

        if (ACTION_CONNECT.equals(action) || "START".equals(action) || "START_VPN".equals(action)) {
            startForegroundNotification(serverName);
            startWireGuardEngine(serverIp, serverName, warpPrivateKey, warpClientIp);
        } else if (ACTION_DISCONNECT.equals(action) || "STOP".equals(action) || "STOP_VPN".equals(action)) {
            stopVpnConnection();
        }

        return START_STICKY;
    }

    private synchronized void startWireGuardEngine(String serverIp, String serverName, String privateKeyHex, String clientIp) {
        if (isRunning) return;

        new Thread(() -> {
            try {
                // 1. إعداد واجهة WireGuard الداخلية مع سيرفرات DNS المحسنة
                Interface.Builder ifaceBuilder = new Interface.Builder();
                ifaceBuilder.addAddress(InetNetwork.parse(clientIp));
                ifaceBuilder.addDnsServer(java.net.InetAddress.getByName("94.140.14.14")); // AdGuard Primary (حجب الإعلانات والتهديدات)
                ifaceBuilder.addDnsServer(java.net.InetAddress.getByName("1.1.1.1"));      // Cloudflare Fallback

                if (privateKeyHex != null && !privateKeyHex.isEmpty()) {
                    ifaceBuilder.parsePrivateKey(privateKeyHex);
                } else {
                    KeyPair keyPair = new KeyPair();
                    ifaceBuilder.setKeyPair(keyPair);
                }

                // 2. إعداد سيرفر الوجهة (Peer)
                Peer.Builder peerBuilder = new Peer.Builder();
                peerBuilder.parsePublicKey("bmXOC+F1FxEMF9mTpdM6yWp166bNQA3JV4G2F2LAKkc=");
                peerBuilder.parseEndpoint(serverIp.contains(":") ? serverIp : serverIp + ":2408");
                peerBuilder.addAllowedIp(InetNetwork.parse("0.0.0.0/0"));

                // 3. تجميع التكوين الكامل
                Config wireGuardConfig = new Config.Builder()
                        .setInterface(ifaceBuilder.build())
                        .addPeer(peerBuilder.build())
                        .build();

                // 4. تشغيل النفق رسمياً عبر GoBackend
                backend.setState(currentTunnel, Tunnel.State.UP, wireGuardConfig);
                isRunning = true;

                Log.i(TAG, "✅ تم تشغيل محرك WireGuard الأصلي بنجاح عبر: " + serverName);

            } catch (Exception ex) {
                Log.e(TAG, "❌ فشل تشغيل محرك WireGuard:", ex);
                stopVpnConnection();
            }
        }).start();
    }

    private synchronized void stopVpnConnection() {
        if (!isRunning) return;
        isRunning = false;

        new Thread(() -> {
            try {
                if (backend != null && currentTunnel != null) {
                    backend.setState(currentTunnel, Tunnel.State.DOWN, null);
                }
            } catch (Exception e) {
                Log.e(TAG, "خطأ أثناء إغلاق النفق: ", e);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
            stopSelf();
            Log.i(TAG, "🔒 تم إيقاف خدمة الـ VPN.");
        }).start();
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
        String contentText = (serverName != null && !serverName.isEmpty()) ? "متصل للنفق عبر: " + serverName : "درع الحماية المشفرة نشط الآن";

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
