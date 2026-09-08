package com.oursician.app.tablature.musicxml

import com.oursician.app.tablature.Beat
import com.oursician.app.tablature.FretPosition
import com.oursician.app.tablature.Measure
import com.oursician.app.tablature.NoteDuration
import com.oursician.app.tablature.STANDARD_TUNING
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MusicXmlParserTest {

    @Test
    fun `parses title tempo standard tuning and measures from plain musicxml`() {
        val tablature = MusicXmlParser.parse(DEMO_SCALE_MUSICXML.byteInputStream())

        assertEquals("Gamme de démonstration", tablature.title)
        assertEquals(90, tablature.tempoBpm)
        assertEquals(STANDARD_TUNING.strings, tablature.tuning.strings)
        assertEquals(EXPECTED_DEMO_SCALE_MEASURES, tablature.measures)
    }

    @Test
    fun `reads alternate tuning from staff-details`() {
        val dropD = musicXmlDocument(
            measuresXml = "<measure number=\"1\">${noteXml(string = 6, fret = 0)}</measure>",
            staffTuningXml = staffTuningXml(listOf("D" to 2, "A" to 2, "D" to 3, "G" to 3, "B" to 3, "E" to 4)),
        )

        val tablature = MusicXmlParser.parse(dropD.byteInputStream())

        val lowString = tablature.tuning.strings.first { it.stringNumber == 6 }
        assertEquals(38, lowString.openStringMidiNote) // D2
    }

    @Test
    fun `falls back to standard tuning when staff-details is absent`() {
        val xml = musicXmlDocument(
            measuresXml = "<measure number=\"1\">${noteXml(string = 6, fret = 0)}</measure>",
            staffTuningXml = null,
        )

        val tablature = MusicXmlParser.parse(xml.byteInputStream())

        assertEquals(STANDARD_TUNING, tablature.tuning)
    }

    @Test
    fun `parses rest as an empty-position beat`() {
        val xml = musicXmlDocument(measuresXml = "<measure number=\"1\">${restXml()}</measure>")

        val tablature = MusicXmlParser.parse(xml.byteInputStream())

        assertEquals(listOf(Measure(listOf(Beat(NoteDuration.QUARTER, emptyList())))), tablature.measures)
    }

    @Test
    fun `parses chord notes into a single beat with multiple positions`() {
        val chordMeasure = "<measure number=\"1\">" +
            noteXml(string = 6, fret = 0) +
            noteXml(string = 5, fret = 2, chord = true) +
            "</measure>"
        val xml = musicXmlDocument(measuresXml = chordMeasure)

        val tablature = MusicXmlParser.parse(xml.byteInputStream())

        val expectedBeat = Beat(NoteDuration.QUARTER, listOf(FretPosition(6, 0), FretPosition(5, 2)))
        assertEquals(listOf(Measure(listOf(expectedBeat))), tablature.measures)
    }

    @Test
    fun `throws when tempo is missing`() {
        val xml = musicXmlDocument(
            measuresXml = "<measure number=\"1\">${noteXml(string = 6, fret = 0)}</measure>",
            tempoBpm = null,
        )

        assertThrows(MusicXmlParseException::class.java) {
            MusicXmlParser.parse(xml.byteInputStream())
        }
    }

    @Test
    fun `throws when a note is missing string or fret notations`() {
        val xml = musicXmlDocument(measuresXml = "<measure number=\"1\">${noteWithoutTabPositionXml()}</measure>")

        assertThrows(MusicXmlParseException::class.java) {
            MusicXmlParser.parse(xml.byteInputStream())
        }
    }

    @Test
    fun `throws on unsupported note duration`() {
        val xml = musicXmlDocument(
            measuresXml = "<measure number=\"1\">${noteXml(string = 6, fret = 0, duration = "32nd")}</measure>",
        )

        assertThrows(MusicXmlParseException::class.java) {
            MusicXmlParser.parse(xml.byteInputStream())
        }
    }

    @Test
    fun `parses a zipped mxl archive identically to the plain xml`() {
        val mxlBytes = zipMusicXml(DEMO_SCALE_MUSICXML)

        val tablature = MusicXmlParser.parse(ByteArrayInputStream(mxlBytes))

        assertEquals("Gamme de démonstration", tablature.title)
        assertEquals(90, tablature.tempoBpm)
        assertEquals(STANDARD_TUNING.strings, tablature.tuning.strings)
        assertEquals(EXPECTED_DEMO_SCALE_MEASURES, tablature.measures)
    }

    @Test
    fun `never resolves the DOCTYPE's external DTD (real exports, eg MuseScore, include one)`() {
        val xml = musicXmlDocument(measuresXml = "<measure number=\"1\">${noteXml(string = 6, fret = 0)}</measure>")
            .replaceFirst(
                "<score-partwise version=\"4.0\">",
                // .invalid is reserved by RFC 2606 to never resolve — proves no DNS lookup is even attempted.
                "<!DOCTYPE score-partwise SYSTEM \"http://example.invalid/partwise.dtd\">" +
                    "<score-partwise version=\"4.0\">",
            )

        val tablature = MusicXmlParser.parse(xml.byteInputStream())

        assertEquals(
            listOf(Measure(listOf(Beat(NoteDuration.QUARTER, listOf(FretPosition(6, 0)))))),
            tablature.measures,
        )
    }
}

