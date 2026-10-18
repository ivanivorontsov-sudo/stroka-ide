package org.ivanivorontsov.stroka.core

object NumberWords {
    private val units = mapOf(
        "ноль" to 0, "нуль" to 0, "один" to 1, "одна" to 1, "одно" to 1, "первая" to 1, "первую" to 1, "два" to 2, "две" to 2, "три" to 3, "четыре" to 4,
        "пять" to 5, "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9, "десять" to 10, "одиннадцать" to 11,
        "двенадцать" to 12, "тринадцать" to 13, "четырнадцать" to 14, "пятнадцать" to 15, "шестнадцать" to 16,
        "семнадцать" to 17, "восемнадцать" to 18, "девятнадцать" to 19,
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19
    )
    private val tens = mapOf(
        "двадцать" to 20, "тридцать" to 30, "сорок" to 40, "пятьдесят" to 50, "шестьдесят" to 60, "семьдесят" to 70,
        "восемьдесят" to 80, "девяносто" to 90,
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
    )
    private val hundreds = mapOf(
        "сто" to 100, "двести" to 200, "триста" to 300, "четыреста" to 400, "пятьсот" to 500, "шестьсот" to 600,
        "семьсот" to 700, "восемьсот" to 800, "девятьсот" to 900
    )
    private val thousands = setOf("тысяча", "тысячи", "тысяч", "thousand")

    fun isNumberWord(w: String): Boolean = w in units || w in tens || w in hundreds || w in thousands || w == "hundred" || w.all { it.isDigit() } && w.isNotEmpty()

    fun parse(words: List<String>, from: Int): Pair<Long, Int>? {
        var i = from
        var total = 0L
        var current = 0L
        var any = false
        var lastWasUnit = false
        while (i < words.size) {
            val w = words[i]
            when {
                w.isNotEmpty() && w.all { it.isDigit() } && w.length < 15 -> {
                    if (any) break
                    return w.toLong() to 1
                }
                w in hundreds -> { if (current % 1000 >= 100) break; current += hundreds.getValue(w); lastWasUnit = false }
                w == "hundred" -> { current = (if (current == 0L) 1 else current) * 100; lastWasUnit = false }
                w in tens -> { if (current % 100 >= 10) break; current += tens.getValue(w); lastWasUnit = false }
                w in units -> {
                    val v = units.getValue(w)
                    if (lastWasUnit || (v >= 10 && current % 100 != 0L) || (current % 10 != 0L)) break
                    current += v; lastWasUnit = true
                }
                w in thousands -> { total += (if (current == 0L) 1 else current) * 1000; current = 0; lastWasUnit = false }
                else -> break
            }
            any = true
            i++
        }
        if (!any) return null
        return (total + current) to (i - from)
    }

    fun parseAll(text: String): Long? {
        val ws = text.trim().lowercase().replace('ё', 'е').split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (ws.isEmpty()) return null
        val r = parse(ws, 0) ?: return null
        return if (r.second == ws.size) r.first else null
    }
}
