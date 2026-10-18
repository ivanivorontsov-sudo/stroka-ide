package org.ivanivorontsov.stroka.core

object Completer {
    private fun isWordChar(c: Char, lang: Lang) = c.isLetterOrDigit() || c == '_' || (c == '$' && (lang == Lang.JAVASCRIPT || lang == Lang.TYPESCRIPT)) || (c == '-' && lang == Lang.CSS)

    fun prefixAt(t: CharSequence, cursor: Int, lang: Lang): String {
        var i = cursor.coerceIn(0, t.length)
        val end = i
        while (i > 0 && isWordChar(t[i - 1], lang)) i--
        if (lang == Lang.ASM6502 && i > 0 && t[i - 1] == '.') i--
        return t.subSequence(i, end).toString()
    }

    fun words(t: CharSequence, lang: Lang, skipFrom: Int = -1, skipTo: Int = -1, maxChars: Int = 400_000): Map<String, Int> {
        val res = HashMap<String, Int>()
        val n = minOf(t.length, maxChars)
        var i = 0
        while (i < n) {
            val c = t[i]
            if (c.isLetter() || c == '_' || (c == '$' && isWordChar(c, lang))) {
                val s = i
                while (i < n && isWordChar(t[i], lang)) i++
                if (!(s == skipFrom && i == skipTo) && i - s >= 2) {
                    val w = t.subSequence(s, i).toString()
                    res[w] = (res[w] ?: 0) + 1
                }
            } else if (c.isDigit()) {
                while (i < n && t[i].isLetterOrDigit()) i++
            } else i++
        }
        return res
    }

    fun suggest(t: CharSequence, cursor: Int, lang: Lang, limit: Int = 15): List<String> {
        val prefix = prefixAt(t, cursor, lang)
        if (prefix.isEmpty()) return emptyList()
        var wordEnd = cursor
        while (wordEnd < t.length && isWordChar(t[wordEnd], lang)) wordEnd++
        val fileWords = words(t, lang, cursor - prefix.length, wordEnd)
        val spec = LangSpec.of(lang)
        val candidates = HashMap<String, Int>()
        for ((w, n) in fileWords) candidates[w] = n
        val dict = if (lang == Lang.ASM6502) spec.allWords.filter { it.length > 1 } else spec.allWords
        for (k in dict) candidates[k] = (candidates[k] ?: 0) + 1
        data class Scored(val w: String, val score: Int)
        val scored = ArrayList<Scored>()
        val pl = prefix.lowercase()
        for ((w, n) in candidates) {
            if (w == prefix) continue
            val wl = w.lowercase()
            val score = when {
                w.startsWith(prefix) -> 3000
                wl.startsWith(pl) -> 2000
                prefix.length >= 2 && isSubsequence(pl, wl) && wl[0] == pl[0] -> 1000
                else -> continue
            }
            val kw = if (w in spec.allWords) 5 else 0
            scored.add(Scored(w, score + minOf(n, 50) * 10 + kw - w.length))
        }
        return scored.sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.w }).take(limit).map { it.w }
    }

    fun isSubsequence(needle: String, hay: String): Boolean {
        var j = 0
        for (ch in hay) {
            if (j < needle.length && needle[j] == ch) j++
        }
        return j == needle.length
    }
}
