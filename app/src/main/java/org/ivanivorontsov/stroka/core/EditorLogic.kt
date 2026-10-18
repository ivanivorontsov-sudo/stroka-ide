package org.ivanivorontsov.stroka.core

data class Edit(val start: Int, val end: Int, val text: String, val cursor: Int, val selEnd: Int = cursor)

object EditorLogic {
    val closers = mapOf('(' to ')', '[' to ']', '{' to '}', '"' to '"', '\'' to '\'', '`' to '`')
    private val closeChars = setOf(')', ']', '}')
    private val pyDedent = Regex("^(return|pass|break|continue|raise)\\b.*")

    fun lineStart(t: CharSequence, pos: Int): Int {
        var i = pos.coerceIn(0, t.length)
        while (i > 0 && t[i - 1] != '\n') i--
        return i
    }

    fun lineEnd(t: CharSequence, pos: Int): Int {
        var i = pos.coerceIn(0, t.length)
        while (i < t.length && t[i] != '\n') i++
        return i
    }

    fun leadingWhitespace(t: CharSequence, start: Int, end: Int): String {
        var i = start
        while (i < end && (t[i] == ' ' || t[i] == '\t')) i++
        return t.subSequence(start, i).toString()
    }

    fun removeOneIndent(indent: String, unit: String): String = when {
        indent.endsWith("\t") -> indent.dropLast(1)
        indent.endsWith(unit) -> indent.dropLast(unit.length)
        else -> indent.trimEnd(' ').let { if (indent.length - it.length > 0) indent.dropLast(minOf(unit.length, indent.length - it.length)) else indent }
    }

    fun afterNewline(t: CharSequence, nlPos: Int, lang: Lang, unit: String): Edit? {
        if (nlPos < 0 || nlPos >= t.length || t[nlPos] != '\n') return null
        val ls = lineStart(t, nlPos)
        val prev = t.subSequence(ls, nlPos).toString()
        val indent = leadingWhitespace(prev, 0, prev.length)
        val trimmed = prev.trimEnd()
        val body = trimmed.trimStart()
        val last = trimmed.lastOrNull()
        var extra = last == '{' || last == '(' || last == '['
        if (lang.colonIndents && last == ':' && !body.startsWith("#")) extra = true
        if (lang == Lang.HTML && last == '>' && isOpeningTagLine(body)) extra = true
        var newIndent = if (extra) indent + unit else indent
        if (lang.colonIndents && !extra && pyDedent.matches(body)) newIndent = removeOneIndent(indent, unit)
        val pos = nlPos + 1
        val next = if (pos < t.length) t[pos] else '\u0000'
        val pairClose = (last == '{' && next == '}') || (last == '(' && next == ')') || (last == '[' && next == ']') ||
            (lang == Lang.HTML && last == '>' && next == '<' && pos + 1 < t.length && t[pos + 1] == '/')
        if (extra && pairClose) {
            val ins = newIndent + "\n" + indent
            return Edit(pos, pos, ins, pos + newIndent.length)
        }
        var e = pos
        while (e < t.length && (t[e] == ' ' || t[e] == '\t')) e++
        if (newIndent.isEmpty() && e == pos) return null
        return Edit(pos, e, newIndent, pos + newIndent.length)
    }

    private val voidTags = setOf("br", "hr", "img", "input", "meta", "link", "area", "base", "col", "embed", "source", "track", "wbr", "!doctype")

    private fun isOpeningTagLine(body: String): Boolean {
        val lt = body.lastIndexOf('<')
        if (lt < 0 || body.endsWith("/>")) return false
        val tag = body.substring(lt + 1).takeWhile { it.isLetterOrDigit() || it == '!' || it == '-' }.lowercase()
        if (tag.isEmpty() || tag in voidTags) return false
        return !body.substring(lt).startsWith("</")
    }

    fun afterTyped(t: CharSequence, pos: Int, lang: Lang, unit: String, autoPair: Boolean = true): Edit? {
        if (pos < 0 || pos >= t.length) return null
        val c = t[pos]
        val next = if (pos + 1 < t.length) t[pos + 1] else '\u0000'
        val prev = if (pos > 0) t[pos - 1] else '\u0000'
        if (c in closeChars) {
            if (autoPair && next == c) return Edit(pos + 1, pos + 2, "", pos + 1)
            val ls = lineStart(t, pos)
            if (c == '}' && pos > ls && t.subSequence(ls, pos).isBlank()) {
                val indent = t.subSequence(ls, pos).toString()
                val wanted = matchingIndent(t, pos) ?: removeOneIndent(indent, unit)
                if (wanted != indent) return Edit(ls, pos, wanted, ls + wanted.length + 1)
            }
            return null
        }
        if (!autoPair) return null
        if (c == '"' || c == '\'' || c == '`') {
            if (next == c) return Edit(pos + 1, pos + 2, "", pos + 1)
            if (lang == Lang.MARKDOWN || lang == Lang.PLAIN) return null
            if (c == '`' && lang != Lang.JAVASCRIPT && lang != Lang.TYPESCRIPT && lang != Lang.KOTLIN) return null
            if (c == '\'' && (lang == Lang.ASM6502 || lang == Lang.HTML && !inTag(t, pos))) return null
            if (prev.isLetterOrDigit() || prev == '\\' || prev == c) return null
            if (next != '\u0000' && !next.isWhitespace() && next !in closeChars && next != ',' && next != ';' && next != ':') return null
            return Edit(pos + 1, pos + 1, c.toString(), pos + 1)
        }
        val close = closers[c] ?: return null
        if (next != '\u0000' && !next.isWhitespace() && next !in closeChars && next != ',' && next != ';' && next != ':' && next != '.') return null
        if (lang == Lang.MARKDOWN && c != '(' && c != '[') return null
        return Edit(pos + 1, pos + 1, close.toString(), pos + 1)
    }

