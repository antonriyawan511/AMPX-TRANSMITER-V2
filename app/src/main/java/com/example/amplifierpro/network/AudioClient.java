package com.example.amplifierpro.network;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class AudioClient {
    private Socket socket;
    private BufferedOutputStream bos;

    public boolean connect(String ip, int port) {
        try {
            socket = new Socket();
            socket.setTcpNoDelay(true);
            socket.setSendBufferSize(32 * 1024); // Reduced from 1MB to 32KB to eliminate Transmitter-side TCP buffer lag
            socket.connect(new InetSocketAddress(ip, port), 5000);
            socket.setSoTimeout(10000); // For handshake
            return true;
        } catch (Exception e) {
            close();
            return false;
        }
    }

    public String performHandshake() {
        if (socket == null) return null;
        try {
            InputStream is = socket.getInputStream();
            int length = is.read();
            if (length > 0 && length <= 255) {
                byte[] nameBytes = new byte[length];
                int readCount = 0;
                while (readCount < length) {
                    int r = is.read(nameBytes, readCount, length - readCount);
                    if (r == -1) break;
                    readCount += r;
                }
                socket.setSoTimeout(0); // Reset for data
                bos = new BufferedOutputStream(socket.getOutputStream(), 16384);
                return new String(nameBytes, 0, readCount, "UTF-8");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean sendAudio(byte[] buffer, int offset, int length) {
        if (bos == null || socket == null || socket.isClosed()) return false;
        try {
            bos.write(buffer, offset, length);
            bos.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void close() {
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {}
        socket = null;
        bos = null;
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }
}
