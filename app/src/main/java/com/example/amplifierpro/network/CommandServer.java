package com.example.amplifierpro.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

public class CommandServer {
    private final int port;
    private final CommandListener listener;
    private DatagramSocket socket;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Thread thread;

    public interface CommandListener {
        void onCommandReceived(String command);
        default void onCommandReceived(String command, String senderIp) {
            onCommandReceived(command);
        }
    }

    public CommandServer(int port, CommandListener listener) {
        this.port = port;
        this.listener = listener;
    }

    public void start() {
        if (isRunning.getAndSet(true)) return;
        thread = new Thread(() -> {
            try {
                socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(port));
                
                byte[] buffer = new byte[2048];
                while (isRunning.get()) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    
                    // Instant UDP Echo for RTT Ping (Byte 0 = 0x50)
                    if (packet.getLength() >= 9 && buffer[0] == 0x50) {
                        DatagramPacket pongPacket = new DatagramPacket(buffer, packet.getLength(), packet.getAddress(), packet.getPort());
                        socket.send(pongPacket);
                        continue;
                    }

                    String command = new String(packet.getData(), 0, packet.getLength()).trim();
                    String senderIp = packet.getAddress() != null ? packet.getAddress().getHostAddress() : "";
                    if (listener != null) {
                        listener.onCommandReceived(command, senderIp);
                    }
                }
            } catch (Exception e) {
                if (isRunning.get()) e.printStackTrace();
            } finally {
                closeSocket();
            }
        });
        thread.setName("CommandServerThread");
        thread.start();
    }

    public void stop() {
        isRunning.set(false) ;
        closeSocket();
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    private void closeSocket() {
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }
}
