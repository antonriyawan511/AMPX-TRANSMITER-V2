package com.example.amplifierpro.codec

class NativeOpusEncoder(
    val sampleRate: Int = 48000,
    val channels: Int = 2,
    application: Int = OPUS_APPLICATION_AUDIO
) {
    companion object {
        const val OPUS_APPLICATION_VOIP = 2048
        const val OPUS_APPLICATION_AUDIO = 2049
        const val OPUS_APPLICATION_RESTRICTED_LOWDELAY = 2051

        init {
            System.loadLibrary("native_opus")
        }
    }

    private var encoderHandle: Long = 0

    init {
        encoderHandle = createEncoder(sampleRate, channels, application)
        if (encoderHandle == 0L) {
            throw IllegalStateException("Gagal membuat native C OpusEncoder")
        }
    }

    @Synchronized
    fun configure(
        bitrate: Int,
        complexity: Int = 5,
        signalMusic: Boolean = true,
        useInbandFec: Boolean = false,
        packetLossPerc: Int = 0
    ) {
        val handle = encoderHandle
        if (handle != 0L) {
            configureEncoder(handle, bitrate, complexity, signalMusic, useInbandFec, packetLossPerc)
        }
    }

    @Synchronized
    fun encode(
        inPcm: ShortArray,
        inOffset: Int,
        frameSize: Int,
        outData: ByteArray,
        maxDataBytes: Int
    ): Int {
        val handle = encoderHandle
        if (handle == 0L) return -1
        return encode(handle, inPcm, inOffset, frameSize, outData, maxDataBytes)
    }

    @Synchronized
    fun reset() {
        val handle = encoderHandle
        if (handle != 0L) {
            resetEncoder(handle)
        }
    }

    @Synchronized
    fun release() {
        val handle = encoderHandle
        if (handle != 0L) {
            encoderHandle = 0L
            destroyEncoder(handle)
        }
    }

    protected fun finalize() {
        release()
    }

    private external fun createEncoder(sampleRate: Int, channels: Int, application: Int): Long
    private external fun configureEncoder(
        encoderPtr: Long,
        bitrate: Int,
        complexity: Int,
        signalMusic: Boolean,
        useInbandFec: Boolean,
        packetLossPerc: Int
    )
    private external fun encode(
        encoderPtr: Long,
        inPcm: ShortArray,
        inOffset: Int,
        frameSize: Int,
        outData: ByteArray,
        maxDataBytes: Int
    ): Int
    private external fun resetEncoder(encoderPtr: Long)
    private external fun destroyEncoder(encoderPtr: Long)
}
