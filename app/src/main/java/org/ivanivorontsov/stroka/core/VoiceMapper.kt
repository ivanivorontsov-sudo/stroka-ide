package org.ivanivorontsov.stroka.core

sealed class VoiceAction {
    data class Insert(val text: String) : VoiceAction()
    object NewLine : VoiceAction()
    object Indent : VoiceAction()
    object Outdent : VoiceAction()
    object Save : VoiceAction()
    object Run : VoiceAction()
    object Undo : VoiceAction()
    object Redo : VoiceAction()
    object SelectAll : VoiceAction()
    object Copy : VoiceAction()
    object Paste : VoiceAction()
    object Cut : VoiceAction()
    object Backspace : VoiceAction()
    object DeleteWord : VoiceAction()
    object DeleteLine : VoiceAction()
    object LineStart : VoiceAction()
    object LineEnd : VoiceAction()
    object Comment : VoiceAction()
    object StopListening : VoiceAction()
    object Close : VoiceAction()
    data class Find(val query: String) : VoiceAction()
    data class GoToLine(val line: Int) : VoiceAction()
}

object VoiceMapper {
    private enum class Glue { NONE, BEFORE, AFTER, BOTH }

    private sealed class Piece {
        data class Sym(val text: String, val glue: Glue) : Piece()
        data class Act(val action: VoiceAction) : Piece()
        data class Mod(val kind: String) : Piece()
    }

