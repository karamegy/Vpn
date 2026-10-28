package io.github.karamegy.Vpn;[span_1](start_span)[span_1](end_span)

import android.app.Notification;[span_2](start_span)[span_2](end_span)
import android.app.NotificationChannel;[span_3](start_span)[span_3](end_span)
import android.app.NotificationManager;[span_4](start_span)[span_4](end_span)
import android.app.PendingIntent;[span_5](start_span)[span_5](end_span)
import android.content.Intent;[span_6](start_span)[span_6](end_span)
import android.content.pm.ServiceInfo;[span_7](start_span)[span_7](end_span)
import android.net.VpnService;[span_8](start_span)[span_8](end_span)
import android.os.Build;[span_9](start_span)[span_9](end_span)
import android.os.ParcelFileDescriptor;[span_10](start_span)[span_10](end_span)
import android.util.Base64;[span_11](start_span)[span_11](end_span)
import android.util.Log;[span_12](start_span)[span_12](end_span)

import com.wireguard.android.backend.GoBackend;[span_13](start_span)[span_13](end_span)
import com.wireguard.android.backend.Tunnel;[span_14](start_span)[span_14](end_span)
import com.wireguard.config.Config;[span_15](start_span)[span_15](end_span)

import java.io.ByteArrayInputStream;[span_16](start_span)[span_16](end_span)
import java.nio.charset.StandardCharsets;[span_17](start_span)[span_17](end_span)
import java.security.SecureRandom;[span_18](start_span)[span_18](end_span)

public class ApexVpnService extends VpnService {[span_19](start_span)[span_19](end_span)
    private static final String TAG = "ApexVpnService";[span_20](start_span)[span_20](end_span)
    private static final String CHANNEL_ID = "ApexVpnChannel";[span_21](start_span)[span_21](end_span)
    private static final int NOTIFICATION_ID = 101;[span_22](start_span)[span_22](end_span)
    private static final String TUNNEL_NAME = "ApexWarpTunnel";[span_23](start_span)[span_23](end_span)

    public static final String ACTION_CONNECT = "io.github.karamegy.Vpn.START";[span_24](start_span)[span_24](end_span)
    public static final String ACTION_DISCONNECT = "io.github.karamegy.Vpn.STOP";[span_25](start_span)[span_25](end_span)

    private GoBackend backend;[span_26](start_span)[span_26](end_span)
    private boolean isTunnelRunning = false;[span_27](start_span)[span_27](end_span)
    private ParcelFileDescriptor vpnInterface = null;[span_28](start_span)[span_28](end_span)

