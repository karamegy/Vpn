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
    private Thread vpnThread = null;

    private static final String ADGUARD_DNS = "94.140.14.14";

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
                       .addDnsServer(ADGUARD_DNS);

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

                Log.i(TAG, "✅ تم تشغيل النفق بنجاح عبر: " + serverName);
                runPacketLoop();

            } catch (Exception ex) {
                Log.e(TAG, "❌ فشل إنشاء واجهة الـ VPN:", ex);
                stopVpnConnection();
            }
        });
        vpnThread.start();
    }

    private void runPacketLoop() {
        DatagramSocket dnsSocket = null;
        try (FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
             FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor())) {

            dnsSocket = new DatagramSocket();
            protect(dnsSocket);
            dnsSocket.setSoTimeout(2500);

            byte[] packetBuffer = new byte[32767];
            InetAddress targetDns = InetAddress.getByName(ADGUARD_DNS);

            while (isRunning && !Thread.currentThread().isInterrupted()) {
                int length = in.read(packetBuffer);
                if (length > 28) { // التحقق من وجود IP + UDP headers
                    try {
                        int ipHeaderLen = (packetBuffer[0] & 0x0F) * 4;
                        int udpHeaderLen = 8;
                        int dnsPayloadOffset = ipHeaderLen + udpHeaderLen;
                        int dnsPayloadLen = length - dnsPayloadOffset;

                        // التحقق من أن الحزمة هي بروتوكول UDP (رقم 17)
                        if (dnsPayloadLen > 0 && packetBuffer[9] == 17) {
                            DatagramPacket outPacket = new DatagramPacket(
                                packetBuffer, dnsPayloadOffset, dnsPayloadLen, targetDns, 53
                            );
                            dnsSocket.send(outPacket);

                            byte[] respBuffer = new byte[32767];
                            DatagramPacket inPacket = new DatagramPacket(respBuffer, respBuffer.length);
                            dnsSocket.receive(inPacket);

                            // إعادة بناء ترويسة IP + UDP مع الرد وإرسالها إلى الـ TUN
                            byte[] responseIpPacket = buildIpUdpResponse(packetBuffer, inPacket.getData(), inPacket.getLength());
                            if (responseIpPacket != null) {
                                out.write(responseIpPacket);
                            }
                        }
                    } catch (Exception reqErr) {
                        // يتجاوز الحزم غير المتوافقة دون إيقاف الخدمة
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

    private byte[] buildIpUdpResponse(byte[] requestPacket, byte[] dnsResponsePayload, int dnsResponseLen) {
        try {
            int ipHeaderLen = (requestPacket[0] & 0x0F) * 4;
            int totalLen = ipHeaderLen + 8 + dnsResponseLen;
            byte[] ipPacket = new byte[totalLen];

            // نسخ ترويسة IP وتبديل عناوين المصدر والوجهة
            System.arraycopy(requestPacket, 0, ipPacket, 0, ipHeaderLen);
            System.arraycopy(requestPacket, 16, ipPacket, 12, 4); // Source IP -> Dest IP
            System.arraycopy(requestPacket, 12, ipPacket, 16, 4); // Dest IP -> Source IP

            // تحديث طول الحزمة الكلي
            ipPacket[2] = (byte) (totalLen >> 8);
            ipPacket[3] = (byte) (totalLen & 0xFF);

            // ترويسة UDP: تبديل المنافذ
            int srcPort = ((requestPacket[ipHeaderLen] & 0xFF) << 8) | (requestPacket[ipHeaderLen + 1] & 0xFF);
            int dstPort = ((requestPacket[ipHeaderLen + 2] & 0xFF) << 8) | (requestPacket[ipHeaderLen + 3] & 0xFF);

            ipPacket[ipHeaderLen] = (byte) (dstPort >> 8);
            ipPacket[ipHeaderLen + 1] = (byte) (dstPort & 0xFF);
            ipPacket[ipHeaderLen + 2] = (byte) (srcPort >> 8);
            ipPacket[ipHeaderLen + 3] = (byte) (srcPort & 0xFF);

            int udpLen = 8 + dnsResponseLen;
            ipPacket[ipHeaderLen + 4] = (byte) (udpLen >> 8);
            ipPacket[ipHeaderLen + 5] = (byte) (udpLen & 0xFF);
            ipPacket[ipHeaderLen + 6] = 0; // Checksum
            ipPacket[ipHeaderLen + 7] = 0;

            // دمج حمولة الـ DNS
            System.arraycopy(dnsResponsePayload, 0, ipPacket, ipHeaderLen + 8, dnsResponseLen);

            return ipPacket;
        } catch (Exception e) {
            return null;
        }
    }

    private synchronized void stopVpnConnection() {
        if (!isRunning && vpnInterface == null) return;
        isRunning = false;

        if (vpnThread != null) {
            vpnThread.interrupt();
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
