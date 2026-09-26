package com.simivr.app.telecom

import android.content.Context
import android.net.Uri
import android.telecom.Call
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.simivr.app.audio.DtmfListener
import com.simivr.app.audio.MicCapture
import com.simivr.app.audio.Player
import com.simivr.app.audio.TtsEngine
import com.simivr.app.flow.CallEndedException
import com.simivr.app.flow.FlowActionHandler
import com.simivr.app.flow.model.AudioSource
import com.simivr.app.flow.model.TransferMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID

/**
 * Real, on-device implementation of [FlowActionHandler]: plays audio through the (now
 * speakerphone-routed) call, listens for DTMF via microphone capture + Goertzel detection, places
 * transfer calls, and fires webhooks — all against whichever [Call] is currently active in
 * [CallRepository].
 */
class CallFlowActionHandler(
    private val context: Context,
    private val callRepository: CallRepository,
    private val callController: CallController,
    private val player: Player,
    private val ttsEngine: TtsEngine,
    private val dtmfListener: DtmfListener,
    private val micCapture: MicCapture,
    private val dtmfSensitivity: Double = 0.5
) : FlowActionHandler {

    private val httpClient = OkHttpClient()

    private fun requireCall(): Call = callRepository.activeCall ?: throw CallEndedException("No active call")

    private suspend fun resolveAudioPath(audio: AudioSource): String = when (audio) {
        is AudioSource.File -> audio.path
        is AudioSource.Tts -> ttsEngine.synthesizeToFile(audio.text, audio.locale)
    }

    override suspend fun play(audio: AudioSource) {
        requireCall()
        callController.routeToSpeaker()
        val path = resolveAudioPath(audio)
        player.playFile(path)
    }

    override suspend fun collectDigits(prompt: AudioSource, numDigits: Int, terminator: Char?, timeoutMs: Long): String {
        play(prompt)
        val collected = StringBuilder()
        withTimeoutOrNull(timeoutMs) {
            try {
                dtmfListener.listen(dtmfSensitivity).collect { digit ->
                    if (terminator != null && digit == terminator) {
                        throw StopCollecting()
                    }
                    collected.append(digit)
                    if (collected.length >= numDigits) throw StopCollecting()
                }
            } catch (_: StopCollecting) {
                // numDigits reached or terminator pressed — normal early exit
            }
        }
        return collected.toString()
    }

    override suspend fun waitForDigit(prompt: AudioSource, timeoutMs: Long): Char? {
        play(prompt)
        return withTimeoutOrNull(timeoutMs) {
            dtmfListener.listen(dtmfSensitivity).first()
        }
    }

    override suspend fun transfer(number: String, mode: TransferMode) {
        val call = requireCall()
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val accountHandle: PhoneAccountHandle? = call.details?.accountHandle
        val extras = android.os.Bundle().apply {
            if (accountHandle != null) putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle)
        }
        try {
            telecomManager.placeCall(Uri.fromParts("tel", number, null), extras)
        } catch (e: SecurityException) {
            return
        }
        // The new leg surfaces as a second onCallAdded in IvrInCallService; conferencing that call
        // in is handled there once it goes active (see IvrInCallService transfer handling notes in README —
        // conference support is carrier/OEM-dependent).
    }

    override suspend fun recordVoicemail(prompt: AudioSource, maxDurationMs: Long, playConsentLine: Boolean): String {
        if (playConsentLine) {
            play(AudioSource.Tts("This call may be recorded for quality purposes. Please leave your message after the tone.", "en-IN"))
        } else {
            play(prompt)
        }
        val outFile = File(context.getExternalFilesDir("recordings"), "voicemail_${UUID.randomUUID()}.wav")
        outFile.parentFile?.mkdirs()
        return micCapture.recordToFile(outFile, maxDurationMs)
    }

    override suspend fun callWebhook(url: String, method: String, vars: Map<String, String>): Boolean {
        return try {
            val requestBuilder = Request.Builder().url(url)
            if (method.equals("POST", ignoreCase = true)) {
                val json = com.google.gson.Gson().toJson(vars)
                requestBuilder.post(json.toRequestBody("application/json".toMediaType()))
            }
            httpClient.newCall(requestBuilder.build()).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun hangup() {
        callController.hangup(requireCall())
    }

    private class StopCollecting : Exception()
}
