package org.ivanivorontsov.stroka.core

import java.util.regex.Matcher
import java.util.regex.Pattern

object TextSearch {
    class Query(val text: String, val regex: Boolean, val matchCase: Boolean, val wholeWord: Boolean = false) {
        fun pattern(): Pattern {
            var p = if (regex) text else Pattern.quote(text)
            if (wholeWord) p = "\\b(?:$p)\\b"
            var flags = Pattern.MULTILINE
            if (!matchCase) flags = flags or Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
            return Pattern.compile(p, flags)
        }
    }

    fun validate(q: Query): String? = try {
        q.pattern(); null
    } catch (e: Exception) {
        e.message?.lineSequence()?.firstOrNull() ?: "Ошибка в выражении"
    }

    fun findAll(t: CharSequence, q: Query, max: Int = 5000): List<IntRange> {
        if (q.text.isEmpty()) return emptyList()
        val p = try { q.pattern() } catch (e: Exception) { return emptyList() }
        val m = p.matcher(t)
        val res = ArrayList<IntRange>()
        var from = 0
        while (from <= t.length && m.find(from)) {
            if (m.end() == m.start()) {
                from = m.end() + 1
                continue
            }
            res.add(m.start() until m.end())
            if (res.size >= max) break
            from = m.end()
        }
        return res
    }

    fun findNext(t: CharSequence, q: Query, from: Int, backwards: Boolean = false): IntRange? {
        val all = findAll(t, q)
        if (all.isEmpty()) return null
        return if (!backwards) all.firstOrNull { it.first >= from } ?: all.first()
        else all.lastOrNull { it.last + 1 <= from } ?: all.last()
    }

    fun replacementFor(t: CharSequence, q: Query, range: IntRange, replacement: String): String? {
        val p = try { q.pattern() } catch (e: Exception) { return null }
        val m = p.matcher(t)
        if (!m.find(range.first) || m.start() != range.first || m.end() != range.last + 1) return null
        val repl = if (q.regex) replacement else Matcher.quoteReplacement(replacement)
        val sb = StringBuffer()
        m.appendReplacement(sb, repl)
        return sb.substring(m.start())
    }

    fun replaceAll(t: CharSequence, q: Query, replacement: String): Pair<String, Int> {
        val count = findAll(t, q, Int.MAX_VALUE).size
        if (count == 0) return t.toString() to 0
        val p = q.pattern()
        val repl = if (q.regex) replacement else Matcher.quoteReplacement(replacement)
        return p.matcher(t).replaceAll(repl) to count
    }
}
