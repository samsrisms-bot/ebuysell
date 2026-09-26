package com.simivr.app.audio

/**
 * Wraps [GoertzelDtmf] with debounce logic suitable for a live, windowed audio stream: a digit is
 * only reported once its tone has been stable for [requiredConsecutiveWindows] windows in a row
 * (rejecting single-window blips from noise/voice), and it will not be reported again until the
 * tone drops out (rejecting duplicate reports while the caller holds a key down).
 */
class DtmfStreamDetector(
    sampleRate: Int = 8000,
    windowMs: Int = 40,
    private val sensitivity: Double = 0.5,
    private val requiredConsecutiveWindows: Int = 2
) {
    val goertzel = GoertzelDtmf(sampleRate, windowMs)
    val windowSize get() = goertzel.windowSize

    private var candidateDigit: Char? = null
    private var candidateCount = 0
    private var lastConfirmedDigit: Char? = null

    /** Feed one window of exactly [windowSize] PCM16 samples. Returns a newly confirmed digit, or null. */
    fun processWindow(samples: ShortArray): Char? {
        val detection = goertzel.detect(samples, minMagnitude = GoertzelDtmf.thresholdFor(sensitivity))
        val digit = detection?.digit

        if (digit == null) {
            candidateDigit = null
            candidateCount = 0
            lastConfirmedDigit = null
            return null
        }

        if (digit == candidateDigit) {
            candidateCount++
        } else {
            candidateDigit = digit
            candidateCount = 1
        }

        if (candidateCount >= requiredConsecutiveWindows && lastConfirmedDigit != digit) {
            lastConfirmedDigit = digit
            return digit
        }
        return null
    }

    fun reset() {
        candidateDigit = null
        candidateCount = 0
        lastConfirmedDigit = null
    }
}
