package com.oursician.app.tablature.musicxml

import com.oursician.app.tablature.Beat
import com.oursician.app.tablature.FretPosition
import com.oursician.app.tablature.Measure
import com.oursician.app.tablature.NoteDuration
import com.oursician.app.tablature.STANDARD_TUNING
import com.oursician.app.tablature.Tablature
import com.oursician.app.tablature.Tuning
import com.oursician.app.tablature.TuningString
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** Thrown when a MusicXML file uses a feature Oursician doesn't support, or is missing data a tab needs. */
class MusicXmlParseException(message: String) : IllegalArgumentException(message)

private val NOTE_DURATIONS_BY_MUSICXML_TYPE = mapOf(
    "whole" to NoteDuration.WHOLE,
    "half" to NoteDuration.HALF,
    "quarter" to NoteDuration.QUARTER,
    "eighth" to NoteDuration.EIGHTH,
    "16th" to NoteDuration.SIXTEENTH,
)

private val PITCH_STEP_SEMITONES = mapOf(
    "C" to 0, "D" to 2, "E" to 4, "F" to 5, "G" to 7, "A" to 9, "B" to 11,
)

/**
 * Reads a MusicXML file (plain `.musicxml` or zipped `.mxl`) into Oursician's [Tablature] model.
 * Only the subset of MusicXML a guitar tab needs is supported (single voice, string/fret
 * notations, whole/half/quarter/eighth/16th durations, no dotted notes or tuplets) — anything
 * else fails with a [MusicXmlParseException] rather than silently producing a wrong tab.
 */
object MusicXmlParser {

    fun parse(input: InputStream): Tablature {
        val bytes = input.use { it.readBytes() }
        val document = if (isZipArchive(bytes)) extractMusicXmlDocument(bytes) else parseXmlDocument(bytes)
        document.documentElement.normalize()
        return document.toTablature()
    }

