package com.simivr.app.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Captures the microphone during a live call.
 *
 * IMPORTANT PLATFORM LIMITATION: Android gives third-party apps no API to read the far-end
 * (downlink) audio of a phone call directly. This class captures whatever the physical
 * microphone picks up — which, once the call is on speakerphone (see CallController), acoustically
 * includes the far end's voice/DTMF tones as they play out of the earpiece/speaker into the room,
 * the same way a human listening on speakerphone would hear them. Capture quality therefore
 * depends heavily on device model, speaker/mic placement, ambient noise, and OEM audio processing
 * (some OEMs mute or heavily denoise the mic during calls, which can prevent this entirely — this
 * is surfaced to the user via the Call Audio Test screen).
 *
 * Uses [MediaRecorder.AudioSource.VOICE_COMMUNICATION] (echo-cancelled call-oriented mic source),
 * the only source available to a third-party app that has any chance of working during an active
 * call — [MediaRecorder.AudioSource.VOICE_CALL] requires a system-signature permission and is not
 * usable by apps like this one.
 */
@Singleton
class MicCapture @Inject constructor(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 8000
        private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    @Volatile private var recording = false

    /** Emits fixed-size PCM16 windows of [windowSize] samples at [SAMPLE_RATE] Hz, suitable for [DtmfStreamDetector]. */
    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun windows(windowSize: Int): Flow<ShortArray> = callbackFlow {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBuf, windowSize * 4)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            close(IllegalStateException("AudioRecord failed to initialize — mic may be unavailable during calls on this device"))
            return@callbackFlow
        }
        audioRecord = record
        recording = true
        record.startRecording()

        val thread = Thread({
            val buffer = ShortArray(windowSize)
            while (recording) {
                var readTotal = 0
                while (readTotal < windowSize && recording) {
                    val n = record.read(buffer, readTotal, windowSize - readTotal)
                    if (n <= 0) break
                    readTotal += n
                }
                if (readTotal == windowSize) {
                    trySend(buffer.copyOf())
                }
            }
        }, "MicCapture-DTMF")
        thread.start()

        awaitClose {
            recording = false
            try {
                record.stop()
            } catch (_: IllegalStateException) {
            }
            record.release()
            audioRecord = null
        }
    }

    /** Records raw mic audio to [outFile] as a WAV file until [stopRecording] is called or [maxDurationMs] elapses. */
    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    suspend fun recordToFile(outFile: File, maxDurationMs: Long): String {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBuf, 4096)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("AudioRecord failed to initialize")
        }
        val writer = WavFileWriter(outFile, SAMPLE_RATE)
        audioRecord = record
        recording = true
        record.startRecording()
        val startedAt = System.currentTimeMillis()
        try {
            val chunk = ShortArray(1024)
            while (recording && System.currentTimeMillis() - startedAt < maxDurationMs) {
                val n = record.read(chunk, 0, chunk.size)
                if (n > 0) writer.write(chunk, n)
            }
        } finally {
            writer.close()
            try {
                record.stop()
            } catch (_: IllegalStateException) {
            }
            record.release()
            audioRecord = null
        }
        return outFile.absolutePath
    }

    fun stopRecording() {
        recording = false
    }
}
