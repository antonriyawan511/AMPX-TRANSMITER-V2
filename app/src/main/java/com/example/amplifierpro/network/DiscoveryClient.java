package com.example.amplifierpro.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

public class DiscoveryClient {
    private final int port;

    public static class DiscoveryResult {
        public final String ip;
        public final String name;

        public DiscoveryResult(String ip, String name) {
            this.ip = ip;
            this.name = name;
        }
    }

    public DiscoveryClient(int port) {
        this.port = port;
    }

    private static Set<String> getLocalIpAddresses() {
        Set<String> ips = new HashSet<>();
        ips.add("127.0.0.1");
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    String host = addr.getHostAddress();
                    if (host != null) {
                        ips.add(host);
                    }
                }
            }
        } catch (Exception ignored) {}
        return ips;
    }

    public DiscoveryResult performDiscoveryWithResult() {
        Set<String> localIps = getLocalIpAddresses();
        try (DatagramSocket ds = new DatagramSocket()) {
            ds.setBroadcast(true);
            byte[] msg = "AMPLIFIER_PRO_DISCOVER".getBytes(StandardCharsets.UTF_8);

            // 1. Broadcast global
            try {
                ds.send(new DatagramPacket(msg, msg.length, InetAddress.getByName("255.255.255.255"), port));
            } catch (Exception ignored) {}

            // 2. Broadcast subnet spesifik (e.g. 192.168.8.255) agar lolos filter router
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces != null && interfaces.hasMoreElements()) {
                    NetworkInterface iface = interfaces.nextElement();
                    if (iface.isLoopback() || !iface.isUp()) continue;
                    for (InterfaceAddress ifaceAddr : iface.getInterfaceAddresses()) {
                        InetAddress broadcast = ifaceAddr.getBroadcast();
                        if (broadcast == null && ifaceAddr.getAddress() instanceof Inet4Address) {
                            try {
                                byte[] ip = ifaceAddr.getAddress().getAddress();
                                int prefix = ifaceAddr.getNetworkPrefixLength();
                                if (prefix > 0 && prefix <= 30) {
                                    int mask = 0xFFFFFFFF << (32 - prefix);
                                    int ipInt = ((ip[0] & 0xFF) << 24) | ((ip[1] & 0xFF) << 16) | ((ip[2] & 0xFF) << 8) | (ip[3] & 0xFF);
                                    int bcastInt = ipInt | ~mask;
                                    byte[] bcastBytes = new byte[]{
                                            (byte) ((bcastInt >> 24) & 0xFF),
                                            (byte) ((bcastInt >> 16) & 0xFF),
                                            (byte) ((bcastInt >> 8) & 0xFF),
                                            (byte) (bcastInt & 0xFF)
                                    };
                                    broadcast = InetAddress.getByAddress(bcastBytes);
                                }
                            } catch (Exception ignored) {}
                        }
                        if (broadcast != null) {
                            try {
                                ds.send(new DatagramPacket(msg, msg.length, broadcast, port));
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}

            // 3. Fast Unicast Subnet Sweep (Wajib untuk Hotspot Android / Router dengan Isolasi Broadcast)
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces != null && interfaces.hasMoreElements()) {
                    NetworkInterface iface = interfaces.nextElement();
                    if (iface.isLoopback() || !iface.isUp()) continue;
                    String ifaceName = iface.getName().toLowerCase();
                    // Lewati interface seluler murni (ccmni, rmnet, pdp, dummy) agar tidak memindai IP publik
                    if (ifaceName.startsWith("ccmni") || ifaceName.startsWith("rmnet") || ifaceName.startsWith("pdp") || ifaceName.startsWith("dummy")) {
                        continue;
                    }

                    for (InterfaceAddress ifaceAddr : iface.getInterfaceAddresses()) {
                        if (!(ifaceAddr.getAddress() instanceof Inet4Address)) continue;
                        byte[] ipBytes = ifaceAddr.getAddress().getAddress();
                        int prefix = ifaceAddr.getNetworkPrefixLength();

                        // Fallback prefix jika tidak terbaca di beberapa sistem
                        if (prefix <= 0 || prefix > 30) {
                            if (ifaceName.contains("ap") || ifaceName.contains("wlan") ||
                                    ((ipBytes[0] & 0xFF) == 192 && (ipBytes[1] & 0xFF) == 168) ||
                                    ((ipBytes[0] & 0xFF) == 10)) {
                                prefix = 24;
                            }
                        }

                        // Hanya sweep jika ukuran subnet wajar (/24 sampai /30, maksimal 254 host)
                        if (prefix >= 24 && prefix <= 30) {
                            int mask = 0xFFFFFFFF << (32 - prefix);
                            int ipInt = ((ipBytes[0] & 0xFF) << 24) | ((ipBytes[1] & 0xFF) << 16) | ((ipBytes[2] & 0xFF) << 8) | (ipBytes[3] & 0xFF);
                            int network = ipInt & mask;
                            int broadcastInt = ipInt | ~mask;

                            for (int hostInt = network + 1; hostInt < broadcastInt; hostInt++) {
                                if (hostInt == ipInt) continue; // Jangan kirim ke IP HP sendiri
                                byte[] targetBytes = new byte[]{
                                        (byte) ((hostInt >> 24) & 0xFF),
                                        (byte) ((hostInt >> 16) & 0xFF),
                                        (byte) ((hostInt >> 8) & 0xFF),
                                        (byte) (hostInt & 0xFF)
                                };
                                try {
                                    InetAddress targetAddr = InetAddress.getByAddress(targetBytes);
                                    ds.send(new DatagramPacket(msg, msg.length, targetAddr, port));
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}

            long deadline = System.currentTimeMillis() + 2500;
            byte[] buf = new byte[1024];

            while (System.currentTimeMillis() < deadline) {
                int remainingTimeout = (int) Math.max(100, deadline - System.currentTimeMillis());
                ds.setSoTimeout(remainingTimeout);
                try {
                    DatagramPacket reply = new DatagramPacket(buf, buf.length);
                    ds.receive(reply);

                    String ip = reply.getAddress().getHostAddress();
                    // Abaikan jika respon berasal dari HP ini sendiri (misal Receiver juga aktif di HP yang sama)
                    if (ip == null || localIps.contains(ip)) {
                        continue;
                    }

                    String replyStr = new String(reply.getData(), 0, reply.getLength(), StandardCharsets.UTF_8).trim();
                    if (replyStr.startsWith("AMPLIFIER_PRO_RESPONSE")) {
                        String name = null;
                        if (replyStr.startsWith("AMPLIFIER_PRO_RESPONSE:")) {
                            String candidate = replyStr.substring("AMPLIFIER_PRO_RESPONSE:".length()).trim();
                            if (!candidate.isEmpty() && !candidate.equalsIgnoreCase("Receiver")) {
                                name = candidate;
                            }
                        }
                        return new DiscoveryResult(ip, name);
                    }
                } catch (SocketTimeoutException e) {
                    break;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Query nama perangkat secara langsung (unicast, satu kali jalan, bukan periodik).
     */
    public String queryDeviceName(String targetIp) {
        if (targetIp == null || targetIp.trim().isEmpty()) return null;
        try (DatagramSocket ds = new DatagramSocket()) {
            ds.setSoTimeout(600);
            byte[] msg = "AMPLIFIER_PRO_DISCOVER".getBytes(StandardCharsets.UTF_8);
            ds.send(new DatagramPacket(msg, msg.length, InetAddress.getByName(targetIp.trim()), port));
            byte[] buf = new byte[1024];
            DatagramPacket reply = new DatagramPacket(buf, buf.length);
            ds.receive(reply);
            String replyStr = new String(reply.getData(), 0, reply.getLength(), StandardCharsets.UTF_8).trim();
            if (replyStr.startsWith("AMPLIFIER_PRO_RESPONSE:")) {
                String candidate = replyStr.substring("AMPLIFIER_PRO_RESPONSE:".length()).trim();
                if (!candidate.isEmpty() && !candidate.equalsIgnoreCase("Receiver")) {
                    return candidate;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public String performDiscovery() {
        DiscoveryResult res = performDiscoveryWithResult();
        return res != null ? res.ip : null;
    }
}
