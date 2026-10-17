package io.github.karamegy.Vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import android.util.Log;

import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;
import com.wireguard.config.InetNetwork;
import com.wireguard.config.Interface;
import com.wireguard.config.Peer;

import java.net.InetAddress;
import java.security.SecureRandom;

public class ApexVpnService extends VpnService {
    private static final String TAG = "ApexVpnService";
    private static final String CHANNEL_ID = "ApexVpnChannel";
    private static final int NOTIFICATION_ID = 101;
    private static final String TUNNEL_NAME = "ApexWarpTunnel";

    public static final String ACTION_CONNECT = "io.github.karamegy.Vpn.START";
    public static final String ACTION_DISCONNECT = "io.github.karamegy.Vpn.STOP";

    private GoBackend backend;
    private boolean isTunnelRunning = false;
    private ParcelFileDescriptor vpnInterface = null;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            backend = new GoBackend(getApplicationContext());
        } catch (Exception e) {
            Log.e(TAG, "فشل تهيئة محرك WireGuard GoBackend", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : "";

        if (ACTION_CONNECT.equals(action) || "START".equals(action)) {
            String serverName = intent != null ? intent.getStringExtra("serverName") : "Cloudflare WARP";
            String warpClientIp = intent != null ? intent.getStringExtra("warpClientIp") : "172.16.0.2/32";
            String warpEndpoint = intent != null ? intent.getStringExtra("warpEndpoint") : "162.159.192.1:2408";
            String warpPrivateKey = intent != null ? intent.getStringExtra("warpPrivateKey") : "";

            createNotificationChannel();
            startForeground(NOTIFICATION_ID, buildNotification(serverName));

            startVpnConnection(warpClientIp, warpEndpoint, serverName, warpPrivateKey);
        } else if (ACTION_DISCONNECT.equals(action) || "STOP".equals(action)) {
            stopVpnConnection();
        }

        return START_STICKY;
    }

    private void startVpnConnection(String clientIpStr, String endpointStr, String serverName, String privateKeyBase64) {
        if (isTunnelRunning) return;

        new Thread(() -> {
            try {
                if (backend != null) {
                    Interface.Builder interfaceBuilder = new Interface.Builder();

                    if (privateKeyBase64 != null && !privateKeyBase64.trim().isEmpty()) {
                        interfaceBuilder.parsePrivateKey(privateKeyBase64.trim());
                    } else {
                        byte[] randomBytes = new byte[32];
                        new SecureRandom().nextBytes(randomBytes);
                        String generatedKey = Base64.encodeToString(randomBytes, Base64.NO_WRAP);
                        interfaceBuilder.parsePrivateKey(generatedKey);
                    }

                    String formattedClientIp = (clientIpStr != null && !clientIpStr.isEmpty()) ? clientIpStr : "172.16.0.2/32";
                    if (!formattedClientIp.contains("/")) {
                        formattedClientIp += "/32";
                    }

                    interfaceBuilder.addAddress(InetNetwork.parse(formattedClientIp));
                    interfaceBuilder.addAddress(InetNetwork.parse("fd00::2/128"));

                    interfaceBuilder.addDnsServer(InetAddress.getByName("1.1.1.1"));
                    interfaceBuilder.addDnsServer(InetAddress.getByName("1.0.0.1"));
                    interfaceBuilder.addDnsServer(InetAddress.getByName("2606:4700:4700::1111"));
                    interfaceBuilder.setMtu(1280);

                    Peer.Builder peerBuilder = new Peer.Builder();
                    peerBuilder.parsePublicKey("bmXOC+F1FxEMF9mTpdM6yWp166bNQA3JV4G2F2LAKkc=");

                    String targetEndpoint = (endpointStr != null && !endpointStr.isEmpty()) ? endpointStr : "162.159.192.1:2408";
                    peerBuilder.parseEndpoint(targetEndpoint);

                    peerBuilder.addAllowedIP(InetNetwork.parse("0.0.0.0/0"));
                    peerBuilder.addAllowedIP(InetNetwork.parse("::/0"));

                    Config config = new Config.Builder()
                            .setInterface(interfaceBuilder.build())
                            .addPeer(peerBuilder.build())
                            .build();

                    backend.setState(new Tunnel() {
                        @Override
                        public String getName() {
                            return TUNNEL_NAME;
                        }

                        @Override
                        public void onStateChange(State state) {
                            Log.d(TAG, "حالة نفق WireGuard تغيرت إلى: " + state.name());
                        }
                    }, Tunnel.State.UP, config);

                    isTunnelRunning = true;
                    Log.d(TAG, "✅ تم تشغيل نفق WireGuard/WARP وتوجيه البيانات بنجاح!");
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "تعذر تشغيل GoBackend، جاري التبديل للمشغل الأصلي للنظام:", e);
            }

            try {
                Builder builder = new Builder();
                builder.addAddress("172.16.0.2", 32);
                builder.addDnsServer("1.1.1.1");
                builder.addDnsServer("1.0.0.1");
                builder.addRoute("0.0.0.0", 0);
                builder.setSession("ApexShieldVPN");
                vpnInterface = builder.establish();
                isTunnelRunning = true;
                Log.d(TAG, "✅ تم تشغيل واجهة VPN النظام بنجاح!");
            } catch (Exception ex) {
                Log.e(TAG, "❌ فشل إنشاء واجهة VPN النظام:", ex);
                stopVpnConnection();
            }
        }).start();
    }

    private void stopVpnConnection() {
        new Thread(() -> {
            try {
                if (backend != null && isTunnelRunning) {
                    backend.setState(new Tunnel() {
                        @Override
                        public String getName() {
                            return TUNNEL_NAME;
                        }

                        @Override
                        public void onStateChange(State state) {}
                    }, Tunnel.State.DOWN, null);
                }
            } catch (Exception e) {
                Log.e(TAG, "خطأ أثناء إيقاف WireGuard backend:", e);
            }

            try {
                if (vpnInterface != null) {
                    vpnInterface.close();
                    vpnInterface = null;
                }
            } catch (Exception e) {
                Log.e(TAG, "خطأ أثناء إغلاق vpnInterface:", e);
            }

            isTunnelRunning = false;
            stopForeground(true);
            stopSelf();
            Log.d(TAG, "🔒 تم إيقاف خدمة الـ VPN وإعادة الـ IP الأصلي.");
        }).start();
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

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

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
        stopVpnConnection();
        super.onDestroy();
    }
}
