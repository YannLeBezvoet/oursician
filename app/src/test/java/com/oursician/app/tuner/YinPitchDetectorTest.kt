package com.oursician.app.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class YinPitchDetectorTest {

    private val detector = YinPitchDetector()
    private val sampleRate = 44100

    @Test
    fun `detects each standard guitar string frequency within a few cents`() {
        for (string in STANDARD_GUITAR_TUNING) {
            val buffer = sineWave(string.frequency, sampleRate, sampleCount = 4096)

            val detected = detector.detectPitch(buffer, sampleRate)

            requireNotNull(detected) { "expected a pitch for ${string.label}" }
            val toleranceHz = string.frequency * 0.02f // ~35 cents, generous for a discrete FFT-free estimate
            assertEquals(
                "detected pitch for ${string.label} was off by more than tolerance",
                string.frequency.toDouble(),
                detected.toDouble(),
                toleranceHz.toDouble(),
            )
        }
    }

    @Test
    fun `silence produces no pitch`() {
        val silence = FloatArray(4096)
        assertNull(detector.detectPitch(silence, sampleRate))
    }

    @Test
    fun `low-amplitude noise below the silence gate produces no pitch`() {
        val tinyNoise = FloatArray(4096) { i -> if (i % 2 == 0) 0.001f else -0.001f }
        assertNull(detector.detectPitch(tinyNoise, sampleRate))
    }

    private fun sineWave(frequency: Float, sampleRate: Int, sampleCount: Int): FloatArray =
        FloatArray(sampleCount) { i ->
            sin(2.0 * PI * frequency * i / sampleRate).toFloat()
        }
}