    private val phrases: Map<String, Piece> = buildMap {
        fun sym(text: String, glue: Glue, vararg names: String) { for (n in names) put(n, Piece.Sym(text, glue)) }
        fun act(a: VoiceAction, vararg names: String) { for (n in names) put(n, Piece.Act(a)) }
        fun mod(k: String, vararg names: String) { for (n in names) put(n, Piece.Mod(k)) }
        sym("(", Glue.BOTH, "скобка открыть", "открыть скобку", "открыть скобка", "открывающая скобка", "скобка открывается", "левая скобка", "круглая скобка", "скобка", "open paren", "open parenthesis", "left paren", "left parenthesis", "paren", "open bracket round")
        sym(")", Glue.BEFORE, "скобка закрыть", "закрыть скобку", "закрыть скобка", "закрывающая скобка", "скобка закрывается", "правая скобка", "close paren", "close parenthesis", "right paren", "right parenthesis")
        sym("()", Glue.BEFORE, "пустые скобки", "две скобки", "empty parens", "parens")
        sym("{", Glue.NONE, "фигурная скобка открыть", "открыть фигурную скобку", "открыть фигурную", "фигурная скобка", "фигурная открыть", "открывающая фигурная скобка", "open brace", "left brace", "open curly", "curly brace", "open curly brace")
        sym("}", Glue.NONE, "фигурная скобка закрыть", "закрыть фигурную скобку", "закрыть фигурную", "фигурная закрыть", "закрывающая фигурная скобка", "close brace", "right brace", "close curly", "close curly brace")
        sym("[", Glue.BOTH, "квадратная скобка открыть", "открыть квадратную скобку", "открыть квадратную", "квадратная скобка", "квадратная открыть", "open bracket", "left bracket", "open square bracket", "square bracket")
        sym("]", Glue.BEFORE, "квадратная скобка закрыть", "закрыть квадратную скобку", "закрыть квадратную", "квадратная закрыть", "close bracket", "right bracket", "close square bracket")
        sym("<", Glue.NONE, "меньше", "знак меньше", "less than", "less")
        sym(">", Glue.NONE, "больше", "знак больше", "greater than", "greater")
        sym("<=", Glue.NONE, "меньше или равно", "less or equal", "less than or equal")
        sym(">=", Glue.NONE, "больше или равно", "greater or equal", "greater than or equal")
        sym("\"", Glue.NONE, "кавычка", "кавычки", "двойная кавычка", "двойные кавычки", "quote", "quotes", "double quote")
        sym("'", Glue.NONE, "одинарная кавычка", "одинарные кавычки", "апостроф", "single quote", "apostrophe")
        sym("`", Glue.NONE, "обратная кавычка", "backtick", "back quote")
        sym(";", Glue.BEFORE, "точка с запятой", "semicolon", "semi colon")
        sym(":", Glue.BEFORE, "двоеточие", "colon")
        sym(",", Glue.BEFORE, "запятая", "comma")
        sym(".", Glue.BOTH, "точка", "dot", "period", "point")
        sym("...", Glue.BEFORE, "многоточие", "ellipsis")
        sym("=", Glue.NONE, "равно", "присвоить", "равняется", "equals", "equal", "assign", "equal sign")
        sym("==", Glue.NONE, "двойное равно", "равно равно", "равняется равняется", "double equals", "equals equals", "is equal to")
        sym("===", Glue.NONE, "тройное равно", "triple equals")
        sym("!=", Glue.NONE, "не равно", "not equal", "not equals", "does not equal")
        sym("+", Glue.NONE, "плюс", "plus")
        sym("-", Glue.NONE, "минус", "minus")
        sym("-", Glue.BOTH, "дефис", "тире", "hyphen", "dash")
        sym("*", Glue.NONE, "звездочка", "умножить", "умножить на", "star", "asterisk", "times")
        sym("/", Glue.NONE, "слэш", "слеш", "косая черта", "делить", "делить на", "разделить на", "slash", "divided by", "divide")
        sym("\\", Glue.BOTH, "обратный слэш", "обратный слеш", "бэкслэш", "backslash", "back slash")
        sym("%", Glue.NONE, "процент", "остаток", "percent", "modulo", "mod")
        sym("&", Glue.NONE, "амперсанд", "ampersand")
        sym("&&", Glue.NONE, "логическое и", "и и", "and and", "logical and")
        sym("||", Glue.NONE, "логическое или", "или или", "or or", "logical or")
        sym("|", Glue.NONE, "вертикальная черта", "пайп", "pipe", "vertical bar")
        sym("!", Glue.AFTER, "восклицательный знак", "отрицание", "exclamation mark", "exclamation", "bang")
        sym("?", Glue.BEFORE, "вопросительный знак", "question mark")
        sym("#", Glue.AFTER, "решетка", "решётка", "диез", "hash", "hashtag", "pound sign")
        sym("_", Glue.BOTH, "подчеркивание", "нижнее подчеркивание", "андерскор", "underscore")
        sym("$", Glue.AFTER, "доллар", "dollar", "dollar sign")
        sym("@", Glue.AFTER, "собака", "собачка", "at sign")
        sym("^", Glue.NONE, "крышка", "caret")
        sym("~", Glue.AFTER, "тильда", "tilde")
        sym("->", Glue.NONE, "стрелка", "arrow")
        sym("=>", Glue.NONE, "жирная стрелка", "толстая стрелка", "fat arrow")
        sym("+=", Glue.NONE, "плюс равно", "plus equals")
        sym("-=", Glue.NONE, "минус равно", "minus equals")
        sym("++", Glue.BEFORE, "плюс плюс", "инкремент", "plus plus", "increment")
        sym("--", Glue.BEFORE, "минус минус", "декремент", "minus minus", "decrement")
        sym("//", Glue.NONE, "комментарий", "двойной слэш", "comment slash", "double slash")
        sym(" ", Glue.BOTH, "пробел", "space")
        act(VoiceAction.NewLine, "новая строка", "новую строку", "с новой строки", "перенос строки", "перевод строки", "энтер", "ввод", "new line", "newline", "next line", "enter")
        act(VoiceAction.Indent, "отступ", "таб", "табуляция", "tab", "indent")
        act(VoiceAction.Outdent, "убрать отступ", "отступ назад", "минус отступ", "outdent", "dedent", "unindent")
        mod("camel", "кэмел", "кемел", "camel", "camel case", "кэмел кейс", "верблюжий")
        mod("pascal", "паскаль", "pascal", "pascal case", "паскаль кейс")
        mod("snake", "снейк", "снэйк", "змеиный", "snake", "snake case", "снейк кейс")
        mod("upper", "капс", "заглавными", "все заглавные", "caps", "all caps", "upper case", "constant")
        mod("cap", "с большой", "с большой буквы", "заглавная", "capital", "cap")
        mod("literal", "буквально", "дословно", "literal", "literally")
        mod("nospace", "слитно", "без пробела", "no space")
    }

    private val letters = mapOf(
        "икс" to "x", "игрек" to "y", "зет" to "z", "зэт" to "z", "эй" to "a", "би" to "b", "си" to "c", "ди" to "d",
        "и краткое" to "й", "ай" to "i", "джей" to "j", "кей" to "k", "эн" to "n", "пи" to "p", "кью" to "q", "ар" to "r", "эс" to "s", "ти" to "t",
        "ю" to "u", "ви" to "v", "дабл ю" to "w"
    )

    private val maxPhraseWords = phrases.keys.maxOf { it.split(' ').size }

