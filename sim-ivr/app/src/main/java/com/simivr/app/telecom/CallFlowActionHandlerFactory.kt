package com.simivr.app.telecom

import android.content.Context
import com.simivr.app.audio.DtmfListener
import com.simivr.app.audio.MicCapture
import com.simivr.app.audio.Player
import com.simivr.app.audio.TtsEngine
import javax.inject.Inject
import javax.inject.Singleton

/** Builds a [CallFlowActionHandler] with the caller-configured DTMF sensitivity (a plain Double can't be a Hilt injection target on its own). */
@Singleton
class CallFlowActionHandlerFactory @Inject constructor(
    private val context: Context,
    private val callRepository: CallRepository,
    private val callController: CallController,
    private val player: Player,
    private val ttsEngine: TtsEngine,
    private val dtmfListener: DtmfListener,
    private val micCapture: MicCapture
) {
    fun create(dtmfSensitivity: Double = 0.5): CallFlowActionHandler = CallFlowActionHandler(
        context, callRepository, callController, player, ttsEngine, dtmfListener, micCapture, dtmfSensitivity
    )
}
