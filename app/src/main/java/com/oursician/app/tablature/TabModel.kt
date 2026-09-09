package com.oursician.app.tablature

import com.oursician.app.tuner.STANDARD_GUITAR_TUNING
import com.oursician.app.tuner.frequencyToNearestMidiNote

/**
 * One string of a tuning. String numbers follow standard tab notation: 1 is the
 * highest-pitched (thinnest) string, matching the top line of a tab staff.
 */
data class TuningString(val stringNumber: Int, val openStringMidiNote: Int)

data class Tuning(val name: String, val strings: List<TuningString>)

val STANDARD_TUNING = Tuning(
    name = "Standard (EADGBE)",
    // STANDARD_GUITAR_TUNING is ordered low-to-high (E2..E4); tab strings are numbered
    // high-to-low (1..6), so the list is reversed before assigning string numbers.
    strings = STANDARD_GUITAR_TUNING
        .reversed()
        .mapIndexed { index, string ->
            TuningString(
                stringNumber = index + 1,
                openStringMidiNote = frequencyToNearestMidiNote(string.frequency),
            )
        },
)

/** A single fretted position on the fretboard. Fret 0 means an open (unfretted) string. */
data class FretPosition(val stringNumber: Int, val fret: Int) {
    init {
        require(fret >= 0) { "fret must be >= 0, was $fret" }
    }
}

/** Standard note durations, expressed as a fraction of a whole note. */
enum class NoteDuration(val fractionOfWhole: Double) {
    WHOLE(1.0),
    HALF(0.5),
    QUARTER(0.25),
    EIGHTH(0.125),
    SIXTEENTH(0.0625),
}

/** How many quarter notes [this] is worth — the shared unit for both column width and playback duration. */
val NoteDuration.quarterNoteMultiple: Float
    get() = (fractionOfWhole / NoteDuration.QUARTER.fractionOfWhole).toFloat()

/** One beat in a measure: a rest (empty [positions]) or one or more simultaneous fretted notes (a chord). */
data class Beat(val duration: NoteDuration, val positions: List<FretPosition> = emptyList())

data class Measure(val beats: List<Beat>)

/** [tempoBpm] is quarter notes per minute — it drives auto-scroll playback speed. */
data class Tablature(val title: String, val tuning: Tuning, val tempoBpm: Int, val measures: List<Measure>)

/** Resolves the MIDI note actually sounded by [position] on this tuning. */
fun Tuning.midiNoteAt(position: FretPosition): Int {
    val string = strings.find { it.stringNumber == position.stringNumber }
        ?: error("Unknown string number ${position.stringNumber} for tuning \"$name\" (has ${strings.size} strings)")
    return string.openStringMidiNote + position.fret
}
