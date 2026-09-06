package com.oursician.app.tuner

import kotlin.math.ln
import kotlin.math.roundToInt

private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
private const val A4_FREQUENCY = 440.0
private const val A4_MIDI_NOTE = 69.0

/** A recognized pitch, expressed both as raw frequency and as a note name + tuning offset. */
data class DetectedNote(
    val frequency: Float,
    val name: String,
    val octave: Int,
    /** Deviation from the nearest equal-tempered note, in cents. Negative = flat, positive = sharp. */
    val cents: Double,
)

/** One of the six standard guitar strings, used as a tuning reference. */
data class GuitarString(val label: String, val frequency: Float)

val STANDARD_GUITAR_TUNING = listOf(
    GuitarString("E2", 82.41f),
    GuitarString("A2", 110.00f),
    GuitarString("D3", 146.83f),
    GuitarString("G3", 196.00f),
    GuitarString("B3", 246.94f),
    GuitarString("E4", 329.63f),
)

/** Converts a raw frequency (Hz) into the nearest equal-tempered note and its tuning offset. */
fun frequencyToNote(frequency: Float): DetectedNote {
    require(frequency > 0f) { "frequency must be positive" }

    val exactMidiNote = A4_MIDI_NOTE + 12.0 * (ln(frequency / A4_FREQUENCY) / ln(2.0))
    val nearestMidiNote = exactMidiNote.roundToInt()
    val cents = (exactMidiNote - nearestMidiNote) * 100.0

    val noteIndex = ((nearestMidiNote % 12) + 12) % 12
    val octave = nearestMidiNote / 12 - 1

    return DetectedNote(
        frequency = frequency,
        name = NOTE_NAMES[noteIndex],
        octave = octave,
        cents = cents,
    )
}

/** Finds the closest standard guitar string to a detected frequency. */
fun closestGuitarString(frequency: Float): GuitarString =
    STANDARD_GUITAR_TUNING.minBy { kotlin.math.abs(ln((it.frequency / frequency).toDouble())) }
