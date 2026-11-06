package com.example.amplifierpro.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class MetadataSender {
    private DatagramSocket socket;

    public void open() {
        try {
            if (socket == null || socket.isClosed()) {
                socket = new DatagramSocket();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMetadata(String ip, int port, String metadata) {
        if (ip == null || metadata == null || socket == null || socket.isClosed()) return;
        new Thread(() -> {
            try {
                byte[] data = metadata.getBytes("UTF-8");
                InetAddress address = InetAddress.getByName(ip);
                DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
                
                // Send twice (burst) for reliability on UDP
                socket.send(packet);
                Thread.sleep(30);
                if (socket != null && !socket.isClosed()) {
                    socket.send(packet);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void close() {
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }
}
