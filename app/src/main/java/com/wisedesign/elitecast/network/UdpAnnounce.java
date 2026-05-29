package com.wisedesign.elitecast.network;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

/**
 * Annonce UDP périodique : la régie se rend visible sur le réseau.
 * Compatible avec WscUdpDiscovery du receiver APK TV Box.
 *
 * Protocole :
 *   Régie → broadcast UDP 9002 : "WSC_ANNOUNCE:<ip>:9000" toutes les 8s
 *   TV Box → WSC_DISCOVER      : on répond immédiatement
 */
public class UdpAnnounce extends Thread {

    private static final String TAG      = "UdpAnnounce";
    private static final String ANNOUNCE = "WSC_ANNOUNCE:";
    private static final String DISCOVER = "WSC_DISCOVER";
    private static final long   INTERVAL = 8000L;

    private final Context context;
    private final int     wsPort;
    private final int     udpPort;
    private DatagramSocket socket;
    private volatile boolean running = true;
    private WifiManager.MulticastLock multicastLock;

    public UdpAnnounce(Context ctx, int wsPort, int udpPort) {
        this.context = ctx.getApplicationContext();
        this.wsPort  = wsPort;
        this.udpPort = udpPort;
        setDaemon(true);
        setName("EliteUdpAnnounce");
    }

    @Override
    public void run() {
        acquireMulticastLock();
        try {
            socket = new DatagramSocket(udpPort);
            socket.setBroadcast(true);
            socket.setSoTimeout(2000);

            while (running) {
                String ip  = getLocalIp();
                String msg = ANNOUNCE + ip + ":" + wsPort;
                broadcast(msg);

                long deadline = System.currentTimeMillis() + INTERVAL;
                while (running && System.currentTimeMillis() < deadline) {
                    try {
                        byte[] buf = new byte[256];
                        DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                        socket.receive(pkt);
                        String received = new String(pkt.getData(), 0, pkt.getLength(),
                                StandardCharsets.UTF_8).trim();
                        if (DISCOVER.equals(received)) {
                            Log.d(TAG, "DISCOVER reçu de " + pkt.getAddress().getHostAddress());
                            byte[] reply = msg.getBytes(StandardCharsets.UTF_8);
                            socket.send(new DatagramPacket(reply, reply.length,
                                    pkt.getAddress(), udpPort));
                        }
                    } catch (SocketTimeoutException ignored) {}
                }
            }
        } catch (Exception e) {
            if (running) Log.e(TAG, "UDP Announce error", e);
        } finally {
            if (socket != null && !socket.isClosed()) socket.close();
            releaseMulticastLock();
        }
    }

    private void broadcast(String msg) {
        new Thread(() -> {
            try (DatagramSocket ds = new DatagramSocket()) {
                ds.setBroadcast(true);
                byte[] data = msg.getBytes(StandardCharsets.UTF_8);
                send(ds, data, "255.255.255.255");
                for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                     en != null && en.hasMoreElements(); ) {
                    for (InterfaceAddress ia : en.nextElement().getInterfaceAddresses()) {
                        InetAddress bc = ia.getBroadcast();
                        if (bc != null) send(ds, data, bc.getHostAddress());
                    }
                }
                Log.d(TAG, "📡 Annonce: " + msg);
            } catch (Exception e) { Log.w(TAG, "broadcast: " + e.getMessage()); }
        }, "UdpBcast").start();
    }

    private void send(DatagramSocket ds, byte[] data, String addr) {
        try { ds.send(new DatagramPacket(data, data.length, InetAddress.getByName(addr), udpPort)); }
        catch (Exception ignored) {}
    }

    public String getLocalIp() {
        try {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                int ip = wm.getConnectionInfo().getIpAddress();
                if (ip != 0) return (ip & 0xff) + "." + ((ip >> 8) & 0xff)
                        + "." + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff);
            }
        } catch (Exception ignored) {}
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                 en != null && en.hasMoreElements(); ) {
                for (Enumeration<InetAddress> addrs = en.nextElement().getInetAddresses();
                     addrs.hasMoreElements(); ) {
                    InetAddress a = addrs.nextElement();
                    if (!a.isLoopbackAddress() && a instanceof Inet4Address)
                        return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    private void acquireMulticastLock() {
        try {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                multicastLock = wm.createMulticastLock("Elitecast");
                multicastLock.setReferenceCounted(true);
                multicastLock.acquire();
            }
        } catch (Exception ignored) {}
    }

    private void releaseMulticastLock() {
        try { if (multicastLock != null && multicastLock.isHeld()) multicastLock.release(); }
        catch (Exception ignored) {}
    }

    public void stopAnnounce() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
    }
}
