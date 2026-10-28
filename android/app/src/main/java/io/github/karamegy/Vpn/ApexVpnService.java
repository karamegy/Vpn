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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApexVpnService extends VpnService {
    private static final String TAG = "ApexVpnService";
    private static final String CHANNEL_ID = "ApexVpnChannel";
    private static final int NOTIFICATION_ID = 101;

    public static final String ACTION_CONNECT = "io.github.karamegy.Vpn.START_VPN";
    public static final String ACTION_DISCONNECT = "io.github.karamegy.Vpn.STOP_VPN";

    private ParcelFileDescriptor vpnInterface = null;
    private volatile boolean isRunning = false;
    private Thread vpnThread = null;
    private ExecutorService dnsThreadPool;

    private static final String DEFAULT_DNS = "94.140.14.14";
    private String activeDnsIp = DEFAULT_DNS;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : "";
        String serverName = intent != null ? intent.getStringExtra("serverName") : "Apex Shield Node";
        String serverIp = intent != null ? intent.getStringExtra("serverIp") : DEFAULT_DNS;

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
        this.activeDnsIp = (serverIp != null && !serverIp.trim().isEmpty()) ? serverIp : DEFAULT_DNS;
        dnsThreadPool = Executors.newFixedThreadPool(8);

        vpnThread = new Thread(() -> {
            try {
                Builder builder = new Builder();
                builder.setSession("Apex Shield Protection")
                       .addAddress("10.0.0.2", 32)
                       .addDnsServer(activeDnsIp)
                       .addRoute(activeDnsIp, 32)
                       .setMtu(1500);

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

                Log.i(TAG, "✅ تم تشغيل النفق بنجاح عبر: " + serverName + " (DNS: " + activeDnsIp + ")");
                runDnsLoop();

            } catch (Exception ex) {
                Log.e(TAG, "❌ فشل إنشاء واجهة الـ VPN:", ex);
                stopVpnConnection();
            }
        });
        vpnThread.start();
    }

    private void runDnsLoop() {
        try (FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
             FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor())) {

            byte[] packetBuffer = new byte[32767];
            InetAddress targetDns = InetAddress.getByName(activeDnsIp);

            while (isRunning && !Thread.currentThread().isInterrupted()) {
                int length = in.read(packetBuffer);
                if (length > 28) {
                    int ipHeaderLen = (packetBuffer[0] & 0x0F) * 4;
                    int udpHeaderLen = 8;
                    int dnsPayloadOffset = ipHeaderLen + udpHeaderLen;
                    int dnsPayloadLen = length - dnsPayloadOffset;

                    // التحقق من بروتوكول UDP (17)
                    if (dnsPayloadLen > 0 && packetBuffer[9] == 17) {
                        byte[] requestCopy = new byte[length];
                        System.arraycopy(packetBuffer, 0, requestCopy, 0, length);

                        dnsThreadPool.execute(() -> {
                            try (DatagramSocket querySocket = new DatagramSocket()) {
                                protect(querySocket);
                                querySocket.setSoTimeout(3000);

                                DatagramPacket outPacket = new DatagramPacket(
                                    requestCopy, dnsPayloadOffset, dnsPayloadLen, targetDns, 53
                                );
                                querySocket.send(outPacket);

                                byte[] respBuffer = new byte[4096];
                                DatagramPacket inPacket = new DatagramPacket(respBuffer, respBuffer.length);
                                querySocket.receive(inPacket);

                                byte[] responseIpPacket = buildIpUdpResponse(requestCopy, inPacket.getData(), inPacket.getLength());
                                if (responseIpPacket != null) {
                                    synchronized (out) {
                                        out.write(responseIpPacket);
                                    }
                                }
                            } catch (Exception ignored) {}
                        });
                    }
                }
            }
        } catch (Exception e) {
            if (isRunning) {
                Log.e(TAG, "إغلاق حلقة معالجة الحزم:", e);
            }
        } finally {
            if (dnsThreadPool != null) {
                dnsThreadPool.shutdownNow();
            }
        }
    }

    private byte[] buildIpUdpResponse(byte[] requestPacket, byte[] dnsResponsePayload, int dnsResponseLen) {
        try {
            int ipHeaderLen = (requestPacket[0] & 0x0F) * 4;
            int totalLen = ipHeaderLen + 8 + dnsResponseLen;
            byte[] ipPacket = new byte[totalLen];

            System.arraycopy(requestPacket, 0, ipPacket, 0, ipHeaderLen);
            System.arraycopy(requestPacket, 16, ipPacket, 12, 4); // Source IP -> Dest IP
            System.arraycopy(requestPacket, 12, ipPacket, 16, 4); // Dest IP -> Source IP

            ipPacket[2] = (byte) (totalLen >> 8);
            ipPacket[3] = (byte) (totalLen & 0xFF);

            int srcPort = ((requestPacket[ipHeaderLen] & 0xFF) << 8) | (requestPacket[ipHeaderLen + 1] & 0xFF);
            int dstPort = ((requestPacket[ipHeaderLen + 2] & 0xFF) << 8) | (requestPacket[ipHeaderLen + 3] & 0xFF);

            ipPacket[ipHeaderLen] = (byte) (dstPort >> 8);
            ipPacket[ipHeaderLen + 1] = (byte) (dstPort & 0xFF);
            ipPacket[ipHeaderLen + 2] = (byte) (srcPort >> 8);
            ipPacket[ipHeaderLen + 3] = (byte) (srcPort & 0xFF);

            int udpLen = 8 + dnsResponseLen;
            ipPacket[ipHeaderLen + 4] = (byte) (udpLen >> 8);
            ipPacket[ipHeaderLen + 5] = (byte) (udpLen & 0xFF);
            ipPacket[ipHeaderLen + 6] = 0;
            ipPacket[ipHeaderLen + 7] = 0;

            System.arraycopy(dnsResponsePayload, 0, ipPacket, ipHeaderLen + 8, dnsResponseLen);

            // حساب IPv4 Header Checksum
            ipPacket[10] = 0;
            ipPacket[11] = 0;
            int sum = 0;
            for (int i = 0; i < ipHeaderLen; i += 2) {
                int word = ((ipPacket[i] & 0xFF) << 8) | (ipPacket[i + 1] & 0xFF);
                sum += word;
            }
            while ((sum >> 16) > 0) {
                sum = (sum & 0xFFFF) + (sum >> 16);
            }
            int checksum = ~sum & 0xFFFF;
            ipPacket[10] = (byte) (checksum >> 8);
            ipPacket[11] = (byte) (checksum & 0xFF);

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
