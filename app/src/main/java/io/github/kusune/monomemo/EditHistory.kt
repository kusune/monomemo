package io.github.kusune.monomemo

/** The editor contents and caret selection at one point in the edit timeline. */
data class EditorState(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
) {
    fun normalized(): EditorState {
        val start = selectionStart.coerceIn(0, text.length)
        val end = selectionEnd.coerceIn(0, text.length)
        return if (start <= end) {
            copy(selectionStart = start, selectionEnd = end)
        } else {
            copy(selectionStart = end, selectionEnd = start)
        }
    }
}

/** One TextWatcher change, stored as a reversible replacement operation. */
data class TextEdit(
    val start: Int,
    val removedText: String,
    val insertedText: String,
    val selectionBeforeStart: Int,
    val selectionBeforeEnd: Int,
    val selectionAfterStart: Int,
    val selectionAfterEnd: Int,
) {
    fun undo(current: EditorState): EditorState? = apply(
        current = current,
        expectedText = insertedText,
        replacementText = removedText,
        selectionStart = selectionBeforeStart,
        selectionEnd = selectionBeforeEnd,
    )

    fun redo(current: EditorState): EditorState? = apply(
        current = current,
        expectedText = removedText,
        replacementText = insertedText,
        selectionStart = selectionAfterStart,
        selectionEnd = selectionAfterEnd,
    )

    private fun apply(
        current: EditorState,
        expectedText: String,
        replacementText: String,
        selectionStart: Int,
        selectionEnd: Int,
    ): EditorState? {
        val safeStart = start.coerceIn(0, current.text.length)
        val expectedEnd = safeStart + expectedText.length
        if (expectedEnd > current.text.length ||
            current.text.regionMatches(safeStart, expectedText, 0, expectedText.length).not()
        ) {
            // The editor changed outside this history. Do not guess at a replacement
            // and risk corrupting the note.
            return null
        }

        val nextText = buildString(current.text.length - expectedText.length + replacementText.length) {
            append(current.text, 0, safeStart)
            append(replacementText)
            append(current.text, expectedEnd, current.text.length)
        }
        return EditorState(nextText, selectionStart, selectionEnd).normalized()
    }
}

/** A bounded, branching undo/redo timeline for text edits. */
class EditHistory(private val maxEdits: Int = DEFAULT_MAX_EDITS) {
    private val edits = ArrayList<TextEdit>(maxEdits)
    private var position = 0

    init {
        require(maxEdits >= 1) { "maxEdits must be at least one" }
    }

    val canUndo: Boolean
        get() = position > 0

    val canRedo: Boolean
        get() = position < edits.size

    val editCount: Int
        get() = edits.size

    fun clear() {
        edits.clear()
        position = 0
    }

    /** Records a new edit and removes any abandoned redo branch. */
    fun record(edit: TextEdit): Boolean {
        if (edit.removedText == edit.insertedText) return false

        if (position < edits.size) {
            edits.subList(position, edits.size).clear()
        }
        edits.add(edit)
        position = edits.size

        if (edits.size > maxEdits) {
            edits.removeAt(0)
            position = edits.size
        }
        return true
    }

    fun undo(current: EditorState): EditorState? {
        if (!canUndo) return null
        val target = edits[position - 1].undo(current) ?: return null
        position -= 1
        return target
    }

    fun redo(current: EditorState): EditorState? {
        if (!canRedo) return null
        val target = edits[position].redo(current) ?: return null
        position += 1
        return target
    }

    companion object {
        /** Enough for ordinary sessions while keeping accidental input bounded. */
        const val DEFAULT_MAX_EDITS = 1000
    }
}
