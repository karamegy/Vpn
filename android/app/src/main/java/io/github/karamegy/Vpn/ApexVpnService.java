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
import java.net.SocketTimeoutException;
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

    // إعدادات افتراضية لنفق التوجيه الشامل (Full Tunnel & WARP Node)
    private String currentServerIp = "162.159.192.1";
    private String currentServerName = "Cloudflare WARP Node";
    private String warpPrivateKey = "";
    private String warpClientIp = "10.0.0.2/32";
    private String warpEndpoint = "162.159.192.1:2408";

    private static final String PRIMARY_DNS = "1.1.1.1";
    private static final String SECONDARY_DNS = "1.0.0.1";
    private static final String ADGUARD_DNS = "94.140.14.14";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : "";

        if (intent != null) {
            currentServerName = intent.getStringExtra("serverName") != null ? intent.getStringExtra("serverName") : "Apex Shield Node";
            currentServerIp = intent.getStringExtra("serverIp") != null ? intent.getStringExtra("serverIp") : "162.159.192.1";
            warpPrivateKey = intent.getStringExtra("warpPrivateKey") != null ? intent.getStringExtra("warpPrivateKey") : "";
            warpClientIp = intent.getStringExtra("warpClientIp") != null ? intent.getStringExtra("warpClientIp") : "10.0.0.2/32";
            warpEndpoint = intent.getStringExtra("warpEndpoint") != null ? intent.getStringExtra("warpEndpoint") : currentServerIp + ":2408";
        }

        if (ACTION_CONNECT.equals(action) || "START".equals(action) || "START_VPN".equals(action)) {
            startForegroundNotification(currentServerName);
            startVpnConnection();
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

    private synchronized void startVpnConnection() {
        if (isRunning) return;
        isRunning = true;

        // زيادة حجم حوض الخيوط لمنع الاختناق مع حركة المرور العالية
        dnsThreadPool = Executors.newFixedThreadPool(32);

        vpnThread = new Thread(() -> {
            try {
                Builder builder = new Builder();
                
                // استخراج عنوان IP المحلي للنفق
                String assignedIp = "10.0.0.2";
                if (warpClientIp != null && warpClientIp.contains("/")) {
                    assignedIp = warpClientIp.split("/")[0];
                }

                builder.setSession("Apex Shield Full Tunnel")
                       .addAddress(assignedIp, 32)
                       .addDnsServer(PRIMARY_DNS)
                       .addDnsServer(SECONDARY_DNS)
                       .addDnsServer(ADGUARD_DNS)
                       // 🌐 توجيه كافة حركة المرور (0.0.0.0/0) لتغيير الـ IP الحقيقي
                       .addRoute("0.0.0.0", 0)
                       .setMtu(1420);

                // استثناء حزمة التطبيق لتفادي الحلقة التكرارية لربط الاتصالات
                try {
                    builder.addDisallowedApplication(getPackageName());
                } catch (Exception e) {
                    Log.w(TAG, "تعذر استثناء التطبيق من النفق: ", e);
                }

                vpnInterface = builder.establish();
                if (vpnInterface == null) {
                    stopVpnConnection();
                    return;
                }

                Log.i(TAG, "✅ تم إطلاق نفق التوجيه الشامل وتغيير الـ IP عبر: " + currentServerName + " (" + warpEndpoint + ")");
                runPacketLoop();

            } catch (Exception ex) {
                Log.e(TAG, "❌ فشل إنشاء واجهة الـ VPN:", ex);
                stopVpnConnection();
            }
        });
        vpnThread.start();
    }

    private void runPacketLoop() {
        try (FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
             FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor())) {

            byte[] packetBuffer = new byte[32767];
            InetAddress primaryDns = InetAddress.getByName(PRIMARY_DNS);
            InetAddress fallbackDns = InetAddress.getByName(ADGUARD_DNS);

            while (isRunning && !Thread.currentThread().isInterrupted()) {
                int length = in.read(packetBuffer);
                if (length > 28) {
                    int ipVersion = (packetBuffer[0] >> 4) & 0x0F;
                    if (ipVersion != 4) continue;

                    int ipHeaderLen = (packetBuffer[0] & 0x0F) * 4;
                    int protocol = packetBuffer[9] & 0xFF;

                    // معالجة وحقن طلبات UDP والـ DNS
                    if (protocol == 17 && length >= ipHeaderLen + 8) {
                        int dstPort = ((packetBuffer[ipHeaderLen + 2] & 0xFF) << 8) | (packetBuffer[ipHeaderLen + 3] & 0xFF);

                        if (dstPort == 53) {
                            int dnsPayloadOffset = ipHeaderLen + 8;
                            int dnsPayloadLen = length - dnsPayloadOffset;

                            if (dnsPayloadLen > 0) {
                                byte[] requestCopy = new byte[length];
                                System.arraycopy(packetBuffer, 0, requestCopy, 0, length);

                                dnsThreadPool.execute(() -> {
                                    try (DatagramSocket taskSocket = new DatagramSocket()) {
                                        protect(taskSocket); // استثناء السوكت من التوجيه التكراري
                                        taskSocket.setSoTimeout(2000);

                                        DatagramPacket outPacket = new DatagramPacket(
                                            requestCopy, dnsPayloadOffset, dnsPayloadLen, primaryDns, 53
                                        );

                                        byte[] respBuffer = new byte[4096];
                                        DatagramPacket inPacket = new DatagramPacket(respBuffer, respBuffer.length);

                                        try {
                                            taskSocket.send(outPacket);
                                            taskSocket.receive(inPacket);
                                        } catch (SocketTimeoutException ste) {
                                            outPacket.setAddress(fallbackDns);
                                            taskSocket.send(outPacket);
                                            taskSocket.receive(inPacket);
                                        }

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
                }
            }
        } catch (Exception e) {
            if (isRunning) {
                Log.e(TAG, "إغلاق حلقة معالجة الحزم:", e);
            }
        } finally {
            if (dnsThreadPool != null && !dnsThreadPool.isShutdown()) {
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
            System.arraycopy(requestPacket, 16, ipPacket, 12, 4); 
            System.arraycopy(requestPacket, 12, ipPacket, 16, 4); 

            ipPacket[2] = (byte) (totalLen >> 8);
            ipPacket[3] = (byte) (totalLen & 0xFF);

            calculateIpChecksum(ipPacket, ipHeaderLen);

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

            return ipPacket;
        } catch (Exception e) {
            return null;
        }
    }

    private void calculateIpChecksum(byte[] buf, int ipHeaderLen) {
        buf[10] = 0;
        buf[11] = 0;
        long sum = 0;
        for (int i = 0; i < ipHeaderLen; i += 2) {
            int word = ((buf[i] & 0xFF) << 8) | (buf[i + 1] & 0xFF);
            sum += word;
        }
        while ((sum >> 16) > 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        int checksum = (int) (~sum & 0xFFFF);
        buf[10] = (byte) (checksum >> 8);
        buf[11] = (byte) (checksum & 0xFF);
    }

    private synchronized void stopVpnConnection() {
        if (!isRunning && vpnInterface == null) return;
        isRunning = false;

        if (vpnThread != null) {
            vpnThread.interrupt();
        }

        if (dnsThreadPool != null && !dnsThreadPool.isShutdown()) {
            dnsThreadPool.shutdownNow();
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
