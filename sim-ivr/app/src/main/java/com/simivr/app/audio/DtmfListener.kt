package com.simivr.app.audio

import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject
import javax.inject.Singleton

/** Combines [MicCapture] with a [DtmfStreamDetector] into a simple Flow of confirmed keypresses. */
@Singleton
class DtmfListener @Inject constructor(private val micCapture: MicCapture) {

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun listen(sensitivity: Double = 0.5): Flow<Char> {
        val detector = DtmfStreamDetector(sensitivity = sensitivity)
        return micCapture.windows(detector.windowSize).mapNotNull { window -> detector.processWindow(window) }
    }

    fun stop() = micCapture.stopRecording()
}
