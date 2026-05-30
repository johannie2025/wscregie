package com.wisesmartchurch.system.server;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import com.wisesmartchurch.system.utils.NetworkUtils;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

/** Service qui annonce la régie sur le réseau local (UDP broadcast) */
public class WscUdpAnnounceService extends Service {
    private static final String TAG      = "WscUdpAnnounce";
    private static final String ANNOUNCE = "WSC_ANNOUNCE:";
    private static final String DISCOVER = "WSC_DISCOVER";
    private static final int    UDP_PORT = 9002;
    private static final int    WS_PORT  = 9000;
    private volatile boolean running    = true;
    private DatagramSocket socket;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(this::loop, "WscUdpAnnounce").start();
        return START_STICKY;
    }

    private void loop() {
        try {
            socket = new DatagramSocket(UDP_PORT);
            socket.setBroadcast(true);
            socket.setSoTimeout(2000);
            while (running) {
                String localIp = NetworkUtils.getLocalIp(this);
                String msg = ANNOUNCE + localIp + ":" + WS_PORT;
                broadcast(msg);
                long deadline = System.currentTimeMillis() + 8000;
                while (running && System.currentTimeMillis() < deadline) {
                    try {
                        byte[] buf = new byte[256];
                        DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                        socket.receive(pkt);
                        String rx = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8).trim();
                        if (DISCOVER.equals(rx)) {
                            byte[] reply = msg.getBytes(StandardCharsets.UTF_8);
                            socket.send(new DatagramPacket(reply, reply.length, pkt.getAddress(), UDP_PORT));
                            Log.d(TAG, "DISCOVER → ANNOUNCE to " + pkt.getAddress());
                        }
                    } catch (SocketTimeoutException ignored) {}
                }
            }
        } catch (Exception e) {
            if (running) Log.e(TAG, "UDP loop error", e);
        } finally {
            if (socket != null && !socket.isClosed()) socket.close();
        }
    }

    private void broadcast(String msg) {
        new Thread(() -> {
            try (DatagramSocket ds = new DatagramSocket()) {
                ds.setBroadcast(true);
                byte[] data = msg.getBytes(StandardCharsets.UTF_8);
                send(ds, data, "255.255.255.255");
                for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en!=null&&en.hasMoreElements();) {
                    for (InterfaceAddress ia : en.nextElement().getInterfaceAddresses()) {
                        InetAddress bcast = ia.getBroadcast();
                        if (bcast != null) send(ds, data, bcast.getHostAddress());
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void send(DatagramSocket ds, byte[] data, String addr) {
        try { ds.send(new DatagramPacket(data, data.length, InetAddress.getByName(addr), UDP_PORT)); } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() { running = false; if (socket != null) socket.close(); }
}
