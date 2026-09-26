package com.simivr.app.flow

import com.simivr.app.flow.model.AudioSource
import com.simivr.app.flow.model.TransferMode

/**
 * Bridges the pure [FlowEngine] state machine to real call-audio and telecom side effects.
 * Implemented against real hardware (speaker playback + Goertzel mic capture) in the
 * telecom/audio layer, and faked in unit tests so FlowEngine logic can be tested on the JVM.
 */
interface FlowActionHandler {
    /** Play a prompt to completion (blocks until playback finishes or the call ends). */
    suspend fun play(audio: AudioSource)

    /**
     * Play [prompt] then listen for DTMF digits until [terminator] is pressed, [numDigits] are
     * collected, or [timeoutMs] elapses. Returns whatever digits were collected (possibly fewer
     * than [numDigits] on timeout).
     */
    suspend fun collectDigits(prompt: AudioSource, numDigits: Int, terminator: Char?, timeoutMs: Long): String

    /** Play [prompt] then wait for a single DTMF digit. Returns null on timeout. */
    suspend fun waitForDigit(prompt: AudioSource, timeoutMs: Long): Char?

    /** Merge/forward the call to [number]. */
    suspend fun transfer(number: String, mode: TransferMode)

    /** Optionally play a consent line, then record the mic to a file until silence/timeout. Returns the file path. */
    suspend fun recordVoicemail(prompt: AudioSource, maxDurationMs: Long, playConsentLine: Boolean): String

    /** POST/GET a webhook with the current flow variables. Returns true on 2xx. */
    suspend fun callWebhook(url: String, method: String, vars: Map<String, String>): Boolean

    /** End the call. */
    suspend fun hangup()
}
