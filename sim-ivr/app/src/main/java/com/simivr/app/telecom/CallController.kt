package com.simivr.app.telecom

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Thin, suspend-friendly wrapper around a single [android.telecom.Call] plus the app-wide audio
 * route (which lives on [android.telecom.InCallService], not on the Call itself).
 */
class CallController @Inject constructor(private val serviceHolder: InCallServiceHolder) {

    fun answer(call: Call) = call.answer(VideoProfile.STATE_AUDIO_ONLY)

    fun rejectSilently(call: Call) = call.reject(false, null)

    fun hangup(call: Call) = call.disconnect()

    /** Switches the whole in-call audio session to the speaker — required so the far end can acoustically hear our prompts. */
    fun routeToSpeaker() {
        serviceHolder.service?.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
    }

    fun sendDtmf(call: Call, digit: Char) {
        call.playDtmfTone(digit)
        call.stopDtmfTone()
    }

    /** Best-effort call transfer: places a second call to [number] on the same phone account, then
     * attempts to merge it with [call] into a conference so both parties can talk. True carrier
     * call transfer (SIP REFER / IMS ECT) is not exposed to third-party apps, so this is the closest
     * equivalent achievable with the public Telecom API — it depends on carrier/OEM conference-call
     * support and may not work on all networks (surfaced in the Call Audio Test screen / README). */
    suspend fun conferenceIn(call: Call, newCall: Call) {
        awaitActive(newCall)
        call.conference(newCall)
    }

    suspend fun awaitActive(call: Call, timeoutMs: Long = 45000): Boolean = suspendCancellableCoroutine { cont ->
        if (call.state == Call.STATE_ACTIVE) {
            cont.resume(true)
            return@suspendCancellableCoroutine
        }
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state == Call.STATE_ACTIVE) {
                    call.unregisterCallback(this)
                    if (cont.isActive) cont.resume(true)
                } else if (state == Call.STATE_DISCONNECTED) {
                    call.unregisterCallback(this)
                    if (cont.isActive) cont.resume(false)
                }
            }
        }
        call.registerCallback(callback)
        cont.invokeOnCancellation { call.unregisterCallback(callback) }
    }

    fun stateFlow(call: Call): Flow<Int> = callbackFlow {
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                trySend(state)
            }
        }
        call.registerCallback(callback)
        trySend(call.state)
        awaitClose { call.unregisterCallback(callback) }
    }
}