    private val commandTable: List<Pair<Set<String>, VoiceAction>> = listOf(
        setOf("сохранить", "сохрани", "сохраняй", "сохранить файл", "сохрани файл", "save", "save file") to VoiceAction.Save,
        setOf("запустить", "запусти", "выполнить", "выполни", "пуск", "запуск", "run", "run code", "run it", "execute", "start") to VoiceAction.Run,
        setOf("отменить", "отмена", "отмени", "undo") to VoiceAction.Undo,
        setOf("повторить", "повтори", "вернуть", "верни", "redo") to VoiceAction.Redo,
        setOf("выделить все", "выдели все", "select all") to VoiceAction.SelectAll,
        setOf("копировать", "скопировать", "скопируй", "copy") to VoiceAction.Copy,
        setOf("вставить", "вставь", "paste") to VoiceAction.Paste,
        setOf("вырезать", "вырежи", "cut") to VoiceAction.Cut,
        setOf("стереть", "сотри", "удалить символ", "backspace", "delete") to VoiceAction.Backspace,
        setOf("удалить слово", "удали слово", "стереть слово", "delete word") to VoiceAction.DeleteWord,
        setOf("удалить строку", "удали строку", "стереть строку", "delete line") to VoiceAction.DeleteLine,
        setOf("в начало строки", "начало строки", "home", "line start", "start of line") to VoiceAction.LineStart,
        setOf("в конец строки", "конец строки", "end", "line end", "end of line") to VoiceAction.LineEnd,
        setOf("закомментировать", "раскомментировать", "закомментируй", "toggle comment", "comment out", "comment line") to VoiceAction.Comment,
        setOf("стоп", "хватит", "стоп запись", "выключи микрофон", "stop", "stop listening") to VoiceAction.StopListening,
        setOf("закрыть", "закрой", "close") to VoiceAction.Close
    )

    private val findPrefixes = listOf("найти", "найди", "поиск", "искать", "find", "search for", "search")
    private val gotoPrefixes = listOf("перейти к строке", "перейди к строке", "перейти на строку", "перейди на строку", "к строке", "на строку", "строка", "go to line", "goto line", "jump to line", "line")
    private val commandPrefixes = listOf("команда", "command")

    fun normalize(s: String): String = s.lowercase().replace('ё', 'е').replace(Regex("[\\s\\u00A0]+"), " ").trim()

    fun parseCommand(utterance: String): VoiceAction? {
        var n = normalize(utterance).trimEnd('.', '!', '?', ',')
        var forced = false
        for (p in commandPrefixes) if (n.startsWith("$p ")) { n = n.removePrefix("$p ").trim(); forced = true }
        for ((names, a) in commandTable) if (n in names) return a
        for (p in gotoPrefixes) {
            if (n.startsWith("$p ")) {
                val rest = n.removePrefix("$p ").trim().removePrefix("номер ").removePrefix("number ")
                val num = NumberWords.parseAll(rest)
                if (num != null && num in 1..10_000_000) return VoiceAction.GoToLine(num.toInt())
            }
        }
        for (p in findPrefixes) {
            if (n == p) return VoiceAction.Find("")
            if (n.startsWith("$p ")) {
                val origWords = utterance.trim().trimEnd('.', '!', '?').split(Regex("\\s+"))
                val skip = p.split(' ').size + if (forced) 1 else 0
                return VoiceAction.Find(origWords.drop(skip).joinToString(" ").trim())
            }
        }
        return null
    }

    fun map(utterance: String): List<VoiceAction> {
        val trimmed = utterance.trim()
        if (trimmed.isEmpty()) return emptyList()
        parseCommand(trimmed)?.let { return listOf(it) }
        val fixed = fixCapitalization(trimmed).let { if (it.length > 1 && it.endsWith(".") && !it.endsWith("..") && it[it.length - 2].isLetter()) it.dropLast(1) else it }
        return dictate(fixed)
    }

    private fun fixCapitalization(s: String): String {
        val first = s.takeWhile { !it.isWhitespace() }
        return if (first.length > 1 && first[0].isUpperCase() && first.drop(1).all { !it.isUpperCase() }) s[0].lowercaseChar() + s.substring(1) else s
    }

    private fun splitTokens(s: String): List<String> {
        val res = ArrayList<String>()
        val sb = StringBuilder()
        fun flush() { if (sb.isNotEmpty()) { res.add(sb.toString()); sb.setLength(0) } }
        for (c in s) {
            when {
                c.isWhitespace() -> flush()
                c.isLetterOrDigit() || c == '_' -> sb.append(c)
                else -> {
                    if (c == '.' && sb.isNotEmpty() && sb.all { it.isDigit() }) { sb.append(c); continue }
                    flush(); res.add(c.toString())
                }
            }
        }
        flush()
        return res
    }

