package com.simivr.app.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin DTMF tone detector using the Goertzel algorithm — no Android dependencies, so it
 * can be exercised directly by JVM unit tests with synthesized sine waves.
 *
 * Standard DTMF uses 8 tones split into a "low" group (697/770/852/941 Hz, selects the row) and
 * a "high" group (1209/1336/1477/1633 Hz, selects the column). A single window (default 40ms at
 * 8kHz = 320 samples) is analyzed for energy at each of the 8 frequencies; the strongest
 * low-group and high-group tone are combined to look up the digit, subject to a magnitude
 * threshold and a "twist" check (the two tones must be reasonably balanced in level — real DTMF
 * never has one group wildly louder than the other, which rejects most voice/noise false
 * positives).
 */
class GoertzelDtmf(
    val sampleRate: Int = 8000,
    val windowMs: Int = 40
) {
    val windowSize: Int = sampleRate * windowMs / 1000

    companion object {
        val LOW_FREQS = doubleArrayOf(697.0, 770.0, 852.0, 941.0)
        val HIGH_FREQS = doubleArrayOf(1209.0, 1336.0, 1477.0, 1633.0)
        val DIGIT_GRID = arrayOf(
            charArrayOf('1', '2', '3', 'A'),
            charArrayOf('4', '5', '6', 'B'),
            charArrayOf('7', '8', '9', 'C'),
            charArrayOf('*', '0', '#', 'D')
        )

        /** Maps a UI/config sensitivity in [0,1] (higher = more sensitive) to a magnitude threshold. */
        fun thresholdFor(sensitivity: Double): Double {
            val clamped = sensitivity.coerceIn(0.0, 1.0)
            // sensitivity 0 -> strict threshold 0.35, sensitivity 1 -> lenient threshold 0.06
            return 0.35 - clamped * 0.29
        }
    }

    data class Detection(val digit: Char, val lowMagnitude: Double, val highMagnitude: Double, val twistDb: Double)

    /**
     * Runs Goertzel over [samples] (must have exactly [windowSize] samples) for [targetFreq],
     * returning a magnitude normalized to roughly [0,1] for a full-scale 16-bit tone.
     */
    fun magnitudeAt(samples: ShortArray, targetFreq: Double): Double {
        val k = (0.5 + (windowSize * targetFreq) / sampleRate).toInt()
        val omega = (2.0 * PI * k) / windowSize
        val cosine = cos(omega)
        val coeff = 2.0 * cosine
        var q1 = 0.0
        var q2 = 0.0
        for (sample in samples) {
            val q0 = coeff * q1 - q2 + sample.toDouble()
            q2 = q1
            q1 = q0
        }
        val real = q1 - q2 * cosine
        val imag = q2 * sin(omega)
        val magnitude = sqrt(real * real + imag * imag)
        // Normalize: for a full-scale sinusoid of N samples, Goertzel magnitude is ~N/2 * amplitude.
        return magnitude / (windowSize * Short.MAX_VALUE / 2.0)
    }

    /**
     * Analyzes one window of [samples] and returns the detected digit, or null if no tone (or an
     * ambiguous/too-quiet/too-twisted signal) is present. [minMagnitude] is the per-tone
     * threshold below which a frequency bin is considered silent; derive it from user-facing
     * sensitivity via [thresholdFor]. [maxTwistDb] rejects tone pairs where one group is far
     * louder than the other (real handsets keep twist under ~8dB).
     */
    fun detect(samples: ShortArray, minMagnitude: Double = thresholdFor(0.5), maxTwistDb: Double = 8.0): Detection? {
        require(samples.size == windowSize) { "Expected $windowSize samples, got ${samples.size}" }

        val lowMags = LOW_FREQS.map { magnitudeAt(samples, it) }
        val highMags = HIGH_FREQS.map { magnitudeAt(samples, it) }

        val lowIdx = lowMags.indices.maxByOrNull { lowMags[it] } ?: return null
        val highIdx = highMags.indices.maxByOrNull { highMags[it] } ?: return null
        val lowMag = lowMags[lowIdx]
        val highMag = highMags[highIdx]

        if (lowMag < minMagnitude || highMag < minMagnitude) return null

        // Harmonic/false-positive rejection: the winning bin must clearly beat the runner-up in its own group.
        val lowRunnerUp = lowMags.filterIndexed { i, _ -> i != lowIdx }.maxOrNull() ?: 0.0
        val highRunnerUp = highMags.filterIndexed { i, _ -> i != highIdx }.maxOrNull() ?: 0.0
        if (lowRunnerUp > lowMag * 0.65 || highRunnerUp > highMag * 0.65) return null

        val twistDb = 20.0 * log10(max(lowMag, highMag) / min(lowMag, highMag))
        if (twistDb > maxTwistDb) return null

        return Detection(DIGIT_GRID[lowIdx][highIdx], lowMag, highMag, twistDb)
    }
}