    private fun isZipArchive(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    /** Unwraps an `.mxl` archive: reads its container.xml to find the actual MusicXML entry inside the zip. */
    private fun extractMusicXmlDocument(zipBytes: ByteArray): Document {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        val containerBytes = entries["META-INF/container.xml"]
            ?: throw MusicXmlParseException("Archive .mxl invalide : META-INF/container.xml introuvable")
        val rootFilePath = parseXmlDocument(containerBytes)
            .getElementsByTagName("rootfile")
            .item(0)?.let { it as Element }
            ?.getAttribute("full-path")
            ?.takeIf { it.isNotBlank() }
            ?: throw MusicXmlParseException("Archive .mxl invalide : balise <rootfile full-path=\"…\"> introuvable")
        val scoreBytes = entries[rootFilePath]
            ?: throw MusicXmlParseException("Archive .mxl invalide : fichier référencé \"$rootFilePath\" introuvable")
        return parseXmlDocument(scoreBytes)
    }

    private fun parseXmlDocument(bytes: ByteArray): Document =
        newSecureDocumentBuilder().parse(ByteArrayInputStream(bytes))

    /**
     * A fresh [DocumentBuilder] that still accepts the DOCTYPE declaration real MusicXML exports include
     * (e.g. MuseScore references the official partwise DTD), but never resolves it or any external entity —
     * only that resolution step is the actual XXE/SSRF risk on a file coming from outside the app.
     *
     * The `setFeature` calls below are best-effort: not every JAXP provider honors these Xerces/SAX
     * feature names (Android's built-in one doesn't), so the actual guarantee comes from the
     * [DocumentBuilder.setEntityResolver] override, which is standard JAXP and substitutes empty
     * content for *any* external entity/DTD instead of ever opening a real connection — this is what
     * stopped a bundled MusicXML file with a `<!DOCTYPE ... SYSTEM "http://...">` from trying (and
     * failing, since the app has no INTERNET permission) to fetch it on-device.
     */
    private fun newSecureDocumentBuilder(): DocumentBuilder {
        // Every property/feature setter here is best-effort: Android's built-in JAXP provider is a
        // minimal shim that throws UnsupportedOperationException for some of these (e.g.
        // setXIncludeAware, unconditionally, on-device — caught the hard way, it's simply not called
        // below since XInclude is already off by default) rather than silently ignoring them.
        val factory = DocumentBuilderFactory.newInstance()
        runCatching { factory.isNamespaceAware = false }
        runCatching { factory.isExpandEntityReferences = false }
        runCatching { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        return factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }
    }

    private fun Document.toTablature(): Tablature {
        val part = getElementsByTagName("part").item(0) as? Element
            ?: throw MusicXmlParseException("Aucune balise <part> trouvée dans le fichier MusicXML")
        return Tablature(
            title = findTitle(),
            tuning = findTuning() ?: STANDARD_TUNING,
            tempoBpm = findTempoBpm(),
            measures = part.childElements().filter { it.tagName == "measure" }.map { it.toMeasure() },
        )
    }

    private fun Document.findTitle(): String =
        firstNonBlankText("movement-title") ?: firstNonBlankText("work-title") ?: "Sans titre"

    private fun Document.firstNonBlankText(tagName: String): String? =
        getElementsByTagName(tagName).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun Document.findTempoBpm(): Int {
        val soundElements = getElementsByTagName("sound")
        for (i in 0 until soundElements.length) {
            val tempoAttr = (soundElements.item(i) as Element).getAttribute("tempo")
            if (tempoAttr.isNotBlank()) {
                return tempoAttr.toDoubleOrNull()?.roundToInt()
                    ?: throw MusicXmlParseException("Tempo invalide dans <sound tempo=\"$tempoAttr\">")
            }
        }
        throw MusicXmlParseException(
            "Aucun tempo trouvé (balise <sound tempo=\"…\"/> manquante) : le tempo pilote le défilement, il est obligatoire",
        )
    }

    /** Reads the real tuning from `<staff-details>`/`<staff-tuning>` if present, else null (caller falls back to standard). */
    private fun Document.findTuning(): Tuning? {
        val staffTunings = (getElementsByTagName("staff-details").item(0) as? Element)
            ?.childElements()
            ?.filter { it.tagName == "staff-tuning" }
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        val lineCount = staffTunings.size
        val strings = staffTunings.map { staffTuning ->
            val line = staffTuning.getAttribute("line").toIntOrNull()
                ?: throw MusicXmlParseException("<staff-tuning> sans attribut line valide")
            val step = staffTuning.firstChildText("tuning-step")
                ?: throw MusicXmlParseException("<staff-tuning> sans <tuning-step>")
            val octave = staffTuning.firstChildText("tuning-octave")?.toIntOrNull()
                ?: throw MusicXmlParseException("<staff-tuning> sans <tuning-octave> valide")
            val alter = staffTuning.firstChildText("tuning-alter")?.toDoubleOrNull()?.roundToInt() ?: 0
            // MusicXML numbers staff lines from the bottom; line 1 is the lowest-pitched (highest string number).
            TuningString(
                stringNumber = lineCount + 1 - line,
                openStringMidiNote = pitchToMidiNote(step, octave, alter),
            )
        }.sortedBy { it.stringNumber }

        return Tuning(name = "Accordage du fichier", strings = strings)
    }

    private fun pitchToMidiNote(step: String, octave: Int, alter: Int): Int {
        val semitone = PITCH_STEP_SEMITONES[step.trim().uppercase()]
            ?: throw MusicXmlParseException("Note de tuning-step inconnue : \"$step\"")
        return (octave + 1) * 12 + semitone + alter
    }

    private fun Element.toMeasure(): Measure {
        val beats = mutableListOf<Beat>()
        childElements().forEach { child ->
            when (child.tagName) {
                "backup" -> throw MusicXmlParseException("Partitions multi-voix non supportées (balise <backup> rencontrée)")
                "note" -> {
                    val note = child.toNoteBeatData()
                    if (note.isChord && beats.isNotEmpty()) {
                        val previous = beats.removeAt(beats.lastIndex)
                        beats.add(previous.copy(positions = previous.positions + note.positions))
                    } else {
                        beats.add(Beat(duration = note.duration, positions = note.positions))
                    }
                }
            }
        }
        return Measure(beats = beats)
    }

    private data class NoteBeatData(val duration: NoteDuration, val positions: List<FretPosition>, val isChord: Boolean)

    private fun Element.toNoteBeatData(): NoteBeatData {
        val children = childElements()
        val isChord = children.any { it.tagName == "chord" }
        val isRest = children.any { it.tagName == "rest" }
        if (children.any { it.tagName == "dot" } || children.any { it.tagName == "time-modification" }) {
            throw MusicXmlParseException("Durée de note non supportée (note pointée ou triolet)")
        }

        val typeText = children.firstOrNull { it.tagName == "type" }?.textContent?.trim()
            ?: throw MusicXmlParseException("Note sans balise <type> (durée manquante)")
        val duration = NOTE_DURATIONS_BY_MUSICXML_TYPE[typeText]
            ?: throw MusicXmlParseException("Durée de note non supportée : \"$typeText\"")

        if (isRest) return NoteBeatData(duration, emptyList(), isChord)

        val technical = children.firstOrNull { it.tagName == "notations" }
            ?.childElements()?.firstOrNull { it.tagName == "technical" }
        val stringNumber = technical?.firstChildText("string")?.toIntOrNull()
        val fret = technical?.firstChildText("fret")?.toIntOrNull()
        if (stringNumber == null || fret == null) {
            throw MusicXmlParseException(
                "Ce fichier n'a pas de position de corde/frette pour toutes les notes : c'est " +
                    "probablement une partition en notation standard plutôt qu'une tablature. " +
                    "Réexporte-le avec une portée TAB (MuseScore, Guitar Pro, TuxGuitar...) ou " +
                    "choisis un fichier qui est déjà une vraie tablature.",
            )
        }
        return NoteBeatData(duration, listOf(FretPosition(stringNumber, fret)), isChord)
    }

    private fun Element.childElements(): List<Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

    private fun Element.firstChildText(tagName: String): String? =
        childElements().firstOrNull { it.tagName == tagName }?.textContent?.trim()
}
