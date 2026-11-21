package org.ivanivorontsov.stroka.core

/**
 * Converts spoken Russian code vocabulary to the native «Понятный» 2.5 syntax.
 * Unknown identifiers and phrases are preserved instead of being guessed.
 */
object PonyatnyVoiceMapper {
    private val phrases = linkedMapOf(
        "объяви переменную" to "пусть",
        "создай переменную" to "пусть",
        "переменная" to "пусть",
        "пускай" to "пусть",
        "создай константу" to "константа",
        "постоянная" to "константа",
        "напечатай" to "пиши",
        "напечатать" to "пиши",
        "печатай" to "пиши",
        "печать" to "пиши",
        "выводи" to "пиши",
        "вывод" to "пиши",
        "написать" to "пиши",
        "напиши" to "пиши",
        "выведи" to "пиши",
        "вывести" to "пиши",
        "покажи" to "пиши",
        "процедура" to "функция",
        "подпрограмма" to "функция",
        "вернуть" to "верни",
        "возврат" to "верни",
        "ежели" to "если",
        "коли" to "если",
        "покуда" to "пока",
        "повторяй" to "повтори",
        "конец блока" to "конец",
        "закончить блок" to "конец",
        "открыть скобку" to "(",
        "скобка открыть" to "(",
        "закрыть скобку" to ")",
        "скобка закрыть" to ")",
        "новая строка" to "\n",
        "следующая строка" to "\n",
        "равно" to "=",
        "присвоить" to "=",
        "не равно" to "!=",
        "меньше или равно" to "<=",
        "больше или равно" to ">=",
        "меньше" to "<",
        "больше" to ">",
        "плюс" to "+",
        "прибавить" to "+",
        "минус" to "-",
        "умножить на" to "*",
        "умножить" to "*",
        "разделить на" to "/",
        "делить на" to "/",
        "делить" to "/",
        "остаток от деления" to "%",
        "кричи на систему" to "предупреди",
        "ори на систему" to "предупреди",
        "шепни системе" to "отладка",
        "зуб даю" to "убедись",
        "мамой клянусь" to "убедись",
        "в любом случае" to "наконец",
        "на случай беды" to "попробуй",
        "считай до трех" to "пауза(3000)",
        "барабанная дробь" to "пауза(1000)",
        "в фоне" to "поток",
        "истина" to "истина",
        "ложь" to "ложь",
        "ничего" to "ничего",
        "затем" to "затем",
        "конец" to "конец"
    )

    private val wordAliases = mapOf(
        "напиши" to "пиши",
        "напечатай" to "пиши",
        "печатай" to "пиши",
        "печать" to "пиши",
        "выводи" to "пиши",
        "вывод" to "пиши",
        "выведи" to "пиши",
        "вывести" to "пиши",
        "ежели" to "если",
        "коли" to "если",
        "покуда" to "пока",
        "пускай" to "пусть",
        "процедура" to "функция",
        "подпрограмма" to "функция",
        "вернуть" to "верни",
        "возврат" to "верни"
    )

    fun map(utterance: String): String? {
        val original = utterance.trim()
        if (original.isEmpty()) return null
        if (VoiceMapper.normalize(original) == "стоп") return "стоп"
        var source = original.replace('ё', 'е')
        val output = Regex("^(?:пиши|напиши|напечатай|выведи|вывести|скажи)(?: на экран| текст| строку)?\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(original)
        if (output != null) {
            val value = output.groupValues[1].trim()
            if (value.startsWith("\"") || value.startsWith("«")) return "пиши " + value
            if (value.startsWith("переменную ")) return "пиши " + value.removePrefix("переменную ").trim()
            val isExpression = Regex("""[0-9()*/%<>=]|\b(плюс|минус|умножить|делить|равно|и|или|не|число|строка|длина|сумма|макс|мин)\b""")
                .containsMatchIn(value.lowercase())
            if (isExpression) source = "пиши " + value
            else return "пиши \"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        }
        for ((spoken, code) in phrases.entries.sortedByDescending { it.key.length }) {
            val regex = Regex("(?<![\\p{L}\\p{N}_])" + Regex.escape(spoken) + "(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
            source = regex.replace(source, code)
        }
        val tokens = Regex("[\\p{L}\\p{N}_]+|[^\\p{L}\\p{N}_]+").findAll(source).map { it.value }.toList()
        val words = tokens.map { it.lowercase() }
        val out = StringBuilder()
        var i = 0
        var changed = source != original
        while (i < tokens.size) {
            val token = tokens[i]
            val word = words[i]
            if (word.firstOrNull()?.isLetter() == true && NumberWords.isNumberWord(word)) {
                val parsed = NumberWords.parse(words, i)
                if (parsed != null && parsed.second > 0) {
                    out.append(parsed.first)
                    i += parsed.second
                    changed = true
                    continue
                }
            }
            val alias = wordAliases[word]
            if (alias != null) {
                out.append(alias)
                changed = true
            } else {
                out.append(token)
            }
            i++
        }
        val result = out.toString().trim()
        return if (changed && result.isNotBlank()) result else null
    }
}
