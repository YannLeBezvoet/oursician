package com.oursician.app.tuner

import kotlin.math.sqrt

/** Estimates the fundamental frequency of a mono PCM buffer, or null if none is found. */
interface PitchDetector {
    fun detectPitch(buffer: FloatArray, sampleRate: Int): Float?
}

/**
 * Pitch detector based on the YIN algorithm (de Cheveigné & Kawahara, 2002):
 * a difference function, its cumulative mean normalization, an absolute
 * threshold search, and parabolic interpolation for sub-sample precision.
 */
class YinPitchDetector(
    private val threshold: Double = 0.2,
    private val minFrequencyHz: Double = 70.0,
    private val maxFrequencyHz: Double = 400.0,
    private val silenceRmsThreshold: Float = 0.005f,
) : PitchDetector {

    override fun detectPitch(buffer: FloatArray, sampleRate: Int): Float? {
        if (rms(buffer) < silenceRmsThreshold) return null

        val maxTau = minOf(buffer.size / 2, (sampleRate / minFrequencyHz).toInt())
        val minTau = maxOf(2, (sampleRate / maxFrequencyHz).toInt())
        if (maxTau <= minTau) return null

        val integrationWindow = buffer.size - maxTau
        if (integrationWindow <= 0) return null

        val cmnd = cumulativeMeanNormalizedDifference(buffer, maxTau, integrationWindow)
        val tauEstimate = findFirstDip(cmnd, minTau, maxTau) ?: return null

        val refinedTau = parabolicInterpolation(cmnd, tauEstimate)
        if (refinedTau <= 0.0) return null

        return (sampleRate / refinedTau).toFloat()
    }

    private fun rms(buffer: FloatArray): Float {
        var sum = 0.0
        for (sample in buffer) sum += sample * sample
        return sqrt(sum / buffer.size).toFloat()
    }

    private fun cumulativeMeanNormalizedDifference(
        buffer: FloatArray,
        maxTau: Int,
        integrationWindow: Int,
    ): DoubleArray {
        val diff = DoubleArray(maxTau + 1)
        for (tau in 1..maxTau) {
            var sum = 0.0
            for (j in 0 until integrationWindow) {
                val delta = buffer[j] - buffer[j + tau]
                sum += delta * delta
            }
            diff[tau] = sum
        }

        val cmnd = DoubleArray(maxTau + 1)
        cmnd[0] = 1.0
        var runningSum = 0.0
        for (tau in 1..maxTau) {
            runningSum += diff[tau]
            cmnd[tau] = if (runningSum == 0.0) 1.0 else diff[tau] * tau / runningSum
        }
        return cmnd
    }

    /** Finds the first local minimum below [threshold], per the YIN "absolute threshold" step. */
    private fun findFirstDip(cmnd: DoubleArray, minTau: Int, maxTau: Int): Int? {
        var tau = minTau
        while (tau <= maxTau) {
            if (cmnd[tau] < threshold) {
                while (tau + 1 <= maxTau && cmnd[tau + 1] < cmnd[tau]) {
                    tau++
                }
                return tau
            }
            tau++
        }
        return null
    }

    private fun parabolicInterpolation(cmnd: DoubleArray, tau: Int): Double {
        val x0 = if (tau < 1) tau else tau - 1
        val x2 = if (tau + 1 < cmnd.size) tau + 1 else tau
        if (x0 == tau || x2 == tau) return tau.toDouble()

        val s0 = cmnd[x0]
        val s1 = cmnd[tau]
        val s2 = cmnd[x2]
        val denominator = 2.0 * s1 - s2 - s0
        return if (denominator == 0.0) tau.toDouble() else tau + (s2 - s0) / (2.0 * denominator)
    }
}
