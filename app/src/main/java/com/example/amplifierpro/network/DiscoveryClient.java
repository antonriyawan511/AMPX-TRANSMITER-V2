package com.example.amplifierpro.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class DiscoveryClient {
    private final int port;

    public DiscoveryClient(int port) {
        this.port = port;
    }

    public String performDiscovery() {
        try (DatagramSocket ds = new DatagramSocket()) {
            ds.setBroadcast(true);
            ds.setSoTimeout(2000);
            byte[] msg = "AMPLIFIER_PRO_DISCOVER".getBytes();
            DatagramPacket packet = new DatagramPacket(msg, msg.length, InetAddress.getByName("255.255.255.255"), port);
            ds.send(packet);

            byte[] buf = new byte[1024];
            DatagramPacket reply = new DatagramPacket(buf, buf.length);
            ds.receive(reply);
            return reply.getAddress().getHostAddress();
        } catch (Exception e) {
            return null;
        }
    }
}
