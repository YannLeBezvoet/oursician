package com.oursician.app.tablature.library

import android.content.Context
import android.net.Uri
import com.oursician.app.R
import com.oursician.app.tablature.Tablature
import com.oursician.app.tablature.musicxml.MusicXmlParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** One tablature in the user's library: the built-in demo, or a file the user imported. */
data class Song(val id: String, val title: String, val isBuiltIn: Boolean = false)

private val DEMO_SONG = Song(id = "demo", title = "Gamme de démonstration", isBuiltIn = true)

/**
 * Stores imported MusicXML files in the app's private storage (`filesDir/tablatures/<id>`),
 * alongside a small JSON index of their metadata. Files are copied in at import time rather than
 * keeping the source `content://` URI, so a song stays readable even if the app that provided it
 * (Drive, a file manager...) later revokes access or the file moves.
 */
class SongRepository(context: Context) {
    private val context = context.applicationContext
    private val songsDir = File(this.context.filesDir, "tablatures").apply { mkdirs() }
    private val indexFile = File(songsDir, "index.json")

    fun listSongs(): List<Song> = listOf(DEMO_SONG) + readIndex()

    fun loadTablature(song: Song): Tablature =
        if (song.isBuiltIn) {
            MusicXmlParser.parse(context.resources.openRawResource(R.raw.demo_tablature))
        } else {
            MusicXmlParser.parse(FileInputStream(songFile(song.id)))
        }

    /** Copies and parses [uri] before writing anything: an invalid file is rejected, not persisted. */
    fun importSong(uri: Uri): Song {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Impossible d'ouvrir le fichier sélectionné")
        val tablature = MusicXmlParser.parse(ByteArrayInputStream(bytes))

        val id = UUID.randomUUID().toString()
        songFile(id).writeBytes(bytes)
        val song = Song(id = id, title = tablature.title)
        writeIndex(readIndex() + song)
        return song
    }

    fun deleteSong(song: Song) {
        require(!song.isBuiltIn) { "Le morceau de démonstration ne peut pas être supprimé" }
        songFile(song.id).delete()
        writeIndex(readIndex().filterNot { it.id == song.id })
    }

    private fun songFile(id: String) = File(songsDir, id)

    private fun readIndex(): List<Song> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(indexFile.readText())
            (0 until array.length()).map { i ->
                val entry = array.getJSONObject(i)
                Song(id = entry.getString("id"), title = entry.getString("title"))
            }
        }.getOrDefault(emptyList())
    }

    private fun writeIndex(songs: List<Song>) {
        val array = JSONArray()
        songs.forEach { song ->
            array.put(JSONObject().put("id", song.id).put("title", song.title))
        }
        indexFile.writeText(array.toString())
    }
}