    private fun inTag(t: CharSequence, pos: Int): Boolean {
        var i = pos - 1
        while (i >= 0) {
            if (t[i] == '>') return false
            if (t[i] == '<') return true
            i--
        }
        return false
    }

    private fun matchingIndent(t: CharSequence, closePos: Int): String? {
        var depth = 0
        var i = closePos - 1
        while (i >= 0) {
            val ch = t[i]
            if (ch == '}') depth++
            else if (ch == '{') {
                if (depth == 0) {
                    val ls = lineStart(t, i)
                    return leadingWhitespace(t, ls, i)
                }
                depth--
            }
            i--
        }
        return null
    }

    fun afterDeleted(t: CharSequence, pos: Int, removed: Char): Edit? {
        val close = closers[removed] ?: return null
        if (pos < t.length && t[pos] == close) return Edit(pos, pos + 1, "", pos)
        return null
    }

    fun indentLines(t: CharSequence, selStart: Int, selEnd: Int, unit: String, outdent: Boolean): Edit {
        val a = lineStart(t, minOf(selStart, selEnd))
        val endPos = maxOf(selStart, selEnd)
        val b = lineEnd(t, if (endPos > a && endPos > 0 && t[endPos - 1] == '\n') endPos - 1 else endPos)
        val lines = t.subSequence(a, b).toString().split("\n")
        val res = lines.joinToString("\n") { line ->
            if (outdent) {
                when {
                    line.startsWith("\t") -> line.substring(1)
                    else -> {
                        var n = 0
                        while (n < unit.length.coerceAtLeast(1) && n < line.length && line[n] == ' ') n++
                        line.substring(n)
                    }
                }
            } else if (line.isEmpty() && lines.size > 1) line else unit + line
        }
        return Edit(a, b, res, a, a + res.length)
    }

    fun toggleComment(t: CharSequence, selStart: Int, selEnd: Int, lang: Lang): Edit? {
        val a = lineStart(t, minOf(selStart, selEnd))
        val b = lineEnd(t, maxOf(selStart, selEnd))
        val block = t.subSequence(a, b).toString()
        val lc = lang.lineComment
        if (lc != null) {
            val lines = block.split("\n")
            val nonEmpty = lines.filter { it.isNotBlank() }
            val allCommented = nonEmpty.isNotEmpty() && nonEmpty.all { it.trimStart().startsWith(lc) }
            val minIndent = nonEmpty.minOfOrNull { it.length - it.trimStart().length } ?: 0
            val res = lines.joinToString("\n") { line ->
                if (line.isBlank()) line
                else if (allCommented) {
                    val idx = line.indexOf(lc)
                    val after = line.substring(idx + lc.length)
                    line.substring(0, idx) + if (after.startsWith(" ")) after.substring(1) else after
                } else line.substring(0, minIndent) + lc + " " + line.substring(minIndent)
            }
            return Edit(a, b, res, a, a + res.length)
        }
        val bs = lang.blockStart ?: return null
        val be = lang.blockEnd ?: return null
        val trimmed = block.trim()
        val res = if (trimmed.startsWith(bs) && trimmed.endsWith(be)) {
            val lead = block.substring(0, block.indexOf(bs))
            lead + trimmed.removePrefix(bs).removeSuffix(be).trim()
        } else {
            val lead = leadingWhitespace(block, 0, block.length)
            "$lead$bs ${block.substring(lead.length)} $be"
        }
        return Edit(a, b, res, a, a + res.length)
    }

    fun lineOfOffset(t: CharSequence, pos: Int): Int {
        var n = 0
        val p = pos.coerceIn(0, t.length)
        for (i in 0 until p) if (t[i] == '\n') n++
        return n
    }

    fun offsetOfLine(t: CharSequence, line: Int): Int {
        if (line <= 0) return 0
        var n = 0
        for (i in t.indices) {
            if (t[i] == '\n') {
                n++
                if (n == line) return i + 1
            }
        }
        return t.length
    }

    fun lineCount(t: CharSequence): Int {
        var n = 1
        for (i in t.indices) if (t[i] == '\n') n++
        return n
    }

    fun wordBounds(t: CharSequence, pos: Int): IntRange {
        var a = pos.coerceIn(0, t.length)
        var b = a
        while (a > 0 && (t[a - 1].isLetterOrDigit() || t[a - 1] == '_')) a--
        while (b < t.length && (t[b].isLetterOrDigit() || t[b] == '_')) b++
        return a until b
    }

    fun previousWordStart(t: CharSequence, pos: Int): Int {
        var i = pos.coerceIn(0, t.length)
        while (i > 0 && t[i - 1].isWhitespace() && t[i - 1] != '\n') i--
        if (i > 0 && !(t[i - 1].isLetterOrDigit() || t[i - 1] == '_')) return i - 1
        while (i > 0 && (t[i - 1].isLetterOrDigit() || t[i - 1] == '_')) i--
        return i
    }
}
