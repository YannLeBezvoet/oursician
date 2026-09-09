package com.oursician.app.tablature.playback

import com.oursician.app.tablature.Tablature
import com.oursician.app.tablature.midiNoteAt
import com.oursician.app.tablature.quarterNoteMultiple
import com.oursician.app.tuner.midiNoteToFrequency
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One beat's synthesized audio, stamped with the musical time (seconds from the start of the
 * piece, derived purely from tempo — no on-screen pixel math involved) it starts at. A rest still
 * gets an entry (with an empty [pcm]) rather than being dropped, so this list stays index-aligned,
 * one-to-one, with `tablature.measures.flatMap { it.beats }` — a caller that schedules triggers
 * off its own on-screen layout of that same flattened beat list (e.g. to account for the visual
 * gap between measures, which isn't musical time) can zip the two by index.
 */
data class TimedBeat(val musicalStartSeconds: Float, val pcm: ShortArray)

/**
 * Renders a [Tablature] into a per-beat timeline instead of one continuous buffer: each beat's
 * notes (a chord plays simultaneously) are synthesized with [KarplusStrongSynthesizer] and
 * normalized on their own, then stamped with their musical start time. A caller can then trigger
 * each beat's sound individually — e.g. exactly when it crosses the scrolling staff's playhead —
 * instead of pre-mixing the whole song and hoping one long-running playback stays in lockstep with
 * a separately animated scroll. It's also the same per-note timing a future hit-judging mode
 * (comparing a detected pluck's timestamp to a note's target time) will need.
 */
object TabAudioRenderer {

    const val SAMPLE_RATE_HZ = 44100
    private val EMPTY_PCM = ShortArray(0)

    fun renderTimeline(tablature: Tablature): List<TimedBeat> {
        val secondsPerQuarterNote = 60f / tablature.tempoBpm
        val beats = tablature.measures.flatMap { it.beats }

        var musicalSeconds = 0f
        val timeline = ArrayList<TimedBeat>(beats.size)
        beats.forEach { beat ->
            val durationSeconds = beat.duration.quarterNoteMultiple * secondsPerQuarterNote
            val pcm = if (beat.positions.isEmpty()) {
                EMPTY_PCM
            } else {
                val mix = FloatArray(samplesFor(durationSeconds))
                beat.positions.forEach { position ->
                    val frequency = midiNoteToFrequency(tablature.tuning.midiNoteAt(position))
                    val noteSamples = KarplusStrongSynthesizer.pluck(frequency, durationSeconds, SAMPLE_RATE_HZ)
                    for (j in noteSamples.indices) {
                        if (j < mix.size) mix[j] += noteSamples[j]
                    }
                }
                mix.toNormalizedPcm16()
            }
            timeline += TimedBeat(musicalSeconds, pcm)
            musicalSeconds += durationSeconds
        }
        return timeline
    }

    private fun samplesFor(durationSeconds: Float): Int = (durationSeconds * SAMPLE_RATE_HZ).roundToInt().coerceAtLeast(0)

    private fun FloatArray.toNormalizedPcm16(): ShortArray {
        val peak = maxOfOrNull { abs(it) } ?: 0f
        val scale = if (peak > 1f) 1f / peak else 1f
        return ShortArray(size) { i ->
            (this[i] * scale * Short.MAX_VALUE)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }
}
