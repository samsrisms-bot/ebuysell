package com.simivr.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Plays a prompt file through the in-call audio path. Because third-party apps cannot inject
 * audio into the telephony uplink, this relies on [android.telecom.Call.setAudioRoute] having
 * already switched the call to [android.telecom.CallAudioState.ROUTE_SPEAKER] (done by
 * telecom.CallController before any prompt plays) — the far end then hears the speaker acoustically
 * through the mic, the same way a human on speakerphone would be heard. Playback volume is forced
 * near max on the VOICE_CALL/CALL stream so the far end's mic can pick it up cleanly.
 */
@Singleton
class Player @Inject constructor(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    suspend fun playFile(path: String, streamType: Int = AudioManager.STREAM_VOICE_CALL) {
        stop()
        setStreamVolumeToMax(streamType)
        suspendCancellableCoroutine<Unit> { cont ->
            val mp = MediaPlayer()
            mediaPlayer = mp
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                mp.setDataSource(path)
                mp.setOnCompletionListener {
                    if (cont.isActive) cont.resume(Unit)
                    releasePlayer(mp)
                }
                mp.setOnErrorListener { _, what, extra ->
                    if (cont.isActive) cont.resumeWithException(IllegalStateException("MediaPlayer error $what/$extra"))
                    releasePlayer(mp)
                    true
                }
                mp.prepare()
                mp.start()
            } catch (e: Exception) {
                releasePlayer(mp)
                if (cont.isActive) cont.resumeWithException(e)
            }
            cont.invokeOnCancellation { releasePlayer(mp) }
        }
    }

    fun stop() {
        mediaPlayer?.let { releasePlayer(it) }
    }

    private fun releasePlayer(mp: MediaPlayer) {
        try {
            if (mp.isPlaying) mp.stop()
        } catch (_: IllegalStateException) {
            // already stopped/released
        }
        mp.release()
        if (mediaPlayer == mp) mediaPlayer = null
    }

    private fun setStreamVolumeToMax(streamType: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(streamType)
        audioManager.setStreamVolume(streamType, max, 0)
    }

    /** Plays a short audible test tone through the current audio route, used by the Call Audio Test screen. */
    fun playTestTone(durationMs: Int = 1200) {
        val toneGen = ToneGenerator(AudioManager.STREAM_VOICE_CALL, ToneGenerator.MAX_VOLUME)
        toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, durationMs)
        Thread {
            Thread.sleep(durationMs.toLong() + 100)
            toneGen.release()
        }.start()
    }

    /** Emits a single DTMF tone toward the far end via the standard telephony tone generator (local audible feedback only; use Call.playDtmfTone to actually send DTMF on the line). */
    fun playDtmfFeedback(digit: Char, durationMs: Int = 150) {
        val tone = when (digit) {
            '0' -> ToneGenerator.TONE_DTMF_0
            '1' -> ToneGenerator.TONE_DTMF_1
            '2' -> ToneGenerator.TONE_DTMF_2
            '3' -> ToneGenerator.TONE_DTMF_3
            '4' -> ToneGenerator.TONE_DTMF_4
            '5' -> ToneGenerator.TONE_DTMF_5
            '6' -> ToneGenerator.TONE_DTMF_6
            '7' -> ToneGenerator.TONE_DTMF_7
            '8' -> ToneGenerator.TONE_DTMF_8
            '9' -> ToneGenerator.TONE_DTMF_9
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        val toneGen = ToneGenerator(AudioManager.STREAM_VOICE_CALL, ToneGenerator.MAX_VOLUME)
        toneGen.startTone(tone, durationMs)
        Thread {
            Thread.sleep(durationMs.toLong() + 50)
            toneGen.release()
        }.start()
    }
}
