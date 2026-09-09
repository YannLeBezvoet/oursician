package com.oursician.app.tablature.playback

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KarplusStrongSynthesizerTest {

    @Test
    fun `output length matches requested duration`() {
        val samples = KarplusStrongSynthesizer.pluck(frequencyHz = 220f, durationSeconds = 0.5f, sampleRate = 44100)
        assertEquals(22050, samples.size)
    }

    @Test
    fun `zero duration produces an empty buffer`() {
        val samples = KarplusStrongSynthesizer.pluck(frequencyHz = 440f, durationSeconds = 0f, sampleRate = 44100)
        assertEquals(0, samples.size)
    }

    @Test
    fun `produces actual sound, not silence`() {
        val samples = KarplusStrongSynthesizer.pluck(frequencyHz = 220f, durationSeconds = 0.5f, sampleRate = 44100)
        assertTrue(samples.any { abs(it) > 0.01f })
    }

    @Test
    fun `amplitude decays over time, like a plucked string`() {
        val samples = KarplusStrongSynthesizer.pluck(frequencyHz = 110f, durationSeconds = 1f, sampleRate = 44100, decay = 0.996f)
        val half = samples.size / 2
        val firstHalfEnergy = samples.take(half).sumOf { (it * it).toDouble() }
        val secondHalfEnergy = samples.drop(half).sumOf { (it * it).toDouble() }
        assertTrue(
            "expected decay: first-half energy $firstHalfEnergy should exceed second-half $secondHalfEnergy",
            firstHalfEnergy > secondHalfEnergy,
        )
    }
}
