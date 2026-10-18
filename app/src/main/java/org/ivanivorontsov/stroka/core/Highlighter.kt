package org.ivanivorontsov.stroka.core

object Highlighter {

    fun tokenize(text: CharSequence, lang: Lang): List<Token> {
        val out = ArrayList<Token>()
        when (lang) {
            Lang.PLAIN -> {}
            Lang.HTML -> html(text, 0, text.length, out)
            Lang.CSS -> css(text, 0, text.length, out)
            Lang.JSON -> json(text, out)
            Lang.MARKDOWN -> markdown(text, out)
            Lang.ASM6502 -> asm(text, out)
            else -> code(text, 0, text.length, LangSpec.of(lang), out)
        }
        return out
    }

    private fun isIdentStart(c: Char, spec: LangSpec) = c.isLetter() || c == '_' || (c == '$' && '$' in spec.extraIdentChars)
    private fun isIdentPart(c: Char, spec: LangSpec) = c.isLetterOrDigit() || c in spec.extraIdentChars

    private fun startsWith(t: CharSequence, i: Int, s: String): Boolean {
        if (i + s.length > t.length) return false
        for (k in s.indices) if (t[i + k] != s[k]) return false
        return true
    }

    private fun indexOf(t: CharSequence, s: String, from: Int, end: Int): Int {
        var i = from
        while (i + s.length <= end) {
            if (startsWith(t, i, s)) return i
            i++
        }
        return -1
    }

    private fun lineEnd(t: CharSequence, i: Int, end: Int): Int {
        var k = i
        while (k < end && t[k] != '\n') k++
        return k
    }

    private fun skipString(t: CharSequence, i: Int, end: Int, q: Char, multiline: Boolean): Int {
        var k = i + 1
        while (k < end) {
            val c = t[k]
            if (c == '\\') { k += 2; continue }
            if (c == q) return k + 1
            if (c == '\n' && !multiline) return k
            k++
        }
        return end
    }

    private fun number(t: CharSequence, i: Int, end: Int): Int {
        var k = i
        if (t[k] == '0' && k + 1 < end && (t[k + 1] == 'x' || t[k + 1] == 'X' || t[k + 1] == 'b' || t[k + 1] == 'B' || t[k + 1] == 'o')) {
            k += 2
            while (k < end && (t[k].isLetterOrDigit() || t[k] == '_')) k++
            return k
        }
        while (k < end && (t[k].isDigit() || t[k] == '_' || t[k] == '.')) {
            if (t[k] == '.' && (k + 1 >= end || !t[k + 1].isDigit())) break
            k++
        }
        if (k < end && (t[k] == 'e' || t[k] == 'E')) {
            var m = k + 1
            if (m < end && (t[m] == '+' || t[m] == '-')) m++
            if (m < end && t[m].isDigit()) {
                k = m
                while (k < end && t[k].isDigit()) k++
            }
        }
        while (k < end && t[k].isLetter()) k++
        return k
    }

