package com.simivr.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wraps Android's on-device [TextToSpeech] engine, synthesizing prompts to a WAV file on disk so
 * they can be played through the call audio path exactly like an uploaded recording. Supports the
 * locales called for by the spec: en-IN, hi-IN, te-IN (falls back to the closest installed voice
 * if a language pack isn't installed on the device).
 */
@Singleton
class TtsEngine @Inject constructor(private val context: Context) {

    private var tts: TextToSpeech? = null
    private val cacheDir: File by lazy { File(context.cacheDir, "tts").apply { mkdirs() } }

    private suspend fun engine(): TextToSpeech = suspendCancellableCoroutine { cont ->
        if (tts != null) {
            cont.resume(tts!!)
            return@suspendCancellableCoroutine
        }
        lateinit var instance: TextToSpeech
        instance = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts = instance
                cont.resume(instance)
            } else {
                cont.resumeWithException(IllegalStateException("TextToSpeech init failed: $status"))
            }
        }
    }

    /** Synthesizes [text] in [localeTag] (e.g. "en-IN", "hi-IN", "te-IN") to a cached WAV file, returning its path. */
    suspend fun synthesizeToFile(text: String, localeTag: String): String {
        val engine = engine()
        val locale = Locale.forLanguageTag(localeTag)
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.language = Locale.ENGLISH
        }
        val outFile = File(cacheDir, "tts_${UUID.randomUUID()}.wav")
        val utteranceId = outFile.name

        return suspendCancellableCoroutine { cont ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(id: String?) {
                    if (id == utteranceId && cont.isActive) cont.resume(outFile.absolutePath)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId && cont.isActive) cont.resumeWithException(IllegalStateException("TTS synthesis failed"))
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId && cont.isActive) cont.resumeWithException(IllegalStateException("TTS synthesis failed: $errorCode"))
                }
            })
            val params = android.os.Bundle()
            val synthResult = engine.synthesizeToFile(text, params, outFile, utteranceId)
            if (synthResult != TextToSpeech.SUCCESS) {
                cont.resumeWithException(IllegalStateException("synthesizeToFile rejected request"))
            }
        }
    }

    fun availableVoices(): Set<Voice> = tts?.voices ?: emptySet()

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }

    companion object {
        val SUPPORTED_LOCALES = listOf("en-IN", "hi-IN", "te-IN")
    }
}
