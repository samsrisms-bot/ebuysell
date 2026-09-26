package com.simivr.app.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal 16-bit PCM mono WAV writer — enough to make recordings playable by any standard player. */
class WavFileWriter(private val file: File, private val sampleRate: Int) {
    private val raf = RandomAccessFile(file, "rw")
    private var dataBytesWritten = 0L

    init {
        raf.setLength(0)
        writeHeaderPlaceholder()
    }

    private fun writeHeaderPlaceholder() {
        raf.seek(0)
        raf.write(ByteArray(44)) // patched in close()
    }

    fun write(samples: ShortArray, length: Int = samples.size) {
        val buffer = ByteBuffer.allocate(length * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until length) buffer.putShort(samples[i])
        raf.write(buffer.array())
        dataBytesWritten += length * 2
    }

    fun close() {
        finalizeHeader()
        raf.close()
    }

    private fun finalizeHeader() {
        val totalDataLen = dataBytesWritten + 36
        val byteRate = sampleRate * 2 // mono, 16-bit
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(totalDataLen.toInt())
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16) // PCM chunk size
        header.putShort(1) // audio format = PCM
        header.putShort(1) // channels = mono
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(2) // block align
        header.putShort(16) // bits per sample
        header.put("data".toByteArray())
        header.putInt(dataBytesWritten.toInt())
        raf.seek(0)
        raf.write(header.array())
    }
}
