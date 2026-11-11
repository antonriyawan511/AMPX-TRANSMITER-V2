package com.example.amplifierpro.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MetadataSender {
    private DatagramSocket socket;
    private ExecutorService senderExecutor;

    public synchronized void open() {
        try {
            if (socket == null || socket.isClosed()) {
                socket = new DatagramSocket();
                try {
                    socket.setTrafficClass(0xB8);
                } catch (Exception ignored) {}
            }
            if (senderExecutor == null || senderExecutor.isShutdown()) {
                senderExecutor = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "MetadataSenderWorker");
                    t.setDaemon(true);
                    return t;
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMetadata(String ip, int port, String metadata) {
        final DatagramSocket sock = this.socket;
        final ExecutorService exec = this.senderExecutor;
        if (ip == null || metadata == null || sock == null || sock.isClosed() || exec == null || exec.isShutdown()) return;
        try {
            exec.execute(() -> {
                try {
                    byte[] data = metadata.getBytes("UTF-8");
                    InetAddress address = InetAddress.getByName(ip);
                    DatagramPacket packet = new DatagramPacket(data, data.length, address, port);

                    sock.send(packet);
                    Thread.sleep(30);
                    if (!sock.isClosed()) {
                        sock.send(packet);
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    public synchronized void close() {
        if (senderExecutor != null) {
            senderExecutor.shutdownNow();
            senderExecutor = null;
        }
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }
}
