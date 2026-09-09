package com.oursician.app.tablature.playback

import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Classic Karplus-Strong plucked-string synthesis: a noise burst run through a short circular
 * delay line, averaged and decayed on every pass, which settles into a decaying tone at
 * [frequencyHz] — much closer to a plucked guitar string than a raw sine tone.
 */
object KarplusStrongSynthesizer {

    fun pluck(frequencyHz: Float, durationSeconds: Float, sampleRate: Int, decay: Float = 0.996f): FloatArray {
        require(frequencyHz > 0f) { "frequencyHz must be positive" }
        require(durationSeconds >= 0f) { "durationSeconds must not be negative" }

        val delayLineLength = (sampleRate / frequencyHz).roundToInt().coerceAtLeast(2)
        val ringBuffer = FloatArray(delayLineLength) { Random.nextFloat() * 2f - 1f }
        val sampleCount = (durationSeconds * sampleRate).roundToInt().coerceAtLeast(0)
        val output = FloatArray(sampleCount)

        var index = 0
        for (i in 0 until sampleCount) {
            val current = ringBuffer[index]
            output[i] = current
            val next = ringBuffer[(index + 1) % delayLineLength]
            ringBuffer[index] = decay * 0.5f * (current + next)
            index = (index + 1) % delayLineLength
        }
        return output
    }
}
