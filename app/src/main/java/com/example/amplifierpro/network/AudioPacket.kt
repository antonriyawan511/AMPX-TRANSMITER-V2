package com.example.amplifierpro.network

import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioPacket {
    const val HEADER_SIZE = 8
    const val MAGIC_BYTE: Byte = 0xA5.toByte()
    const val CODEC_OPUS: Byte = 0x01.toByte()
    const val CODEC_PCM_44K: Byte = 0x02.toByte()
    const val CODEC_PCM_48K: Byte = 0x03.toByte()

    fun build(
        sequence: Int,
        timestamp: Long,
        payload: ByteArray,
        payloadOffset: Int,
        payloadLength: Int,
        outPacketBuffer: ByteArray,
        codec: Byte = CODEC_OPUS
    ): Int {
        val totalSize = HEADER_SIZE + payloadLength
        if (outPacketBuffer.size < totalSize) {
            throw IllegalArgumentException("Buffer tujuan terlalu kecil: ${outPacketBuffer.size} < $totalSize")
        }

        val byteBuffer = ByteBuffer.wrap(outPacketBuffer).order(ByteOrder.BIG_ENDIAN)
        byteBuffer.put(MAGIC_BYTE)
        byteBuffer.put(codec)
        byteBuffer.putShort((sequence and 0xFFFF).toShort())
        byteBuffer.putInt((timestamp and 0xFFFFFFFFL).toInt())
        byteBuffer.put(payload, payloadOffset, payloadLength)

        return totalSize
    }
}