    fun dictate(s: String): List<VoiceAction> {
        val raw = splitTokens(s)
        val norm = raw.map { normalize(it) }
        val out = ArrayList<VoiceAction>()
        val sb = StringBuilder()
        var glueNext = true
        var quoteOpen = false
        var mod: String? = null
        val group = ArrayList<String>()
        var noSpace = false

        fun emitText(text: String, glueBefore: Boolean, glueAfter: Boolean) {
            if (sb.isNotEmpty() && !glueNext && !glueBefore && sb.last() != '\n') sb.append(' ')
            sb.append(text)
            glueNext = glueAfter
        }

        fun flushGroup() {
            if (group.isEmpty()) return
            val m = mod
            val word = when (m) {
                "camel" -> group.mapIndexed { i, w -> if (i == 0) w.lowercase() else w.lowercase().replaceFirstChar { it.uppercaseChar() } }.joinToString("")
                "pascal" -> group.joinToString("") { w -> w.lowercase().replaceFirstChar { it.uppercaseChar() } }
                "snake" -> group.joinToString("_") { it.lowercase() }
                "upper" -> group.joinToString("_") { it.uppercase() }
                else -> group.joinToString("")
            }
            emitText(word, false, false)
            group.clear()
            mod = null
        }

        fun flushText() {
            if (sb.isNotEmpty()) { out.add(VoiceAction.Insert(sb.toString())); sb.setLength(0) }
        }

        var i = 0
        while (i < raw.size) {
            var matched: Piece? = null
            var len = 0
            if (mod != "literal") {
                for (n in minOf(maxPhraseWords, raw.size - i) downTo 1) {
                    val key = norm.subList(i, i + n).joinToString(" ")
                    val p = phrases[key]
                    if (p != null) { matched = p; len = n; break }
                }
            }
            if (matched == null && mod != "literal") {
                for (n in minOf(2, raw.size - i) downTo 1) {
                    val key = norm.subList(i, i + n).joinToString(" ")
                    val l = letters[key]
                    if (l != null && (mod == null)) { emitText(l, noSpace, false); noSpace = false; len = n; break }
                }
                if (len > 0) { i += len; continue }
                if (NumberWords.isNumberWord(norm[i]) && mod == null) {
                    val r = NumberWords.parse(norm, i)
                    if (r != null && r.second > 0) {
                        emitText(r.first.toString(), noSpace, false); noSpace = false
                        i += r.second; continue
                    }
                }
            }
            when (val p = matched) {
                null -> {
                    val w = raw[i]
                    if (mod == "literal") { emitText(w, noSpace, false); mod = null; noSpace = false }
                    else if (mod == "cap") { emitText(w.replaceFirstChar { it.uppercaseChar() }, noSpace, false); mod = null; noSpace = false }
                    else if (mod != null) group.add(w)
                    else if (w.length == 1 && !w[0].isLetterOrDigit()) {
                        val g = when (w[0]) {
                            '(', '[' -> Glue.BOTH
                            ')', ']', ',', ';', ':', '?' -> Glue.BEFORE
                            '!', '#', '$', '@', '~' -> Glue.AFTER
                            '.', '_', '\\' -> Glue.BOTH
                            else -> Glue.NONE
                        }
                        emitText(w, g == Glue.BEFORE || g == Glue.BOTH, g == Glue.AFTER || g == Glue.BOTH)
                    } else { emitText(w, noSpace, false); noSpace = false }
                    i++
                }
                is Piece.Mod -> {
                    flushGroup()
                    if (p.kind == "nospace") noSpace = true else mod = p.kind
                    i += len
                }
                is Piece.Sym -> {
                    flushGroup()
                    if (p.text == "\"" || p.text == "'" || p.text == "`") {
                        if (!quoteOpen) emitText(p.text, false, true) else emitText(p.text, true, false)
                        quoteOpen = !quoteOpen
                    } else {
                        emitText(p.text, p.glue == Glue.BEFORE || p.glue == Glue.BOTH, p.glue == Glue.AFTER || p.glue == Glue.BOTH)
                    }
                    i += len
                }
                is Piece.Act -> {
                    flushGroup()
                    flushText()
                    out.add(p.action)
                    glueNext = true
                    i += len
                }
            }
        }
        flushGroup()
        flushText()
        return out
    }
}
