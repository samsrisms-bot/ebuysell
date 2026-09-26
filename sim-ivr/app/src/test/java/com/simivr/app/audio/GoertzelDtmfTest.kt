package com.simivr.app.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.sin
import kotlin.math.PI
import kotlin.random.Random

class GoertzelDtmfTest {

    private val detector = GoertzelDtmf(sampleRate = 8000, windowMs = 40)

    private fun digitToFreqs(digit: Char): Pair<Double, Double> {
        for (row in GoertzelDtmf.DIGIT_GRID.indices) {
            for (col in GoertzelDtmf.DIGIT_GRID[row].indices) {
                if (GoertzelDtmf.DIGIT_GRID[row][col] == digit) {
                    return GoertzelDtmf.LOW_FREQS[row] to GoertzelDtmf.HIGH_FREQS[col]
                }
            }
        }
        error("Unknown digit $digit")
    }

    private fun synthesizeTone(digit: Char, windowSize: Int, sampleRate: Int, amplitude: Double = 0.9, noise: Double = 0.0): ShortArray {
        val (low, high) = digitToFreqs(digit)
        return ShortArray(windowSize) { i ->
            val t = i.toDouble() / sampleRate
            var sample = amplitude * 0.5 * sin(2 * PI * low * t) + amplitude * 0.5 * sin(2 * PI * high * t)
            if (noise > 0.0) sample += noise * (Random.nextDouble() * 2 - 1)
            (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    @Test
    fun `detects every DTMF digit cleanly`() {
        val allDigits = GoertzelDtmf.DIGIT_GRID.flatMap { it.toList() }
        for (digit in allDigits) {
            val samples = synthesizeTone(digit, detector.windowSize, detector.sampleRate)
            val detection = detector.detect(samples)
            assertThat(detection).isNotNull()
            assertThat(detection!!.digit).isEqualTo(digit)
        }
    }

    @Test
    fun `rejects silence`() {
        val silence = ShortArray(detector.windowSize) { 0 }
        assertThat(detector.detect(silence)).isNull()
    }

    @Test
    fun `rejects pure noise`() {
        val random = Random(42)
        val noise = ShortArray(detector.windowSize) { (random.nextDouble() * 2 - 1).let { (it * 1500).toInt().toShort() } }
        assertThat(detector.detect(noise)).isNull()
    }

    @Test
    fun `still detects digit with moderate background noise`() {
        val samples = synthesizeTone('5', detector.windowSize, detector.sampleRate, amplitude = 0.9, noise = 0.05)
        val detection = detector.detect(samples)
        assertThat(detection).isNotNull()
        assertThat(detection!!.digit).isEqualTo('5')
    }

    @Test
    fun `rejects a single tone with no partner frequency as not a valid DTMF pair`() {
        // A quiet 500Hz tone sits far from every DTMF bin (nearest is 697Hz), so spectral leakage
        // into any bin stays well under the detection threshold.
        val windowSize = detector.windowSize
        val samples = ShortArray(windowSize) { i ->
            val t = i.toDouble() / detector.sampleRate
            (0.3 * sin(2 * PI * 500.0 * t) * Short.MAX_VALUE).toInt().toShort()
        }
        assertThat(detector.detect(samples)).isNull()
    }

    @Test
    fun `sensitivity threshold gets more lenient as sensitivity increases`() {
        val strict = GoertzelDtmf.thresholdFor(0.0)
        val lenient = GoertzelDtmf.thresholdFor(1.0)
        assertThat(lenient).isLessThan(strict)
    }

    @Test
    fun `stream detector debounces and requires tone dropout before repeating same digit`() {
        val streamDetector = DtmfStreamDetector(sensitivity = 0.5, requiredConsecutiveWindows = 2)
        val toneWindow = synthesizeTone('7', streamDetector.windowSize, 8000)
        val silenceWindow = ShortArray(streamDetector.windowSize) { 0 }

        assertThat(streamDetector.processWindow(toneWindow)).isNull() // 1st window: not yet confirmed
        assertThat(streamDetector.processWindow(toneWindow)).isEqualTo('7') // 2nd consecutive window: confirmed
        assertThat(streamDetector.processWindow(toneWindow)).isNull() // still held, no duplicate event

        assertThat(streamDetector.processWindow(silenceWindow)).isNull()

        assertThat(streamDetector.processWindow(toneWindow)).isNull()
        assertThat(streamDetector.processWindow(toneWindow)).isEqualTo('7') // pressed again after release
    }
}
