package org.ivanivorontsov.stroka.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorLogicTest {
    private fun apply(t: String, e: Edit?): String = if (e == null) t else t.substring(0, e.start) + e.text + t.substring(e.end)

    @Test
    fun newlineKeepsIndent() {
        val t = "    x = 1\n"
        val e = EditorLogic.afterNewline(t, t.length - 1, Lang.PYTHON, "    ")
        assertEquals("    x = 1\n    ", apply(t, e))
    }

    @Test
    fun newlineAfterColonIndentsPython() {
        val t = "def f():\n"
        assertEquals("def f():\n    ", apply(t, EditorLogic.afterNewline(t, 8, Lang.PYTHON, "    ")))
    }

    @Test
    fun newlineAfterReturnDedents() {
        val t = "def f():\n    return 1\n"
        assertEquals("def f():\n    return 1\n", apply(t, EditorLogic.afterNewline(t, t.length - 1, Lang.PYTHON, "    ")))
    }

    @Test
    fun newlineBetweenBracesSplits() {
        val t = "if (a) {\n}"
        val e = EditorLogic.afterNewline(t, 8, Lang.JAVASCRIPT, "  ")!!
        assertEquals("if (a) {\n  \n}", apply(t, e))
        assertEquals(11, e.cursor)
    }

    @Test
    fun htmlTagIndent() {
        val t = "<div>\n</div>"
        assertEquals("<div>\n    \n</div>", apply(t, EditorLogic.afterNewline(t, 5, Lang.HTML, "    ")))
        val v = "<br>\n"
        assertNull(EditorLogic.afterNewline(v, 4, Lang.HTML, "    "))
    }

    @Test
    fun bracketPairing() {
        val t = "f("
        val e = EditorLogic.afterTyped(t, 1, Lang.PYTHON, "    ")!!
        assertEquals("f()", apply(t, e))
        assertEquals(2, e.cursor)
    }

    @Test
    fun noPairBeforeWord() {
        assertNull(EditorLogic.afterTyped("(abc", 0, Lang.PYTHON, "    "))
    }

    @Test
    fun closingSkipsOver() {
        val t = "f())"
        val e = EditorLogic.afterTyped(t, 2, Lang.PYTHON, "    ")!!
        assertEquals("f()", apply(t, e))
        assertEquals(3, e.cursor)
    }

    @Test
    fun quotePairingAndApostrophe() {
        assertEquals("x = \"\"", apply("x = \"", EditorLogic.afterTyped("x = \"", 4, Lang.PYTHON, "    ")))
        assertNull(EditorLogic.afterTyped("don'", 3, Lang.PYTHON, "    "))
        assertNull(EditorLogic.afterTyped("it'", 2, Lang.MARKDOWN, "    "))
    }

    @Test
    fun closingBraceOutdents() {
        val t = "if (a) {\n    x();\n    }"
        val e = EditorLogic.afterTyped(t, t.length - 1, Lang.JAVASCRIPT, "    ")!!
        assertEquals("if (a) {\n    x();\n}", apply(t, e))
    }

    @Test
    fun backspaceRemovesPair() {
        val t = "f)"
        val e = EditorLogic.afterDeleted(t, 1, '(')!!
        assertEquals("f", apply(t, e))
    }

    @Test
    fun indentAndOutdentLines() {
        val t = "a\nb\nc"
        val e = EditorLogic.indentLines(t, 0, 3, "  ", false)
        assertEquals("  a\n  b\nc", apply(t, e))
        val back = apply(t, e)
        assertEquals("a\nb\nc", apply(back, EditorLogic.indentLines(back, 0, 6, "  ", true)))
    }

    @Test
    fun toggleLineComment() {
        val t = "  x = 1\n  y = 2"
        val c = apply(t, EditorLogic.toggleComment(t, 0, t.length, Lang.PYTHON))
        assertEquals("  # x = 1\n  # y = 2", c)
        assertEquals(t, apply(c, EditorLogic.toggleComment(c, 0, c.length, Lang.PYTHON)))
    }

    @Test
    fun toggleBlockCommentHtml() {
        val t = "<p>hi</p>"
        val c = apply(t, EditorLogic.toggleComment(t, 0, 0, Lang.HTML))
        assertEquals("<!-- <p>hi</p> -->", c)
        assertEquals(t, apply(c, EditorLogic.toggleComment(c, 0, 0, Lang.HTML)))
    }

    @Test
    fun lineHelpers() {
        val t = "a\nbb\nccc"
        assertEquals(3, EditorLogic.lineCount(t))
        assertEquals(2, EditorLogic.lineOfOffset(t, 6))
        assertEquals(5, EditorLogic.offsetOfLine(t, 2))
        val idx = LineIndex()
        idx.rebuild(t)
        assertEquals(3, idx.count)
        assertEquals(1, idx.lineOf(3))
        assertEquals(5, idx.startOf(2))
    }
}
