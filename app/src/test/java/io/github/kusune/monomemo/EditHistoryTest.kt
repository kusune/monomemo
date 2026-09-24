package io.github.kusune.monomemo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryTest {
    @Test
    fun undoAndRedoRestoreTextAndSelection() {
        val history = EditHistory()
        val before = EditorState("abc", 3, 3)
        val after = EditorState("abc!", 4, 4)

        history.record(
            TextEdit(
                start = 3,
                removedText = "",
                insertedText = "!",
                selectionBeforeStart = 3,
                selectionBeforeEnd = 3,
                selectionAfterStart = 4,
                selectionAfterEnd = 4,
            ),
        )

        assertTrue(history.canUndo)
        assertEquals(before, history.undo(after))
        assertFalse(history.canUndo)
        assertTrue(history.canRedo)
        assertEquals(after, history.redo(before))
        assertFalse(history.canRedo)
    }

    @Test
    fun newEditAfterUndoDropsRedoBranch() {
        val history = EditHistory()
        val initial = EditorState("abc", 3, 3)
        val first = EditorState("abc!", 4, 4)
        val alternate = EditorState("abc?", 4, 4)

        history.record(edit(3, "", "!", 3, 4))
        assertEquals(initial, history.undo(first))

        history.record(edit(3, "", "?", 3, 4))
        assertFalse(history.canRedo)
        assertEquals(initial, history.undo(alternate))
        assertEquals(alternate, history.redo(initial))
        assertFalse(history.canRedo)
    }

    @Test
    fun replacementUndoRestoresSelectedText() {
        val history = EditHistory()
        val before = EditorState("hello", 1, 4)
        val after = EditorState("h!o", 2, 2)

        history.record(
            TextEdit(
                start = 1,
                removedText = "ell",
                insertedText = "!",
                selectionBeforeStart = 1,
                selectionBeforeEnd = 4,
                selectionAfterStart = 2,
                selectionAfterEnd = 2,
            ),
        )

        assertEquals(before, history.undo(after))
    }

    private fun edit(
        start: Int,
        removed: String,
        inserted: String,
        beforeCaret: Int,
        afterCaret: Int,
    ) = TextEdit(
        start = start,
        removedText = removed,
        insertedText = inserted,
        selectionBeforeStart = beforeCaret,
        selectionBeforeEnd = beforeCaret,
        selectionAfterStart = afterCaret,
        selectionAfterEnd = afterCaret,
    )
}
