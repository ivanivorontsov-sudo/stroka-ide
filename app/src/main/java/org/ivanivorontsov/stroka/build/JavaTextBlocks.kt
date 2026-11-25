package org.ivanivorontsov.stroka.build

/**
 * Текстовые блоки Java 15+ ("""…""") для ECJ 3.18 (уровень Java 8): каждый блок заменяется
 * обычными строками, склеенными через +, по правилам JLS 3.10.6 (общий отступ, хвостовые пробелы,
 * \<перевод строки>, \s). Число строк сохраняется, поэтому номера строк в ошибках не сдвигаются.
 */
object JavaTextBlocks {
    fun desugar(src: String): String {
        if (!src.contains("\"\"\"")) return src
        val out = StringBuilder(src.length)
        var i = 0
        val n = src.length
        while (i < n) {
            val c = src[i]
            when {
                c == '/' && i + 1 < n && src[i + 1] == '/' -> { val e = src.indexOf('\n', i).let { if (it < 0) n else it }; out.append(src, i, e); i = e }
                c == '/' && i + 1 < n && src[i + 1] == '*' -> { val e = src.indexOf("*/", i + 2).let { if (it < 0) n else it + 2 }; out.append(src, i, e); i = e }
                c == '\'' -> { val e = skipQuoted(src, i, '\''); out.append(src, i, e); i = e }
                src.startsWith("\"\"\"", i) -> {
                    var j = i + 3
                    while (j < n && (src[j] == ' ' || src[j] == '\t' || src[j] == '\u000c')) j++
                    if (j < n && src[j] == '\r') j++
                    if (j >= n || src[j] != '\n') throw BuildException("Текстовый блок \"\"\" должен продолжаться с новой строки.")
                    val start = j + 1
                    var k = start
                    while (k < n) {
                        if (src[k] == '\\') { k += 2; continue }
                        if (src.startsWith("\"\"\"", k)) break
                        k++
                    }
                    if (k >= n) throw BuildException("Текстовый блок \"\"\" не закрыт.")
                    out.append('\n').append(render(src.substring(start, k)))
                    i = k + 3
                }
                c == '"' -> { val e = skipQuoted(src, i, '"'); out.append(src, i, e); i = e }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    private fun skipQuoted(s: String, from: Int, q: Char): Int {
        var i = from + 1
        while (i < s.length && s[i] != q && s[i] != '\n') { if (s[i] == '\\') i++; i++ }
        return minOf(i + 1, s.length)
    }

    private fun render(raw: String): String {
        val lines = raw.replace("\r\n", "\n").split('\n')
        val closingOwnLine = lines.last().isBlank()
        val significant = lines.filterIndexed { idx, l -> l.isNotBlank() || (idx == lines.lastIndex && closingOwnLine) }
        val indent = significant.minOfOrNull { l -> l.takeWhile { it == ' ' || it == '\t' }.length } ?: 0
        val content = lines.mapIndexed { idx, l ->
            val stripped = if (l.isBlank()) "" else l.substring(minOf(indent, l.length)).trimEnd(' ', '\t')
            if (idx == lines.lastIndex && closingOwnLine) "" else stripped
        }
        // Склеиваем, учитывая \<перевод строки> (без перевода) и последнюю строку
        val parts = ArrayList<String>()
        for ((idx, l) in content.withIndex()) {
            val last = idx == content.lastIndex
            if (last && closingOwnLine) { parts += ""; continue }
            val joined = l.endsWith("\\") && !l.endsWith("\\\\")
            val text = if (joined) l.dropLast(1) else l
            parts += escape(text) + if (last || joined) "" else "\\n"
        }
        // Одна строка исходника = один литерал; переводы строк исходника сохраняются
        return parts.joinToString(" +\n") { "\"" + it + "\"" }
    }

    private fun escape(s: String): String {
        val b = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                val d = s[i + 1]
                b.append(if (d == 's') "\\040" else "\\$d")
                i += 2; continue
            }
            if (c == '"') b.append("\\\"") else b.append(c)
            i++
        }
        return b.toString()
    }
}
