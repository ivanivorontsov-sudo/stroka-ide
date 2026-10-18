package org.ivanivorontsov.stroka.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleterTest {
    @Test
    fun suggestsKeywordsAndFileWords() {
        val t = "counter = 0\ncounterMax = 10\nco"
        val s = Completer.suggest(t, t.length, Lang.PYTHON)
        assertTrue(s.contains("counter"))
        assertTrue(s.contains("counterMax"))
        assertTrue(s.contains("continue"))
    }

    @Test
    fun exactCaseFirstAndFrequency() {
        val t = "value value value Value\nva"
        val s = Completer.suggest(t, t.length, Lang.JAVASCRIPT)
        assertEquals("value", s.first())
    }

    @Test
    fun doesNotSuggestWordBeingTyped() {
        val t = "zzq"
        assertTrue(Completer.suggest(t, 3, Lang.PLAIN).isEmpty())
    }

    @Test
    fun fuzzySubsequence() {
        val t = "getElementById\nge"
        val s = Completer.suggest("getElementById\ngEBI", 19, Lang.JAVASCRIPT)
        assertTrue(s.contains("getElementById"))
        assertTrue(Completer.suggest(t, t.length, Lang.JAVASCRIPT).contains("getElementById"))
    }

    @Test
    fun prefix() {
        assertEquals("abc", Completer.prefixAt("x.abc", 5, Lang.PYTHON))
        assertEquals("\$el", Completer.prefixAt("\$el", 3, Lang.JAVASCRIPT))
        assertEquals("", Completer.prefixAt("x(", 2, Lang.PYTHON))
    }

    @Test
    fun cyrillicWords() {
        val t = "привет приветствие\nпри"
        assertTrue(Completer.suggest(t, t.length, Lang.MARKDOWN).containsAll(listOf("привет", "приветствие")))
    }
}