private val EXPECTED_DEMO_SCALE_MEASURES = listOf(
    Measure(
        beats = listOf(
            Beat(NoteDuration.QUARTER, listOf(FretPosition(stringNumber = 6, fret = 0))),
            Beat(NoteDuration.QUARTER, listOf(FretPosition(stringNumber = 6, fret = 2))),
            Beat(NoteDuration.QUARTER, listOf(FretPosition(stringNumber = 6, fret = 4))),
            Beat(NoteDuration.QUARTER, listOf(FretPosition(stringNumber = 6, fret = 5))),
        ),
    ),
    Measure(
        beats = listOf(
            Beat(NoteDuration.QUARTER, listOf(FretPosition(stringNumber = 6, fret = 7))),
            Beat(NoteDuration.QUARTER, listOf(FretPosition(stringNumber = 6, fret = 9))),
            Beat(NoteDuration.QUARTER, listOf(FretPosition(stringNumber = 6, fret = 11))),
            Beat(NoteDuration.QUARTER, listOf(FretPosition(stringNumber = 6, fret = 12))),
        ),
    ),
)

private val STANDARD_STAFF_TUNING_XML =
    staffTuningXml(listOf("E" to 2, "A" to 2, "D" to 3, "G" to 3, "B" to 3, "E" to 4))

private val DEMO_SCALE_MUSICXML = musicXmlDocument(
    title = "Gamme de démonstration",
    measuresXml = """
        <measure number="1">
          ${noteXml(string = 6, fret = 0)}
          ${noteXml(string = 6, fret = 2)}
          ${noteXml(string = 6, fret = 4)}
          ${noteXml(string = 6, fret = 5)}
        </measure>
        <measure number="2">
          ${noteXml(string = 6, fret = 7)}
          ${noteXml(string = 6, fret = 9)}
          ${noteXml(string = 6, fret = 11)}
          ${noteXml(string = 6, fret = 12)}
        </measure>
    """.trimIndent(),
)

private fun staffTuningXml(lowToHighLines: List<Pair<String, Int>>): String =
    lowToHighLines.mapIndexed { index, (step, octave) ->
        "<staff-tuning line=\"${index + 1}\"><tuning-step>$step</tuning-step><tuning-octave>$octave</tuning-octave></staff-tuning>"
    }.joinToString("")

private fun noteXml(string: Int, fret: Int, duration: String = "quarter", chord: Boolean = false): String = """
    <note>
      ${if (chord) "<chord/>" else ""}
      <pitch><step>E</step><octave>2</octave></pitch>
      <duration>1</duration>
      <type>$duration</type>
      <notations><technical><string>$string</string><fret>$fret</fret></technical></notations>
    </note>
""".trimIndent()

private fun restXml(duration: String = "quarter"): String = """
    <note>
      <rest/>
      <duration>1</duration>
      <type>$duration</type>
    </note>
""".trimIndent()

private fun noteWithoutTabPositionXml(): String = """
    <note>
      <pitch><step>E</step><octave>2</octave></pitch>
      <duration>1</duration>
      <type>quarter</type>
    </note>
""".trimIndent()

/**
 * Builds a minimal but well-formed score-partwise document around the given `<measure>` block(s).
 * [measuresXml] must start with the opening tag of its first `<measure>` — attributes/tempo are
 * inserted as that measure's first children, the way a real MusicXML export lays them out.
 */
private fun musicXmlDocument(
    measuresXml: String,
    title: String = "Test",
    tempoBpm: Int? = 90,
    staffTuningXml: String? = STANDARD_STAFF_TUNING_XML,
): String {
    val staffDetailsXml = staffTuningXml?.let { "<staff-details><staff-lines>6</staff-lines>$it</staff-details>" } ?: ""
    val soundXml = tempoBpm?.let { "<direction><sound tempo=\"$it\"/></direction>" } ?: ""
    val headerXml = "<attributes><divisions>1</divisions>$staffDetailsXml</attributes>$soundXml"
    val firstMeasureOpenTagEnd = measuresXml.indexOf('>') + 1
    val measuresWithHeader =
        measuresXml.substring(0, firstMeasureOpenTagEnd) + headerXml + measuresXml.substring(firstMeasureOpenTagEnd)

    // Not .trimIndent(): measuresXml already embeds its own (differently indented) multi-line
    // content, which would confuse trimIndent's common-indent detection and leave stray
    // whitespace before the <?xml?> declaration — which XML parsers reject outright.
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
        "<score-partwise version=\"4.0\">" +
        "<work><work-title>$title</work-title></work>" +
        "<part-list><score-part id=\"P1\"><part-name>Guitare</part-name></score-part></part-list>" +
        "<part id=\"P1\">$measuresWithHeader</part>" +
        "</score-partwise>"
}

private fun zipMusicXml(musicXml: String): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry("META-INF/container.xml"))
        zip.write(
            """
                <?xml version="1.0" encoding="UTF-8"?>
                <container>
                  <rootfiles>
                    <rootfile full-path="score.xml"/>
                  </rootfiles>
                </container>
            """.trimIndent().toByteArray(),
        )
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("score.xml"))
        zip.write(musicXml.toByteArray())
        zip.closeEntry()
    }
    return out.toByteArray()
}