    fun code(t: CharSequence, from: Int, end: Int, spec: LangSpec, out: MutableList<Token>) {
        var i = from
        var lineStartOnlySpace = true
        while (i < end) {
            val c = t[i]
            if (c == '\n') { lineStartOnlySpace = true; i++; continue }
            if (c == ' ' || c == '\t' || c == '\r') { i++; continue }
            val atLineStart = lineStartOnlySpace
            lineStartOnlySpace = false
            val lc = spec.lineComments.firstOrNull { startsWith(t, i, it) }
            if (lc != null) {
                val e = lineEnd(t, i, end)
                out.add(Token(i, e, TokenType.COMMENT)); i = e; continue
            }
            val bc = spec.blockComment
            if (bc != null && startsWith(t, i, bc.first)) {
                val k = indexOf(t, bc.second, i + bc.first.length, end)
                val e = if (k < 0) end else k + bc.second.length
                out.add(Token(i, e, TokenType.COMMENT)); i = e; continue
            }
            if (spec.preprocessor && c == '#' && atLineStart) {
                val e = lineEnd(t, i, end)
                out.add(Token(i, e, TokenType.META)); i = e; continue
            }
            if (spec.tripleQuotes && (startsWith(t, i, "\"\"\"") || startsWith(t, i, "'''"))) {
                val q = t.subSequence(i, i + 3).toString()
                val k = indexOf(t, q, i + 3, end)
                val e = if (k < 0) end else k + 3
                out.add(Token(i, e, TokenType.STRING)); i = e; continue
            }
            if (c in spec.quotes) {
                val e = skipString(t, i, end, c, c == '`')
                out.add(Token(i, e, TokenType.STRING)); i = e; continue
            }
            if (spec.decorators && c == '@' && i + 1 < end && isIdentStart(t[i + 1], spec)) {
                var k = i + 1
                while (k < end && (isIdentPart(t[k], spec) || t[k] == '.')) k++
                out.add(Token(i, k, TokenType.META)); i = k; continue
            }
            if (c.isDigit() || (c == '.' && i + 1 < end && t[i + 1].isDigit())) {
                val e = number(t, i, end)
                out.add(Token(i, e, TokenType.NUMBER)); i = e; continue
            }
            if (isIdentStart(c, spec)) {
                var k = i + 1
                while (k < end && isIdentPart(t[k], spec)) k++
                val word = t.subSequence(i, k).toString()
                val key = if (spec.caseInsensitive) word.lowercase() else word
                val type = when (key) {
                    in spec.keywords -> TokenType.KEYWORD
                    in spec.constants -> TokenType.CONSTANT
                    in spec.types -> TokenType.TYPE
                    in spec.builtins -> TokenType.BUILTIN
                    else -> {
                        var m = k
                        while (m < end && t[m] == ' ') m++
                        if (m < end && t[m] == '(') TokenType.FUNCTION
                        else if (word[0].isUpperCase() && word.length > 1 && word.any { it.isLowerCase() }) TokenType.TYPE
                        else null
                    }
                }
                if (type != null) out.add(Token(i, k, type))
                i = k; continue
            }
            i++
        }
    }

    fun css(t: CharSequence, from: Int, end: Int, out: MutableList<Token>) {
        var i = from
        var depth = 0
        var inValue = false
        val spec = LangSpec.of(Lang.CSS)
        while (i < end) {
            val c = t[i]
            when {
                startsWith(t, i, "/*") -> {
                    val k = indexOf(t, "*/", i + 2, end)
                    val e = if (k < 0) end else k + 2
                    out.add(Token(i, e, TokenType.COMMENT)); i = e
                }
                c == '"' || c == '\'' -> {
                    val e = skipString(t, i, end, c, false)
                    out.add(Token(i, e, TokenType.STRING)); i = e
                }
                c == '{' -> { depth++; inValue = false; i++ }
                c == '}' -> { depth = maxOf(0, depth - 1); inValue = false; i++ }
                c == ';' -> { inValue = false; i++ }
                c == '@' -> {
                    var k = i + 1
                    while (k < end && (t[k].isLetterOrDigit() || t[k] == '-')) k++
                    out.add(Token(i, k, TokenType.META)); i = k
                }
                c == '#' && depth > 0 && inValue -> {
                    var k = i + 1
                    while (k < end && t[k].isLetterOrDigit()) k++
                    out.add(Token(i, k, TokenType.NUMBER)); i = k
                }
                c.isDigit() || (c == '.' && i + 1 < end && t[i + 1].isDigit() && inValue) -> {
                    var k = i
                    while (k < end && (t[k].isDigit() || t[k] == '.')) k++
                    while (k < end && (t[k].isLetter() || t[k] == '%')) k++
                    out.add(Token(i, k, TokenType.NUMBER)); i = k
                }
                c.isLetter() || c == '-' || c == '_' || ((c == '.' || c == '#') && depth == 0) -> {
                    var k = i + 1
                    while (k < end && (t[k].isLetterOrDigit() || t[k] == '-' || t[k] == '_')) k++
                    if (depth == 0) {
                        out.add(Token(i, k, TokenType.TAG))
                    } else if (!inValue) {
                        var m = k
                        while (m < end && t[m] == ' ') m++
                        if (m < end && t[m] == ':') {
                            out.add(Token(i, k, TokenType.ATTR)); inValue = true
                        }
                    } else {
                        val word = t.subSequence(i, k).toString()
                        if (word in spec.keywords) out.add(Token(i, k, TokenType.KEYWORD))
                        else if (k < end && t[k] == '(') out.add(Token(i, k, TokenType.FUNCTION))
                    }
                    i = k
                }
                c == ':' && depth > 0 -> { inValue = true; i++ }
                else -> i++
            }
        }
    }

