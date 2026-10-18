package org.ivanivorontsov.stroka.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UndoStackTest {
    private class Doc {
        val sb = StringBuilder()
        val undo = UndoStack()
        var time = 0L
        fun replace(start: Int, end: Int, text: String, record: Boolean = true) {
            val removed = sb.substring(start, end)
            sb.replace(start, end, text)
            if (record) undo.record(start, removed, text, time)
        }
        fun type(s: String) { for (c in s) { replace(sb.length, sb.length, c.toString()); time += 50 } }
        fun apply(r: UndoStack.Result?) { r?.ops?.forEach { sb.replace(it.start, it.end, it.text) } }
    }

    @Test
    fun wordsAreGrouped() {
        val d = Doc()
        d.type("hello world")
        d.apply(d.undo.undo())
        assertEquals("hello ", d.sb.toString())
        d.apply(d.undo.undo())
        assertEquals("", d.sb.toString())
        assertFalse(d.undo.canUndo)
        d.apply(d.undo.redo())
        assertEquals("hello ", d.sb.toString())
        d.apply(d.undo.redo())
        assertEquals("hello world", d.sb.toString())
    }

    @Test
    fun pauseBreaksGroup() {
        val d = Doc()
        d.type("ab")
        d.time += 5000
        d.type("cd")
        d.apply(d.undo.undo())
        assertEquals("ab", d.sb.toString())
    }

    @Test
    fun replacementAndDeletion() {
        val d = Doc()
        d.type("abc")
        d.time += 5000
        d.replace(1, 2, "XYZ")
        d.time += 5000
        d.replace(0, 1, "")
        assertEquals("XYZc", d.sb.toString())
        d.apply(d.undo.undo())
        assertEquals("aXYZc", d.sb.toString())
        d.apply(d.undo.undo())
        assertEquals("abc", d.sb.toString())
    }

    @Test
    fun newlineWithIndentUndoneTogether() {
        val d = Doc()
        d.type("if x:")
        d.time += 5000
        d.replace(5, 5, "\n")
        d.replace(6, 6, "    ")
        d.apply(d.undo.undo())
        assertEquals("if x:", d.sb.toString())
    }

    @Test
    fun newEditClearsRedo() {
        val d = Doc()
        d.type("a")
        d.apply(d.undo.undo())
        d.time += 5000
        d.type("b")
        assertFalse(d.undo.canRedo)
    }
}
