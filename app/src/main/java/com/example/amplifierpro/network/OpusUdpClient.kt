package com.example.amplifierpro.network

import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.example.amplifierpro.codec.NativeOpusEncoder
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class OpusUdpClient {

    companion object {
        private const val TAG = "OpusUdpClient"
        const val SAMPLE_RATE = 48000
        const val CHANNELS = 2
        private const val MAX_OPUS_PAYLOAD = 1275
        private const val RING_BUFFER_SIZE = 262144 // 256 KB (harus pangkat 2)
        private const val RING_BUFFER_MASK = RING_BUFFER_SIZE - 1
    }

    private var socket: DatagramSocket? = null
    private var targetAddress: InetAddress? = null
    private var targetPort: Int = 5000
    private var isBroadcast: Boolean = false

    @Volatile private var encoder: NativeOpusEncoder? = null
    private val encoderLock = Any()
    private var controlChannel: TransmitterControlChannel? = null
    private var senderThread: Thread? = null

    val isConnected = AtomicBoolean(false)
    val packetsSent = AtomicLong(0)
    val bytesSent = AtomicLong(0)

    // Dynamic settings
    @Volatile var currentStreamMode: Int = 0 // 0=PCM Uncompressed bit-perfect, 1=Opus Compressed
    @Volatile var currentSampleRate: Int = 44100
    @Volatile var currentBitrate: Int = 96000
    @Volatile var currentFrameMs: Int = 20
    @Volatile var currentAudioProfile: Int = 0 // 0=Audio/Musik, 1=Low Delay, 2=VOIP
    @Volatile var currentFecPercent: Int = 0
    @Volatile var currentQosMode: Int = 1 // 0=Low Latency (DSCP EF 0xB8), 1=Standar (DSCP AF31 0x68)
    @Volatile var currentPreset: Int = 0 // 0=Standar (500ms), 1=Gaming (150ms), 2=HiFi (1000ms), 3=Jarak Jauh (2500ms)

    private var sequenceNumber: Int = 0
    private var timestamp: Long = 0
    private var fractionalTimestampMs: Double = 0.0

    // Pre-allocated Ring Buffer memisahkan thread AudioRecord+DSP dari thread Encode+UDP Send
    private val ringLock = Object()
    private val ringBuffer = ByteArray(RING_BUFFER_SIZE)
    private var ringWritePos: Int = 0
    private var ringReadPos: Int = 0
    private var ringAvailable: Int = 0

    // Buffer daur ulang (Zero-Allocation Loop di UdpSenderThread)
    private val frameExtractBuffer = ByteArray(11520) // hingga 60ms stereo @ 48kHz
    private val reusableShortBuffer = ShortArray(5760)
    private val encodeOutBuffer = ByteArray(MAX_OPUS_PAYLOAD)
    private val sendPacketBuffer = ByteArray(AudioPacket.HEADER_SIZE + 2048)

    @Synchronized
    fun connect(ip: String, port: Int, broadcast: Boolean): Boolean {
        try {
            close()

            targetAddress = InetAddress.getByName(ip)
            targetPort = port
            isBroadcast = broadcast

            socket = DatagramSocket().apply {
                this.broadcast = broadcast
                try {
                    sendBufferSize = 1024 * 512
                } catch (_: Exception) {}
                applyQoS(this, currentQosMode)
            }

            synchronized(encoderLock) {
                encoder?.release()
                encoder = null
                if (currentStreamMode == 1) {
                    val appMode = when (currentAudioProfile) {
                        1 -> NativeOpusEncoder.OPUS_APPLICATION_RESTRICTED_LOWDELAY
                        2 -> NativeOpusEncoder.OPUS_APPLICATION_VOIP
                        else -> NativeOpusEncoder.OPUS_APPLICATION_AUDIO
                    }
                    encoder = NativeOpusEncoder(SAMPLE_RATE, CHANNELS, appMode).apply {
                        configure(
                            bitrate = currentBitrate,
                            complexity = 4,
                            signalMusic = (currentAudioProfile != 2),
                            useInbandFec = (currentFecPercent > 0 && currentAudioProfile == 2),
                            packetLossPerc = currentFecPercent
                        )
                    }
                }
            }

            // Inisialisasi Saluran Kontrol UDP
            controlChannel = TransmitterControlChannel().apply {
                start(object : TransmitterControlChannel.Listener {
                    override fun onRequestConfig(senderAddress: InetAddress, senderPort: Int) {
                        val (targetMs, maxMs) = getBufferLimitsForPreset(currentPreset)
                        val effectiveFrameMs = if (currentStreamMode == 0) 5 else currentFrameMs
                        sendConfig(senderAddress, currentPreset, targetMs, maxMs, effectiveFrameMs)
                    }
                })
                targetAddress?.let { addr ->
                    val (targetMs, maxMs) = getBufferLimitsForPreset(currentPreset)
                    val effectiveFrameMs = if (currentStreamMode == 0) 5 else currentFrameMs
                    sendStart(addr, currentPreset, targetMs, maxMs, effectiveFrameMs)
                }
            }

            sequenceNumber = 0
            fractionalTimestampMs = SystemClock.elapsedRealtime().toDouble()
            timestamp = fractionalTimestampMs.toLong()
            synchronized(ringLock) {
                ringWritePos = 0
                ringReadPos = 0
                ringAvailable = 0
            }
            packetsSent.set(0)
            bytesSent.set(0)
            isConnected.set(true)

            senderThread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                runSenderLoop()
            }, "UdpStreamSenderThread").apply { start() }

            Log.i(TAG, "OpusUdpClient (Native C libopus) terhubung ke $ip:$port (Mode=$currentStreamMode, Bitrate=$currentBitrate)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menghubungkan OpusUdpClient", e)
            close()
            return false
        }
    }

    fun sendAudio(buffer: ByteArray, offset: Int, length: Int): Boolean {
        if (!isConnected.get() || socket == null) return false
        if (length <= 0) return true

        synchronized(ringLock) {
            if (!isConnected.get()) return false
            // Jika ring buffer penuh akibat jaringan macet ekstrem, buang data tertua (jaga real-time)
            if (ringAvailable + length > RING_BUFFER_SIZE) {
                val overflow = (ringAvailable + length) - RING_BUFFER_SIZE
                ringReadPos = (ringReadPos + overflow) and RING_BUFFER_MASK
                ringAvailable -= overflow
            }

            val firstChunk = Math.min(length, RING_BUFFER_SIZE - ringWritePos)
            System.arraycopy(buffer, offset, ringBuffer, ringWritePos, firstChunk)
            if (firstChunk < length) {
                System.arraycopy(buffer, offset + firstChunk, ringBuffer, 0, length - firstChunk)
            }
            ringWritePos = (ringWritePos + length) and RING_BUFFER_MASK
            ringAvailable += length
            ringLock.notifyAll()
        }
        return true
    }

    private fun runSenderLoop() {
        while (isConnected.get()) {
            try {
                val mode = currentStreamMode
                val frameMs = currentFrameMs
                val samplesPerFrame = (SAMPLE_RATE * frameMs) / 1000
                val requiredBytes = if (mode == 0) {
                    1024 // 256 samples stereo (5.8ms @ 44.1k, 5.3ms @ 48k)
                } else {
                    samplesPerFrame * CHANNELS * 2
                }

                synchronized(ringLock) {
                    while (isConnected.get() && ringAvailable < requiredBytes) {
                        ringLock.wait(20)
                        val updatedMode = currentStreamMode
                        val updatedFrameMs = currentFrameMs
                        val updatedRequired = if (updatedMode == 0) 1024 else ((SAMPLE_RATE * updatedFrameMs) / 1000) * CHANNELS * 2
                        if (updatedRequired != requiredBytes) break
                    }
                    if (!isConnected.get()) return
                    if (ringAvailable < requiredBytes) continue

                    val firstCopy = Math.min(requiredBytes, RING_BUFFER_SIZE - ringReadPos)
                    System.arraycopy(ringBuffer, ringReadPos, frameExtractBuffer, 0, firstCopy)
                    if (firstCopy < requiredBytes) {
                        System.arraycopy(ringBuffer, 0, frameExtractBuffer, firstCopy, requiredBytes - firstCopy)
                    }
                    ringReadPos = (ringReadPos + requiredBytes) and RING_BUFFER_MASK
                    ringAvailable -= requiredBytes
                }

                val sock = socket ?: break
                val dest = targetAddress ?: break

                if (mode == 0) {
                    val sr = currentSampleRate
                    val codecByte = if (sr == 44100) AudioPacket.CODEC_PCM_44K else AudioPacket.CODEC_PCM_48K
                    val packetSize = AudioPacket.build(
                        sequence = (sequenceNumber++ and 0xFFFF),
                        timestamp = timestamp,
                        codec = codecByte,
                        payload = frameExtractBuffer,
                        payloadOffset = 0,
                        payloadLength = requiredBytes,
                        outPacketBuffer = sendPacketBuffer
                    )
                    fractionalTimestampMs += (requiredBytes * 1000.0) / (sr * CHANNELS * 2.0)
                    timestamp = fractionalTimestampMs.toLong()

                    val packet = DatagramPacket(sendPacketBuffer, packetSize, dest, targetPort)
                    sock.send(packet)

                    packetsSent.incrementAndGet()
                    bytesSent.addAndGet(packetSize.toLong())
                } else {
                    encodeAndSendFrame(frameExtractBuffer, samplesPerFrame, frameMs, sock, dest)
                }
            } catch (e: InterruptedException) {
                if (!isConnected.get()) break
            } catch (e: Exception) {
                if (!isConnected.get()) break
                Log.e(TAG, "Error pada UdpStreamSenderThread", e)
            }
        }
    }

    private fun encodeAndSendFrame(
        pcmBytes: ByteArray,
        samplesPerFrame: Int,
        frameMs: Int,
        sock: DatagramSocket,
        dest: InetAddress
    ) {
        val shortCount = samplesPerFrame * CHANNELS
        var bi = 0
        for (si in 0 until shortCount) {
            val low = pcmBytes[bi].toInt() and 0xFF
            val high = pcmBytes[bi + 1].toInt()
            reusableShortBuffer[si] = ((high shl 8) or low).toShort()
            bi += 2
        }

        val encodedBytes = synchronized(encoderLock) {
            val enc = encoder ?: return
            enc.encode(reusableShortBuffer, 0, samplesPerFrame, encodeOutBuffer, encodeOutBuffer.size)
        }

        if (encodedBytes > 0) {
            val packetSize = AudioPacket.build(
                sequence = (sequenceNumber++ and 0xFFFF),
                timestamp = timestamp,
                payload = encodeOutBuffer,
                payloadOffset = 0,
                payloadLength = encodedBytes,
                outPacketBuffer = sendPacketBuffer
            )

            fractionalTimestampMs += frameMs.toDouble()
            timestamp = fractionalTimestampMs.toLong()

            val packet = DatagramPacket(sendPacketBuffer, packetSize, dest, targetPort)
            sock.send(packet)

            packetsSent.incrementAndGet()
            bytesSent.addAndGet(packetSize.toLong())
        }
    }

    @Synchronized
    fun updateSettings(
        preset: Int,
        bitrate: Int,
        frameMs: Int,
        audioProfile: Int,
        fecPercent: Int,
        qosMode: Int
    ) {
        currentPreset = preset
        currentBitrate = bitrate
        currentFrameMs = frameMs
        currentAudioProfile = audioProfile
        currentFecPercent = fecPercent
        currentQosMode = qosMode

        synchronized(ringLock) {
            ringWritePos = 0
            ringReadPos = 0
            ringAvailable = 0
            ringLock.notifyAll()
        }

        synchronized(encoderLock) {
            encoder?.release()
            encoder = null
            if (currentStreamMode == 1) {
                val appMode = when (currentAudioProfile) {
                    1 -> NativeOpusEncoder.OPUS_APPLICATION_RESTRICTED_LOWDELAY
                    2 -> NativeOpusEncoder.OPUS_APPLICATION_VOIP
                    else -> NativeOpusEncoder.OPUS_APPLICATION_AUDIO
                }
                try {
                    encoder = NativeOpusEncoder(SAMPLE_RATE, CHANNELS, appMode).apply {
                        configure(
                            bitrate = currentBitrate,
                            complexity = 4,
                            signalMusic = (currentAudioProfile != 2),
                            useInbandFec = (currentFecPercent > 0 && currentAudioProfile == 2),
                            packetLossPerc = currentFecPercent
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal re-inisialisasi NativeOpusEncoder saat updateSettings", e)
                }
            }
        }

        socket?.let { sock ->
            applyQoS(sock, qosMode)
        }

        targetAddress?.let { addr ->
            val (targetMs, maxMs) = getBufferLimitsForPreset(preset)
            val effectiveFrameMs = if (currentStreamMode == 0) 5 else frameMs
            controlChannel?.sendConfig(addr, preset, targetMs, maxMs, effectiveFrameMs)
        }
    }

    private fun applyQoS(sock: DatagramSocket, qos: Int) {
        try {
            when (qos) {
                0 -> {
                    sock.trafficClass = 0xB8 // DSCP EF (Expedited Forwarding - Real-Time Audio)
                    try { sock.sendBufferSize = 1024 * 512 } catch (_: Exception) {}
                }
                1 -> {
                    sock.trafficClass = 0x68 // DSCP AF31 (Multimedia Streaming, RFC 2597)
                    try { sock.sendBufferSize = 1024 * 512 } catch (_: Exception) {}
                }
                else -> {
                    sock.trafficClass = 0xB8
                    try { sock.sendBufferSize = 1024 * 512 } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    fun getBufferLimitsForPreset(preset: Int): Pair<Int, Int> {
        return when (preset) {
            0 -> 500 to 1500   // Standar Video (500ms target, 1500ms max)
            1 -> 150 to 500    // Gaming Low Latency (150ms target, 500ms max)
            2 -> 60 to 200     // Pro Gaming Super Cepat (60ms target, 200ms max)
            3 -> 20 to 100     // Real-Time Live Ekstrem (20ms target, 100ms max)
            4 -> 1000 to 3000  // Hi-Fi Musik (1000ms target, 3000ms max)
            5 -> 2500 to 6000  // Jarak Jauh / Sinyal Lemah (2500ms target, 6000ms max)
            else -> when (currentFrameMs) {
                5 -> 60 to 200
                10 -> 150 to 500
                20 -> 1000 to 3000
                else -> 2500 to 6000
            }
        }
    }

    @Synchronized
    fun close() {
        isConnected.set(false)
        synchronized(ringLock) {
            ringWritePos = 0
            ringReadPos = 0
            ringAvailable = 0
            ringLock.notifyAll()
        }

        senderThread?.interrupt()
        try {
            senderThread?.join(250)
        } catch (_: Exception) {}
        senderThread = null

        targetAddress?.let { addr ->
            try {
                controlChannel?.sendStopAndClose(addr)
            } catch (_: Exception) {}
        }
        controlChannel?.stop()
        controlChannel = null

        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null

        synchronized(encoderLock) {
            encoder?.release()
            encoder = null
        }
        targetAddress = null
    }
}