    fun html(t: CharSequence, from: Int, end: Int, out: MutableList<Token>) {
        var i = from
        while (i < end) {
            val c = t[i]
            if (startsWith(t, i, "<!--")) {
                val k = indexOf(t, "-->", i + 4, end)
                val e = if (k < 0) end else k + 3
                out.add(Token(i, e, TokenType.COMMENT)); i = e; continue
            }
            if (c == '<' && i + 1 < end && (t[i + 1].isLetter() || t[i + 1] == '/' || t[i + 1] == '!' || t[i + 1] == '?')) {
                var k = i + 1
                if (t[k] == '/' || t[k] == '!' || t[k] == '?') k++
                val ns = k
                while (k < end && (t[k].isLetterOrDigit() || t[k] == '-' || t[k] == ':' || t[k] == '_')) k++
                val tagName = t.subSequence(ns, k).toString().lowercase()
                out.add(Token(i, k, TokenType.TAG))
                val closing = t[i + 1] == '/'
                while (k < end && t[k] != '>') {
                    val ch = t[k]
                    if (ch == '"' || ch == '\'') {
                        val e = skipString(t, k, end, ch, true)
                        out.add(Token(k, e, TokenType.STRING)); k = e
                    } else if (ch.isLetter() || ch == '-' || ch == '_' || ch == ':' || ch == '@') {
                        var m = k + 1
                        while (m < end && (t[m].isLetterOrDigit() || t[m] == '-' || t[m] == '_' || t[m] == ':' || t[m] == '.')) m++
                        out.add(Token(k, m, TokenType.ATTR)); k = m
                    } else if (ch == '/' && k + 1 < end && t[k + 1] == '>') {
                        out.add(Token(k, k + 2, TokenType.TAG)); k += 2
                        break
                    } else k++
                }
                if (k < end && t[k] == '>') { out.add(Token(k, k + 1, TokenType.TAG)); k++ }
                i = k
                if (!closing && (tagName == "script" || tagName == "style")) {
                    val closeTag = "</$tagName"
                    var ce = i
                    var found = -1
                    while (ce < end) {
                        val p = indexOf(t, "</", ce, end)
                        if (p < 0) break
                        if (t.subSequence(p, minOf(end, p + closeTag.length)).toString().lowercase() == closeTag) { found = p; break }
                        ce = p + 2
                    }
                    val stop = if (found < 0) end else found
                    if (tagName == "script") code(t, i, stop, LangSpec.of(Lang.JAVASCRIPT), out) else css(t, i, stop, out)
                    i = stop
                }
                continue
            }
            if (c == '&') {
                var k = i + 1
                while (k < end && k - i < 10 && (t[k].isLetterOrDigit() || t[k] == '#')) k++
                if (k < end && t[k] == ';') { out.add(Token(i, k + 1, TokenType.CONSTANT)); i = k + 1; continue }
            }
            i++
        }
    }

    fun json(t: CharSequence, out: MutableList<Token>) {
        var i = 0
        val end = t.length
        while (i < end) {
            val c = t[i]
            when {
                c == '"' -> {
                    val e = skipString(t, i, end, '"', false)
                    var m = e
                    while (m < end && (t[m] == ' ' || t[m] == '\t')) m++
                    out.add(Token(i, e, if (m < end && t[m] == ':') TokenType.ATTR else TokenType.STRING))
                    i = e
                }
                c == '-' || c.isDigit() -> {
                    val s = i
                    if (c == '-') i++
                    if (i < end && t[i].isDigit()) {
                        val e = number(t, i, end)
                        out.add(Token(s, e, TokenType.NUMBER)); i = e
                    }
                }
                startsWith(t, i, "//") -> {
                    val e = lineEnd(t, i, end)
                    out.add(Token(i, e, TokenType.COMMENT)); i = e
                }
                c.isLetter() -> {
                    var k = i
                    while (k < end && t[k].isLetter()) k++
                    val w = t.subSequence(i, k).toString()
                    if (w == "true" || w == "false" || w == "null") out.add(Token(i, k, TokenType.CONSTANT))
                    i = k
                }
                else -> i++
            }
        }
    }

