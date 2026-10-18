package org.ivanivorontsov.stroka.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TextSearchTest {
    @Test
    fun plainCaseInsensitive() {
        val r = TextSearch.findAll("Foo foo FOO", TextSearch.Query("foo", regex = false, matchCase = false))
        assertEquals(3, r.size)
        assertEquals(1, TextSearch.findAll("Foo foo FOO", TextSearch.Query("foo", false, true)).size)
    }

    @Test
    fun plainEscapesRegexChars() {
        assertEquals(1, TextSearch.findAll("a.b axb", TextSearch.Query("a.b", false, true)).size)
    }

    @Test
    fun regexWithGroupsReplace() {
        val q = TextSearch.Query("(\\w+)@(\\w+)", regex = true, matchCase = true)
        val (res, n) = TextSearch.replaceAll("ivan@mail and petr@ya", q, "$2:$1")
        assertEquals("mail:ivan and ya:petr", res)
        assertEquals(2, n)
    }

    @Test
    fun singleReplacement() {
        val q = TextSearch.Query("v(\\d)", true, true)
        val t = "v1 v2"
        val m = TextSearch.findNext(t, q, 1)!!
        assertEquals(3..4, m)
        assertEquals("ver2", TextSearch.replacementFor(t, q, m, "ver$1"))
    }

    @Test
    fun cyrillicCaseInsensitive() {
        assertEquals(2, TextSearch.findAll("Привет привет", TextSearch.Query("ПРИВЕТ", false, false)).size)
    }

    @Test
    fun wholeWord() {
        assertEquals(1, TextSearch.findAll("cat concat cat_", TextSearch.Query("cat", false, true, wholeWord = true)).size)
    }

    @Test
    fun invalidRegex() {
        assertNotNull(TextSearch.validate(TextSearch.Query("(", true, true)))
        assertNull(TextSearch.validate(TextSearch.Query("(", false, true)))
        assertEquals(0, TextSearch.findAll("(((", TextSearch.Query("(", true, true)).size)
    }

    @Test
    fun wrapAroundBackwards() {
        val q = TextSearch.Query("a", false, true)
        assertEquals(0..0, TextSearch.findNext("a-a", q, 3, backwards = false))
        assertEquals(2..2, TextSearch.findNext("a-a", q, 0, backwards = true))
    }
}
