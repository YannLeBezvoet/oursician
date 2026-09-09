package com.oursician.app.tablature.playback

import com.oursician.app.tablature.Beat
import com.oursician.app.tablature.FretPosition
import com.oursician.app.tablature.Measure
import com.oursician.app.tablature.NoteDuration
import com.oursician.app.tablature.STANDARD_TUNING
import com.oursician.app.tablature.Tablature
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabAudioRendererTest {

    @Test
    fun `a rest still gets an entry, with empty pcm, to keep the timeline index-aligned with the beat list`() {
        val timeline = TabAudioRenderer.renderTimeline(tablatureOf(Beat(NoteDuration.QUARTER, emptyList())))

        assertEquals(1, timeline.size)
        assertTrue(timeline[0].pcm.isEmpty())
    }

    @Test
    fun `a single note is not silent`() {
        val timeline = TabAudioRenderer.renderTimeline(
            tablatureOf(Beat(NoteDuration.QUARTER, listOf(FretPosition(6, 0)))),
        )

        assertEquals(1, timeline.size)
        assertTrue(timeline[0].pcm.any { it != 0.toShort() })
    }

    @Test
    fun `a chord is not silent`() {
        val timeline = TabAudioRenderer.renderTimeline(
            tablatureOf(Beat(NoteDuration.QUARTER, listOf(FretPosition(6, 0), FretPosition(5, 2)))),
        )

        assertEquals(1, timeline.size)
        assertTrue(timeline[0].pcm.any { it != 0.toShort() })
    }

    @Test
    fun `each beat's musical start time accumulates the durations of the beats before it, rests included`() {
        val tablature = tablatureOf(
            Beat(NoteDuration.QUARTER, listOf(FretPosition(6, 0))),
            Beat(NoteDuration.QUARTER, emptyList()),
            Beat(NoteDuration.HALF, listOf(FretPosition(6, 2))),
        )

        val timeline = TabAudioRenderer.renderTimeline(tablature)

        val secondsPerQuarterNote = 60f / tablature.tempoBpm
        assertEquals(3, timeline.size)
        assertEquals(0f, timeline[0].musicalStartSeconds, 1e-6f)
        assertEquals(1f * secondsPerQuarterNote, timeline[1].musicalStartSeconds, 1e-6f)
        assertEquals(2f * secondsPerQuarterNote, timeline[2].musicalStartSeconds, 1e-6f)
    }

    @Test
    fun `a beat's sample count matches its duration`() {
        val tablature = tablatureOf(Beat(NoteDuration.HALF, listOf(FretPosition(6, 0))))

        val timeline = TabAudioRenderer.renderTimeline(tablature)

        val secondsPerQuarterNote = 60f / tablature.tempoBpm
        val expectedSamples = (2f * secondsPerQuarterNote * TabAudioRenderer.SAMPLE_RATE_HZ).roundToInt()
        assertEquals(expectedSamples, timeline[0].pcm.size)
    }

    private fun tablatureOf(vararg beats: Beat): Tablature = Tablature(
        title = "Test",
        tuning = STANDARD_TUNING,
        tempoBpm = 120,
        measures = listOf(Measure(beats.toList())),
    )
}
