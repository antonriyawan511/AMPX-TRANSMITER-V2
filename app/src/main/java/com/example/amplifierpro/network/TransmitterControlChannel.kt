package com.example.amplifierpro.network

import android.os.SystemClock
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

class TransmitterControlChannel(
    val controlPort: Int = CONTROL_PORT
) {
    companion object {
        private const val TAG = "TransmitterControl"
        const val CONTROL_PORT = 5001
    }

    interface Listener {
        fun onRequestConfig(senderAddress: InetAddress, senderPort: Int)
    }

    private val isRunning = AtomicBoolean(false)
    private var controlSocket: DatagramSocket? = null
    private var listenThread: Thread? = null
    var listener: Listener? = null

    fun start(listener: Listener? = null) {
        if (isRunning.getAndSet(true)) return
        this.listener = listener

        try {
            controlSocket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(controlPort))
                broadcast = true
                try {
                    trafficClass = 0xB8
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal bind control socket di port $controlPort, fallback ke dynamic control socket", e)
            try {
                controlSocket = DatagramSocket().apply {
                    broadcast = true
                    try {
                        trafficClass = 0xB8
                    } catch (_: Exception) {}
                }
            } catch (err: Exception) {
                Log.e(TAG, "Gagal membuat control socket", err)
            }
        }

        listenThread = Thread({
            val buf = ByteArray(1024)
            val packet = DatagramPacket(buf, buf.size)
            while (isRunning.get()) {
                try {
                    packet.length = buf.size
                    controlSocket?.receive(packet)
                    val str = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                    val json = JSONObject(str)
                    val action = json.optString("action")

                    if (action == "REQUEST_CONFIG") {
                        val host = packet.address?.hostAddress
                        if (host != null) {
                            com.example.amplifierpro.service.AudioStreamService.notifyReceiverAlive(host)
                        }
                        this.listener?.onRequestConfig(packet.address, packet.port)
                    } else if (action == "ALIVE" || action == "PING") {
                        val host = packet.address?.hostAddress
                        if (host != null) {
                            com.example.amplifierpro.service.AudioStreamService.notifyReceiverAlive(host)
                        }
                    } else if (action == "BYE" || action == "STOP") {
                        val host = packet.address?.hostAddress
                        if (host != null) {
                            com.example.amplifierpro.service.AudioStreamService.notifyReceiverDisconnected(host)
                        }
                    }
                } catch (e: SocketException) {
                    break
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        Log.w(TAG, "Error saat membaca control socket", e)
                    }
                }
            }
        }, "Transmitter-ControlListenThread").apply { start() }
    }

    fun sendStart(destAddress: InetAddress, preset: Int, targetMs: Int, maxMs: Int, frameMs: Int = 20) {
        val json = JSONObject().apply {
            put("action", "START")
            put("preset", preset)
            put("targetMs", targetMs)
            put("maxMs", maxMs)
            put("frameMs", frameMs)
        }
        sendPacketAsync(destAddress, controlPort, json, duplicateCount = 2)
    }

    fun sendConfig(destAddress: InetAddress, preset: Int, targetMs: Int, maxMs: Int, frameMs: Int = 20) {
        val json = JSONObject().apply {
            put("action", "CONFIG")
            put("preset", preset)
            put("targetMs", targetMs)
            put("maxMs", maxMs)
            put("frameMs", frameMs)
        }
        sendPacketAsync(destAddress, controlPort, json, duplicateCount = 2)
    }

    fun sendStopAndClose(destAddress: InetAddress) {
        if (!isRunning.getAndSet(false)) return

        val socketToClose = controlSocket
        val threadToInterrupt = listenThread
        controlSocket = null
        listenThread = null

        Thread({
            try {
                val json = JSONObject().apply {
                    put("action", "STOP")
                }
                val bytes = json.toString().toByteArray(Charsets.UTF_8)
                val packet = DatagramPacket(bytes, bytes.size, destAddress, controlPort)
                socketToClose?.send(packet)
                SystemClock.sleep(15)
                socketToClose?.send(packet)
                Log.d(TAG, "Berhasil mengirim aksi STOP ke $destAddress:$controlPort")
            } catch (e: Exception) {
                Log.w(TAG, "Gagal kirim STOP ke Receiver", e)
            } finally {
                try {
                    socketToClose?.close()
                } catch (_: Exception) {}
                threadToInterrupt?.interrupt()
            }
        }, "Transmitter-ControlStopSender").start()
    }

    private fun sendPacketAsync(dest: InetAddress, port: Int, json: JSONObject, duplicateCount: Int = 1) {
        Thread({
            try {
                val bytes = json.toString().toByteArray(Charsets.UTF_8)
                val packet = DatagramPacket(bytes, bytes.size, dest, port)
                for (i in 0 until duplicateCount) {
                    controlSocket?.send(packet)
                    if (i < duplicateCount - 1) {
                        SystemClock.sleep(15)
                    }
                }
                Log.d(TAG, "Berhasil kirim kontrol ${json.optString("action")} ke $dest:$port")
            } catch (e: Exception) {
                Log.w(TAG, "Gagal kirim paket kontrol ke $dest:$port", e)
            }
        }, "Transmitter-ControlSender").start()
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        try {
            controlSocket?.close()
        } catch (_: Exception) {}
        listenThread?.interrupt()
        try {
            listenThread?.join(250)
        } catch (_: Exception) {}
        controlSocket = null
        listenThread = null
        listener = null
    }
}
