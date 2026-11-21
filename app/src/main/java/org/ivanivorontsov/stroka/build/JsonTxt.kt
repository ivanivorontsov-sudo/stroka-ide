package org.ivanivorontsov.stroka.build

object JsonTxt {
    fun quote(s: String): String {
        val b = StringBuilder(s.length + 2)
        b.append('"')
        var i = 0
        while (i < s.length) {
            when (val c = s[i]) {
                '\\' -> b.append("\\\\")
                '"' -> b.append("\\\"")
                '\n' -> b.append("\\n")
                '\r' -> b.append("\\r")
                '\t' -> b.append("\\t")
                else -> if (c.code < 32) b.append("\\u%04x".format(c.code)) else b.append(c)
            }
            i++
        }
        b.append('"')
        return b.toString()
    }

    fun objectOf(map: Map<String, String>): String {
        val b = StringBuilder()
        b.append('{')
        var first = true
        for ((k, v) in map) {
            if (!first) b.append(',')
            first = false
            b.append(quote(k)).append(':').append(quote(v))
        }
        b.append('}')
        return b.toString()
    }

    fun parseStringMap(raw: String): Map<String, String> {
        var t = raw.trim()
        if (t.startsWith("module.exports")) {
            val eq = t.indexOf('=')
            if (eq >= 0) t = t.substring(eq + 1).trim()
        }
        if (t.endsWith(";")) t = t.substring(0, t.length - 1).trim()
        if (!t.startsWith("{") || !t.endsWith("}")) {
            throw BuildException("Непонятный формат списка стандартных библиотек.")
        }
        val out = LinkedHashMap<String, String>()
        var i = 1
        fun skip() {
            while (i < t.length && t[i].isWhitespace()) i++
        }
        fun parseString(): String {
            if (i >= t.length || t[i] != '"') throw BuildException("Ожидалась строка в std.js")
            i++
            val b = StringBuilder()
            while (i < t.length) {
                val c = t[i++]
                if (c == '"') return b.toString()
                if (c == '\\') {
                    if (i >= t.length) break
                    when (val e = t[i++]) {
                        'n' -> b.append('\n')
                        'r' -> b.append('\r')
                        't' -> b.append('\t')
                        '"' -> b.append('"')
                        '\\' -> b.append('\\')
                        '/' -> b.append('/')
                        'u' -> {
                            if (i + 4 > t.length) throw BuildException("Обрезанный \\u в std.js")
                            b.append(t.substring(i, i + 4).toInt(16).toChar())
                            i += 4
                        }
                        else -> b.append(e)
                    }
                } else {
                    b.append(c)
                }
            }
            throw BuildException("Незакрытая строка в std.js")
        }
        while (true) {
            skip()
            if (i >= t.length || t[i] == '}') break
            if (t[i] == ',') {
                i++
                continue
            }
            val k = parseString()
            skip()
            if (i >= t.length || t[i] != ':') throw BuildException("Ожидалось «:» в std.js")
            i++
            skip()
            val v = parseString()
            out[k] = v
        }
        return out
    }
}

sealed class Jv {
    data class Obj(val m: LinkedHashMap<String, Jv>) : Jv()
    data class Arr(val a: ArrayList<Jv>) : Jv()
    data class Str(val s: String) : Jv()
    data class Num(val raw: String) : Jv()
    data class Bool(val b: Boolean) : Jv()
    object Null : Jv()

    fun obj(k: String): Jv? = (this as? Obj)?.m?.get(k)
    fun str(k: String): String? = when (val v = obj(k)) {
        is Str -> v.s
        is Num -> v.raw
        is Bool -> v.b.toString()
        else -> null
    }
    fun int(k: String): Int? = str(k)?.toIntOrNull() ?: (obj(k) as? Num)?.raw?.toDoubleOrNull()?.toInt()
    fun arr(k: String): List<Jv> = (obj(k) as? Arr)?.a ?: emptyList()
    fun asStr(): String? = (this as? Str)?.s
    fun asObj(): Map<String, Jv> = (this as? Obj)?.m ?: emptyMap()
    fun asArr(): List<Jv> = (this as? Arr)?.a ?: emptyList()
}

fun JsonTxt.parse(raw: String): Jv {
    val s = raw.trim()
    var i = 0
    fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }
    fun parse(): Jv {
        skip()
        if (i >= s.length) return Jv.Null
        return when (s[i]) {
            '{' -> {
                i++
                val m = LinkedHashMap<String, Jv>()
                while (true) {
                    skip()
                    if (i < s.length && s[i] == '}') { i++; break }
                    if (s[i] == ',') { i++; continue }
                    val k = (parse() as? Jv.Str)?.s ?: throw BuildException("Ожидался ключ JSON")
                    skip()
                    if (i >= s.length || s[i] != ':') throw BuildException("Ожидалось :")
                    i++
                    m[k] = parse()
                }
                Jv.Obj(m)
            }
            '[' -> {
                i++
                val a = ArrayList<Jv>()
                while (true) {
                    skip()
                    if (i < s.length && s[i] == ']') { i++; break }
                    if (s[i] == ',') { i++; continue }
                    a.add(parse())
                }
                Jv.Arr(a)
            }
            '"' -> {
                val start = s
                val b = StringBuilder()
                i++
                while (i < start.length) {
                    val c = start[i++]
                    if (c == '"') return Jv.Str(b.toString())
                    if (c == '\\') {
                        if (i >= start.length) break
                        when (val e = start[i++]) {
                            'n' -> b.append('\n')
                            'r' -> b.append('\r')
                            't' -> b.append('\t')
                            '"' -> b.append('"')
                            '\\' -> b.append('\\')
                            '/' -> b.append('/')
                            'u' -> {
                                b.append(start.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> b.append(e)
                        }
                    } else b.append(c)
                }
                throw BuildException("Незакрытая строка JSON")
            }
            't' -> { i += 4; Jv.Bool(true) }
            'f' -> { i += 5; Jv.Bool(false) }
            'n' -> { i += 4; Jv.Null }
            else -> {
                val st = i
                while (i < s.length && s[i] in "+-0123456789.eE") i++
                Jv.Num(s.substring(st, i))
            }
        }
    }
    return parse()
}
