package com.oursician.app.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteUtilsTest {

    @Test
    fun `440 Hz is A4 with no cents deviation`() {
        val note = frequencyToNote(440f)
        assertEquals("A", note.name)
        assertEquals(4, note.octave)
        assertEquals(0.0, note.cents, 0.5)
    }

    @Test
    fun `E2 guitar string frequency resolves to E2`() {
        val note = frequencyToNote(82.41f)
        assertEquals("E", note.name)
        assertEquals(2, note.octave)
        assertEquals(0.0, note.cents, 1.0)
    }

    @Test
    fun `a slightly sharp A4 reports positive cents`() {
        val note = frequencyToNote(444f)
        assertEquals("A", note.name)
        assertTrue("expected positive cents for a sharp note, got ${note.cents}", note.cents > 0)
    }

    @Test
    fun `a slightly flat A4 reports negative cents`() {
        val note = frequencyToNote(436f)
        assertEquals("A", note.name)
        assertTrue("expected negative cents for a flat note, got ${note.cents}", note.cents < 0)
    }

    @Test
    fun `closest guitar string picks the nearest reference`() {
        assertEquals("E2", closestGuitarString(83f).label)
        assertEquals("A2", closestGuitarString(112f).label)
        assertEquals("D3", closestGuitarString(145f).label)
        assertEquals("G3", closestGuitarString(198f).label)
        assertEquals("B3", closestGuitarString(244f).label)
        assertEquals("E4", closestGuitarString(333f).label)
    }
}
