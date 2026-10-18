package org.ivanivorontsov.stroka.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlighterTest {
    private fun typeAt(t: String, lang: Lang, word: String, nth: Int = 0): TokenType? {
        var idx = -1
        repeat(nth + 1) { idx = t.indexOf(word, idx + 1) }
        return Highlighter.tokenize(t, lang).firstOrNull { idx >= it.start && idx < it.end }?.type
    }

    @Test
    fun python() {
        val t = "def f(x):\n    # note\n    return \"s\" + 42\n@dec\nclass A: pass"
        assertEquals(TokenType.KEYWORD, typeAt(t, Lang.PYTHON, "def"))
        assertEquals(TokenType.FUNCTION, typeAt(t, Lang.PYTHON, "f("))
        assertEquals(TokenType.COMMENT, typeAt(t, Lang.PYTHON, "note"))
        assertEquals(TokenType.STRING, typeAt(t, Lang.PYTHON, "\"s\""))
        assertEquals(TokenType.NUMBER, typeAt(t, Lang.PYTHON, "42"))
        assertEquals(TokenType.META, typeAt(t, Lang.PYTHON, "@dec"))
    }

    @Test
    fun pythonTripleQuotes() {
        val t = "x = \"\"\"line1\nline2\"\"\"\ny = 1"
        assertEquals(TokenType.STRING, typeAt(t, Lang.PYTHON, "line2"))
        assertEquals(TokenType.NUMBER, typeAt(t, Lang.PYTHON, "1", 1))
    }

    @Test
    fun javascript() {
        val t = "const a = `x${1}`; /* block\ncomment */ let b = true"
        assertEquals(TokenType.KEYWORD, typeAt(t, Lang.JAVASCRIPT, "const"))
        assertEquals(TokenType.STRING, typeAt(t, Lang.JAVASCRIPT, "`x"))
        assertEquals(TokenType.COMMENT, typeAt(t, Lang.JAVASCRIPT, "comment"))
        assertEquals(TokenType.CONSTANT, typeAt(t, Lang.JAVASCRIPT, "true"))
    }

    @Test
    fun cPreprocessor() {
        val t = "#include <stdio.h>\nint main() { return 0x1F; }"
        assertEquals(TokenType.META, typeAt(t, Lang.C, "include"))
        assertEquals(TokenType.TYPE, typeAt(t, Lang.C, "int"))
        assertEquals(TokenType.NUMBER, typeAt(t, Lang.C, "0x1F"))
    }

    @Test
    fun htmlWithScriptAndStyle() {
        val t = "<div class=\"a\">x &amp; y</div><script>let z = 1;</script><style>p { display: flex; }</style>"
        assertEquals(TokenType.TAG, typeAt(t, Lang.HTML, "<div"))
        assertEquals(TokenType.ATTR, typeAt(t, Lang.HTML, "class"))
        assertEquals(TokenType.STRING, typeAt(t, Lang.HTML, "\"a\""))
        assertEquals(TokenType.CONSTANT, typeAt(t, Lang.HTML, "&amp;"))
        assertEquals(TokenType.KEYWORD, typeAt(t, Lang.HTML, "let"))
        assertEquals(TokenType.ATTR, typeAt(t, Lang.HTML, "display"))
        assertEquals(TokenType.KEYWORD, typeAt(t, Lang.HTML, "flex"))
    }

    @Test
    fun cssAndJson() {
        val css = ".btn:hover { margin: 10px; color: #fff; }"
        assertEquals(TokenType.TAG, typeAt(css, Lang.CSS, ".btn"))
        assertEquals(TokenType.ATTR, typeAt(css, Lang.CSS, "margin"))
        assertEquals(TokenType.NUMBER, typeAt(css, Lang.CSS, "10px"))
        assertEquals(TokenType.NUMBER, typeAt(css, Lang.CSS, "#fff"))
        val json = "{\"k\": \"v\", \"n\": -1.5e3, \"b\": null}"
        assertEquals(TokenType.ATTR, typeAt(json, Lang.JSON, "\"k\""))
        assertEquals(TokenType.STRING, typeAt(json, Lang.JSON, "\"v\""))
        assertEquals(TokenType.NUMBER, typeAt(json, Lang.JSON, "-1.5e3"))
        assertEquals(TokenType.CONSTANT, typeAt(json, Lang.JSON, "null"))
    }

    @Test
    fun markdown() {
        val t = "# Title\n- item **bold** and `code`\n```\nfenced\n```\n[link](http://x)"
        assertEquals(TokenType.HEADING, typeAt(t, Lang.MARKDOWN, "Title"))
        assertEquals(TokenType.EMPHASIS, typeAt(t, Lang.MARKDOWN, "bold"))
        assertEquals(TokenType.STRING, typeAt(t, Lang.MARKDOWN, "code"))
        assertEquals(TokenType.STRING, typeAt(t, Lang.MARKDOWN, "fenced"))
        assertEquals(TokenType.LINK, typeAt(t, Lang.MARKDOWN, "link"))
    }

    @Test
    fun asm6502() {
        val t = "    processor 6502\nReset:\n    LDA #\$FF ; load\n    sta WSYNC,x\n    .byte %1010"
        assertEquals(TokenType.META, typeAt(t, Lang.ASM6502, "processor"))
        assertEquals(TokenType.LABEL, typeAt(t, Lang.ASM6502, "Reset"))
        assertEquals(TokenType.KEYWORD, typeAt(t, Lang.ASM6502, "LDA"))
        assertEquals(TokenType.NUMBER, typeAt(t, Lang.ASM6502, "\$FF"))
        assertEquals(TokenType.COMMENT, typeAt(t, Lang.ASM6502, "load"))
        assertEquals(TokenType.BUILTIN, typeAt(t, Lang.ASM6502, "x"))
        assertEquals(TokenType.META, typeAt(t, Lang.ASM6502, ".byte"))
        assertEquals(TokenType.NUMBER, typeAt(t, Lang.ASM6502, "%1010"))
    }

    @Test
    fun gdscriptKotlinJava() {
        assertEquals(TokenType.KEYWORD, typeAt("func _ready():\n\tvar v := Vector2(1, 2)", Lang.GDSCRIPT, "func"))
        assertEquals(TokenType.TYPE, typeAt("func _ready():\n\tvar v := Vector2(1, 2)", Lang.GDSCRIPT, "Vector2"))
        assertEquals(TokenType.KEYWORD, typeAt("fun main() { val x: Int = 1 }", Lang.KOTLIN, "val"))
        assertEquals(TokenType.TYPE, typeAt("fun main() { val x: Int = 1 }", Lang.KOTLIN, "Int"))
        assertEquals(TokenType.KEYWORD, typeAt("public static void main(String[] a) {}", Lang.JAVA, "static"))
    }

    @Test
    fun tokensSortedAndInBounds() {
        for (lang in Lang.entries) {
            val t = "x = 1 # y \"z\" <a b='c'> {d: e}; /* f */ // g\n"
            val toks = Highlighter.tokenize(t, lang)
            var last = 0
            for (tk in toks) {
                assertTrue("$lang $tk", tk.start >= 0 && tk.end <= t.length && tk.start < tk.end)
                assertTrue("$lang order $tk", tk.start >= last)
                last = tk.end
            }
        }
    }

    @Test
    fun largeInputIsFast() {
        val sb = StringBuilder()
        repeat(5000) { sb.append("def f$it(x):\n    return x * $it  # c\n") }
        val start = System.nanoTime()
        Highlighter.tokenize(sb, Lang.PYTHON)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("took $ms ms", ms < 2000)
    }

    @Test
    fun langDetection() {
        assertEquals(Lang.PYTHON, Lang.forName("a.py"))
        assertEquals(Lang.ASM6502, Lang.forName("kibalchish.asm"))
        assertEquals(Lang.GDSCRIPT, Lang.forName("player.gd"))
        assertEquals(Lang.PLAIN, Lang.forName("README"))
    }
}
