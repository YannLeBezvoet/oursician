package com.oursician.app.tablature

import com.oursician.app.tuner.frequencyToNote
import com.oursician.app.tuner.midiNoteToFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TabModelTest {

    @Test
    fun `standard tuning has six strings numbered 1 to 6`() {
        assertEquals(6, STANDARD_TUNING.strings.size)
        assertEquals((1..6).toList(), STANDARD_TUNING.strings.map { it.stringNumber }.sorted())
    }

    @Test
    fun `standard tuning open strings match standard guitar pitches`() {
        val expected = mapOf(
            1 to ("E" to 4), // high E
            2 to ("B" to 3),
            3 to ("G" to 3),
            4 to ("D" to 3),
            5 to ("A" to 2),
            6 to ("E" to 2), // low E
        )

        expected.forEach { (stringNumber, nameAndOctave) ->
            val string = STANDARD_TUNING.strings.first { it.stringNumber == stringNumber }
            val note = frequencyToNote(midiNoteToFrequency(string.openStringMidiNote))
            assertEquals("string $stringNumber name", nameAndOctave.first, note.name)
            assertEquals("string $stringNumber octave", nameAndOctave.second, note.octave)
        }
    }

    @Test
    fun `fretting a string offsets the open string midi note`() {
        val openStringMidiNote = STANDARD_TUNING.midiNoteAt(FretPosition(stringNumber = 6, fret = 0))
        val thirdFretMidiNote = STANDARD_TUNING.midiNoteAt(FretPosition(stringNumber = 6, fret = 3))
        assertEquals(openStringMidiNote + 3, thirdFretMidiNote)
    }

    @Test
    fun `negative fret is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            FretPosition(stringNumber = 1, fret = -1)
        }
    }

    @Test
    fun `unknown string number is rejected`() {
        assertThrows(IllegalStateException::class.java) {
            STANDARD_TUNING.midiNoteAt(FretPosition(stringNumber = 7, fret = 0))
        }
    }
}
