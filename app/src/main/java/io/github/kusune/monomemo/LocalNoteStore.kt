package io.github.kusune.monomemo

import android.content.Context
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Local-first storage for the first vertical slice.
 *
 * The storage boundary is deliberately small. Cloud synchronization will be
 * added above this class rather than mixed into the editor.
 */
class LocalNoteStore(context: Context) {
    data class Snapshot(
        val text: String,
        val selectionStart: Int,
        val selectionEnd: Int,
        val scrollX: Int,
        val scrollY: Int,
    )

    private val notesDirectory = File(context.filesDir, "notes")
    private val noteFile = File(notesDirectory, "main.txt")
    private val temporaryFile = File(notesDirectory, "main.txt.tmp")
    private val preferences = context.getSharedPreferences("editor-state", Context.MODE_PRIVATE)

    fun load(): Snapshot {
        val text = if (noteFile.isFile) {
            try {
                noteFile.readText(StandardCharsets.UTF_8)
            } catch (_: IOException) {
                ""
            }
        } else {
            ""
        }

        val maxPosition = text.length
        return Snapshot(
            text = text,
            selectionStart = preferences.getInt("selection_start", maxPosition).coerceIn(0, maxPosition),
            selectionEnd = preferences.getInt("selection_end", maxPosition).coerceIn(0, maxPosition),
            scrollX = preferences.getInt("scroll_x", 0).coerceAtLeast(0),
            scrollY = preferences.getInt("scroll_y", 0).coerceAtLeast(0),
        )
    }

    @Synchronized
    fun save(text: String, selectionStart: Int, selectionEnd: Int, scrollX: Int, scrollY: Int) {
        notesDirectory.mkdirs()
        temporaryFile.writeText(text, StandardCharsets.UTF_8)
        if (!temporaryFile.renameTo(noteFile)) {
            noteFile.delete()
            if (!temporaryFile.renameTo(noteFile)) {
                throw IOException("Could not replace local note")
            }
        }
        preferences.edit()
            .putInt("selection_start", selectionStart.coerceIn(0, text.length))
            .putInt("selection_end", selectionEnd.coerceIn(0, text.length))
            .putInt("scroll_x", scrollX.coerceAtLeast(0))
            .putInt("scroll_y", scrollY.coerceAtLeast(0))
            .apply()
    }
}