    @Override
    public void onCreate() {[span_29](start_span)[span_29](end_span)
        super.onCreate();[span_30](start_span)[span_30](end_span)
        try {[span_31](start_span)[span_31](end_span)
            backend = new GoBackend(getApplicationContext());[span_32](start_span)[span_32](end_span)
        } catch (Exception e) {[span_33](start_span)[span_33](end_span)
            Log.e(TAG, "فشل تهيئة محرك WireGuard GoBackend", e);[span_34](start_span)[span_34](end_span)
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {[span_35](start_span)[span_35](end_span)
        String action = intent != null ? intent.getAction() : "";[span_36](start_span)[span_36](end_span)

        if (ACTION_CONNECT.equals(action) || "START".equals(action)) {[span_37](start_span)[span_37](end_span)
            String serverName = intent != null ? intent.getStringExtra("serverName") : "Cloudflare WARP";[span_38](start_span)[span_38](end_span)
            String warpClientIp = intent != null ? intent.getStringExtra("warpClientIp") : "172.16.0.2/32";[span_39](start_span)[span_39](end_span)
            String warpEndpoint = intent != null ? intent.getStringExtra("warpEndpoint") : "162.159.192.1:2408";[span_40](start_span)[span_40](end_span)
            String warpPrivateKey = intent != null ? intent.getStringExtra("warpPrivateKey") : "";[span_41](start_span)[span_41](end_span)

            createNotificationChannel();[span_42](start_span)[span_42](end_span)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {[span_43](start_span)[span_43](end_span)
                startForeground(NOTIFICATION_ID, buildNotification(serverName), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);[span_44](start_span)[span_44](end_span)
            } else {[span_45](start_span)[span_45](end_span)
                startForeground(NOTIFICATION_ID, buildNotification(serverName));[span_46](start_span)[span_46](end_span)
            }

            startVpnConnection(warpClientIp, warpEndpoint, serverName, warpPrivateKey);[span_47](start_span)[span_47](end_span)
        } else if (ACTION_DISCONNECT.equals(action) || "STOP".equals(action)) {[span_48](start_span)[span_48](end_span)
            stopVpnConnection();[span_49](start_span)[span_49](end_span)
        }

        return START_STICKY;[span_50](start_span)[span_50](end_span)
    }

    private void startVpnConnection(String clientIpStr, String endpointStr, String serverName, String privateKeyBase64) {[span_51](start_span)[span_51](end_span)
        if (isTunnelRunning) return;[span_52](start_span)[span_52](end_span)

        new Thread(() -> {[span_53](start_span)[span_53](end_span)
            try {[span_54](start_span)[span_54](end_span)
                if (backend != null) {[span_55](start_span)[span_55](end_span)
                    String privateKey;[span_56](start_span)[span_56](end_span)
                    if (privateKeyBase64 != null && !privateKeyBase64.trim().isEmpty()) {[span_57](start_span)[span_57](end_span)
                        privateKey = privateKeyBase64.trim();[span_58](start_span)[span_58](end_span)
                    } else {[span_59](start_span)[span_59](end_span)
                        byte[] randomBytes = new byte[32];[span_60](start_span)[span_60](end_span)
                        new SecureRandom().nextBytes(randomBytes);[span_61](start_span)[span_61](end_span)
                        privateKey = Base64.encodeToString(randomBytes, Base64.NO_WRAP);[span_62](start_span)[span_62](end_span)
                    }

                    String formattedClientIp = (clientIpStr != null && !clientIpStr.isEmpty()) ? clientIpStr : "172.16.0.2/32";[span_63](start_span)[span_63](end_span)
                    if (!formattedClientIp.contains("/")) {[span_64](start_span)[span_64](end_span)
                        formattedClientIp += "/32";[span_65](start_span)[span_65](end_span)
                    }

                    String targetEndpoint = (endpointStr != null && !endpointStr.isEmpty()) ? endpointStr : "162.159.192.1:2408";[span_66](start_span)[span_66](end_span)

                    String wgConfigStr = "[Interface]\n" +[span_67](start_span)[span_67](end_span)
                            "PrivateKey = " + privateKey + "\n" +[span_68](start_span)[span_68](end_span)
                            "Address = " + formattedClientIp + ", fd00::2/128\n" +[span_69](start_span)[span_69](end_span)
                            "DNS = 1.1.1.1, 1.0.0.1, 2606:4700:4700::1111\n" +[span_70](start_span)[span_70](end_span)
                            "MTU = 1280\n\n" +[span_71](start_span)[span_71](end_span)
                            "[Peer]\n" +[span_72](start_span)[span_72](end_span)
                            "PublicKey = bmXOC+F1FxEMF9mTpdM6yWp166bNQA3JV4G2F2LAKkc=\n" +[span_73](start_span)[span_73](end_span)
                            "Endpoint = " + targetEndpoint + "\n" +[span_74](start_span)[span_74](end_span)
                            "AllowedIPs = 0.0.0.0/0, ::/0\n";[span_75](start_span)[span_75](end_span)

                    Config config = Config.parse(new ByteArrayInputStream(wgConfigStr.getBytes(StandardCharsets.UTF_8)));[span_76](start_span)[span_76](end_span)

                    backend.setState(new Tunnel() {[span_77](start_span)[span_77](end_span)
                        @Override
                        public String getName() {[span_78](start_span)[span_78](end_span)
                            return TUNNEL_NAME;[span_79](start_span)[span_79](end_span)
                        }

                        @Override
                        public void onStateChange(State state) {[span_80](start_span)[span_80](end_span)
                            Log.d(TAG, "حالة نفق WireGuard تغيرت إلى: " + state.name());[span_81](start_span)[span_81](end_span)
                        }
                    }, Tunnel.State.UP, config);[span_82](start_span)[span_82](end_span)

                    isTunnelRunning = true;[span_83](start_span)[span_83](end_span)
                    Log.d(TAG, "✅ تم تشغيل نفق WireGuard/WARP وتوجيه البيانات بنجاح!");[span_84](start_span)[span_84](end_span)
                    return;[span_85](start_span)[span_85](end_span)
                }
            } catch (Exception e) {[span_86](start_span)[span_86](end_span)
                Log.e(TAG, "فشل WireGuard GoBackend، جاري المحاولة بواسطة واجهة VpnService النظام:", e);[span_87](start_span)[span_87](end_span)
            }

            try {[span_88](start_span)[span_88](end_span)
                Builder builder = new Builder();[span_89](start_span)[span_89](end_span)
                builder.addAddress("172.16.0.2", 32);[span_90](start_span)[span_90](end_span)
                builder.addDnsServer("1.1.1.1");[span_91](start_span)[span_91](end_span)
                builder.addDnsServer("1.0.0.1");[span_92](start_span)[span_92](end_span)
                builder.addRoute("0.0.0.0", 0);[span_93](start_span)[span_93](end_span)
                builder.setSession("ApexShieldVPN");[span_94](start_span)[span_94](end_span)
                vpnInterface = builder.establish();[span_95](start_span)[span_95](end_span)
                isTunnelRunning = true;[span_96](start_span)[span_96](end_span)
                Log.d(TAG, "✅ تم تشغيل واجهة VPN النظام بنجاح!");[span_97](start_span)[span_97](end_span)
            } catch (Exception ex) {[span_98](start_span)[span_98](end_span)
                Log.e(TAG, "❌ فشل إنشاء واجهة VPN النظام:", ex);[span_99](start_span)[span_99](end_span)
                stopVpnConnection();[span_100](start_span)[span_100](end_span)
            }
        }).start();[span_101](start_span)[span_101](end_span)
    }

    private void stopVpnConnection() {[span_102](start_span)[span_102](end_span)
        new Thread(() -> {[span_103](start_span)[span_103](end_span)
            try {[span_104](start_span)[span_104](end_span)
                if (backend != null && isTunnelRunning) {[span_105](start_span)[span_105](end_span)
                    backend.setState(new Tunnel() {[span_106](start_span)[span_106](end_span)
                        @Override
                        public String getName() {[span_107](start_span)[span_107](end_span)
                            return TUNNEL_NAME;[span_108](start_span)[span_108](end_span)
                        }

                        @Override
                        public void onStateChange(State state) {}[span_109](start_span)[span_109](end_span)
                    }, Tunnel.State.DOWN, null);[span_110](start_span)[span_110](end_span)
                }
            } catch (Exception e) {[span_111](start_span)[span_111](end_span)
                Log.e(TAG, "خطأ أثناء إيقاف WireGuard backend:", e);[span_112](start_span)[span_112](end_span)
            }

            try {[span_113](start_span)[span_113](end_span)
                if (vpnInterface != null) {[span_114](start_span)[span_114](end_span)
                    vpnInterface.close();[span_115](start_span)[span_115](end_span)
                    vpnInterface = null;[span_116](start_span)[span_116](end_span)
                }
            } catch (Exception e) {[span_117](start_span)[span_117](end_span)
                Log.e(TAG, "خطأ أثناء إغلاق vpnInterface:", e);[span_118](start_span)[span_118](end_span)
            }

            isTunnelRunning = false;[span_119](start_span)[span_119](end_span)
            stopForeground(true);[span_120](start_span)[span_120](end_span)
            stopSelf();[span_121](start_span)[span_121](end_span)
            Log.d(TAG, "🔒 تم إيقاف خدمة الـ VPN وإعادة الـ IP الأصلي.");[span_122](start_span)[span_122](end_span)
        }).start();[span_123](start_span)[span_123](end_span)
    }

    private void createNotificationChannel() {[span_124](start_span)[span_124](end_span)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {[span_125](start_span)[span_125](end_span)
            NotificationChannel channel = new NotificationChannel([span_126](start_span)[span_126](end_span)
                    CHANNEL_ID,[span_127](start_span)[span_127](end_span)
                    "Apex Shield VPN Protection",[span_128](start_span)[span_128](end_span)
                    NotificationManager.IMPORTANCE_LOW[span_129](start_span)[span_129](end_span)
            );
            channel.setDescription("حالة الاتصال بالشبكة الافتراضية المشفرة");[span_130](start_span)[span_130](end_span)
            NotificationManager manager = getSystemService(NotificationManager.class);[span_131](start_span)[span_131](end_span)
            if (manager != null) {[span_132](start_span)[span_132](end_span)
                manager.createNotificationChannel(channel);[span_133](start_span)[span_133](end_span)
            }
        }
    }

    private Notification buildNotification(String serverName) {[span_134](start_span)[span_134](end_span)
        Intent notificationIntent = new Intent(this, MainActivity.class);[span_135](start_span)[span_135](end_span)
        PendingIntent pendingIntent = PendingIntent.getActivity([span_136](start_span)[span_136](end_span)
                this,[span_137](start_span)[span_137](end_span)
                0,[span_138](start_span)[span_138](end_span)
                notificationIntent,[span_139](start_span)[span_139](end_span)
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT[span_140](start_span)[span_140](end_span)
        );

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)[span_141](start_span)[span_141](end_span)
                ? new Notification.Builder(this, CHANNEL_ID)[span_142](start_span)[span_142](end_span)
                : new Notification.Builder(this);[span_143](start_span)[span_143](end_span)

        String contentText = (serverName != null && !serverName.isEmpty())[span_144](start_span)[span_144](end_span)
                ? "متصل بالدرع عبر: " + serverName[span_145](start_span)[span_145](end_span)
                : "درع الحماية وخدمة Cloudflare WARP المشفرة نشطة الآن";[span_146](start_span)[span_146](end_span)

        return builder[span_147](start_span)[span_147](end_span)
                .setContentTitle("Apex Shield AI")[span_148](start_span)[span_148](end_span)
                .setContentText(contentText)[span_149](start_span)[span_149](end_span)
                .setSmallIcon(android.R.drawable.ic_lock_lock)[span_150](start_span)[span_150](end_span)
                .setContentIntent(pendingIntent)[span_151](start_span)[span_151](end_span)
                .setOngoing(true)[span_152](start_span)[span_152](end_span)
                .build();[span_153](start_span)[span_153](end_span)
    }

    @Override
    public void onDestroy() {[span_154](start_span)[span_154](end_span)
        stopVpnConnection();[span_155](start_span)[span_155](end_span)
        super.onDestroy();[span_156](start_span)[span_156](end_span)
    }
}