    fun markdown(t: CharSequence, out: MutableList<Token>) {
        var i = 0
        val end = t.length
        var inFence = false
        var fenceStart = 0
        while (i < end) {
            val le = lineEnd(t, i, end)
            var s = i
            while (s < le && (t[s] == ' ' || t[s] == '\t')) s++
            if (startsWith(t, s, "```") || startsWith(t, s, "~~~")) {
                if (!inFence) { inFence = true; fenceStart = i } else { inFence = false; out.add(Token(fenceStart, le, TokenType.STRING)) }
                i = le + 1; continue
            }
            if (inFence) { i = le + 1; continue }
            if (s < le && t[s] == '#') {
                out.add(Token(i, le, TokenType.HEADING))
            } else if (s < le && t[s] == '>') {
                out.add(Token(i, le, TokenType.COMMENT))
            } else {
                if (s < le && (t[s] == '-' || t[s] == '*' || t[s] == '+') && s + 1 < le && t[s + 1] == ' ') out.add(Token(s, s + 1, TokenType.META))
                else {
                    var d = s
                    while (d < le && t[d].isDigit()) d++
                    if (d > s && d < le && t[d] == '.') out.add(Token(s, d + 1, TokenType.META))
                }
                inline(t, s, le, out)
            }
            i = le + 1
        }
        if (inFence) out.add(Token(fenceStart, end, TokenType.STRING))
    }

    private fun inline(t: CharSequence, from: Int, end: Int, out: MutableList<Token>) {
        var i = from
        while (i < end) {
            val c = t[i]
            if (c == '`') {
                val k = indexOf(t, "`", i + 1, end)
                if (k > 0) { out.add(Token(i, k + 1, TokenType.STRING)); i = k + 1; continue }
            }
            if (startsWith(t, i, "**") || startsWith(t, i, "__")) {
                val m = t.subSequence(i, i + 2).toString()
                val k = indexOf(t, m, i + 2, end)
                if (k > 0) { out.add(Token(i, k + 2, TokenType.EMPHASIS)); i = k + 2; continue }
            }
            if (c == '[') {
                val k = indexOf(t, "](", i + 1, end)
                if (k > 0) {
                    val p = indexOf(t, ")", k + 2, end)
                    if (p > 0) { out.add(Token(i, p + 1, TokenType.LINK)); i = p + 1; continue }
                }
            }
            i++
        }
    }

    fun asm(t: CharSequence, out: MutableList<Token>) {
        val spec = LangSpec.of(Lang.ASM6502)
        var i = 0
        val end = t.length
        var lineStart = true
        while (i < end) {
            val c = t[i]
            if (c == '\n') { lineStart = true; i++; continue }
            if (c == ';') { val e = lineEnd(t, i, end); out.add(Token(i, e, TokenType.COMMENT)); i = e; continue }
            if (c == '"' || c == '\'') { val e = skipString(t, i, end, c, false); out.add(Token(i, e, TokenType.STRING)); i = e; continue }
            if (lineStart && (c.isLetter() || c == '_' || c == '.' || c == '@')) {
                var k = i + 1
                while (k < end && (t[k].isLetterOrDigit() || t[k] == '_' || t[k] == '.')) k++
                val word = t.subSequence(i, k).toString().lowercase()
                val type = when (word) {
                    in spec.keywords -> TokenType.KEYWORD
                    in spec.types -> TokenType.META
                    else -> TokenType.LABEL
                }
                out.add(Token(i, if (type == TokenType.LABEL && k < end && t[k] == ':') k + 1 else k, type))
                lineStart = false; i = k; continue
            }
            if (c == ' ' || c == '\t') { lineStart = false; i++; continue }
            lineStart = false
            if (c == '#') { out.add(Token(i, i + 1, TokenType.CONSTANT)); i++; continue }
            if (c == '$' || c == '%' || c.isDigit()) {
                var k = i + 1
                val hex = c == '$'
                val bin = c == '%'
                if (bin && (k >= end || (t[k] != '0' && t[k] != '1'))) { i++; continue }
                if (hex && (k >= end || !(t[k].isDigit() || t[k].lowercaseChar() in 'a'..'f'))) { i++; continue }
                while (k < end && (if (hex) (t[k].isDigit() || t[k].lowercaseChar() in 'a'..'f') else if (bin) (t[k] == '0' || t[k] == '1') else t[k].isLetterOrDigit())) k++
                out.add(Token(i, k, TokenType.NUMBER)); i = k; continue
            }
            if (c.isLetter() || c == '_' || c == '.') {
                var k = i + 1
                while (k < end && (t[k].isLetterOrDigit() || t[k] == '_' || t[k] == '.')) k++
                val word = t.subSequence(i, k).toString().lowercase()
                val type = when (word) {
                    in spec.keywords -> TokenType.KEYWORD
                    in spec.types -> TokenType.META
                    in spec.builtins -> TokenType.BUILTIN
                    else -> null
                }
                if (type != null) out.add(Token(i, k, type))
                i = k; continue
            }
            i++
        }
    }
}
