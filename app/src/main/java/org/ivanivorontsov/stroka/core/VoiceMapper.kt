package org.ivanivorontsov.stroka.core

sealed class VoiceAction {
    data class Insert(val text: String) : VoiceAction()
    data class InsertBlock(val title: String, val code: String) : VoiceAction()
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
    data class SetLiteralMode(val kind: String?) : VoiceAction()
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
        // Bilingual programming vocabulary: spoken Russian aliases emit canonical source tokens.
        sym("function", Glue.NONE, "функция", "функцию", "функшен", "фанкшн")
        sym("return", Glue.NONE, "возврат", "вернуть значение", "верни значение", "ретерн")
        sym("if", Glue.NONE, "если")
        sym("else", Glue.NONE, "иначе")
        sym("elif", Glue.NONE, "иначе если", "иначе условие")
        sym("for", Glue.NONE, "для цикла", "цикл for")
        sym("while", Glue.NONE, "пока")
        sym("class", Glue.NONE, "класс", "создать класс")
        sym("import", Glue.NONE, "импорт", "импортировать")
        sym("from", Glue.NONE, "из модуля")
        sym("as", Glue.NONE, "как псевдоним")
        sym("try", Glue.NONE, "попробовать", "блок try")
        sym("except", Glue.NONE, "обработать исключение", "блок except")
        sym("finally", Glue.NONE, "в любом случае", "блок finally")
        sym("throw", Glue.NONE, "выбросить исключение")
        sym("new", Glue.NONE, "создать экземпляр")
        sym("this", Glue.NONE, "текущий объект")
        sym("self", Glue.NONE, "сам объект")
        sym("const", Glue.NONE, "константа", "объявить константу")
        sym("let", Glue.NONE, "объявить let")
        sym("var", Glue.NONE, "объявить var")
        sym("async", Glue.NONE, "асинхронный", "асинхронно")
        sym("await", Glue.NONE, "ожидать результат", "дождаться результата")
        sym("true", Glue.NONE, "истина", "истинно")
        sym("false", Glue.NONE, "ложь", "ложно")
        sym("undefined", Glue.NONE, "не определено")
        sym("console.log", Glue.NONE, "console log", "консоль лог")
        sym("println", Glue.NONE, "print line", "печать строки")
        sym("public", Glue.NONE, "публичный", "открытый доступ")
        sym("private", Glue.NONE, "приватный", "закрытый доступ")
        sym("protected", Glue.NONE, "защищенный", "защищённый")
        sym("static", Glue.NONE, "статический")
        sym("void", Glue.NONE, "пустой тип", "тип void")
        sym("int", Glue.NONE, "целое число", "тип int")
        sym("float", Glue.NONE, "число с плавающей точкой", "тип float")
        sym("double", Glue.NONE, "двойная точность", "тип double")
        sym("string", Glue.NONE, "строковый тип", "тип string")
        sym("boolean", Glue.NONE, "логический тип", "тип boolean")
        sym("List", Glue.NONE, "тип list", "тип лист")
        sym("Map", Glue.NONE, "тип map", "ассоциативный массив")
        sym("**", Glue.NONE, "степень", "в степень", "to the power of", "power operator")
        sym("!==", Glue.NONE, "строго не равно", "strict not equal")
        sym("::", Glue.NONE, "оператор области видимости", "scope resolution")
        sym("{", Glue.NONE, "left curly bracket", "open curly bracket", "open curly brace")
        sym("}", Glue.NONE, "right curly bracket", "close curly bracket", "close curly brace")
        sym("[", Glue.BOTH, "open square bracket")
        sym("]", Glue.BEFORE, "close square bracket")
        sym("\"", Glue.NONE, "open quote", "close quote", "double quotes")
        sym("'", Glue.NONE, "single quotes")
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
        mod("kebab", "kebab", "kebab case", "кебаб", "кебаб кейс", "через дефис")
        mod("flat", "flat case", "flat", "слитно нижним регистром")
        mod("lower", "lowercase", "lower case", "нижний регистр", "строчными")
    }

    private val letters = mapOf(
        "икс" to "x", "игрек" to "y", "зет" to "z", "зэт" to "z", "эй" to "a", "би" to "b", "си" to "c", "ди" to "d",
        "и краткое" to "й", "ай" to "i", "джей" to "j", "кей" to "k", "эн" to "n", "пи" to "p", "кью" to "q", "ар" to "r", "эс" to "s", "ти" to "t",
        "ю" to "u", "ви" to "v", "дабл ю" to "w",
        "letter a" to "a", "letter ay" to "a", "letter bee" to "b", "letter b" to "b",
        "letter see" to "c", "letter cee" to "c", "letter c" to "c",
        "letter dee" to "d", "letter d" to "d", "letter ee" to "e", "letter e" to "e",
        "letter eff" to "f", "letter f" to "f", "letter gee" to "g", "letter g" to "g",
        "letter aitch" to "h", "letter h" to "h", "letter eye" to "i", "letter i" to "i",
        "letter jay" to "j", "letter j" to "j", "letter kay" to "k", "letter k" to "k",
        "letter ell" to "l", "letter l" to "l", "letter em" to "m", "letter m" to "m",
        "letter en" to "n", "letter n" to "n", "letter oh" to "o", "letter o" to "o",
        "letter pee" to "p", "letter p" to "p", "letter cue" to "q", "letter queue" to "q",
        "letter are" to "r", "letter r" to "r", "letter ess" to "s", "letter s" to "s",
        "letter tee" to "t", "letter t" to "t", "letter you" to "u", "letter u" to "u",
        "letter vee" to "v", "letter v" to "v", "letter double you" to "w", "letter w" to "w",
        "letter ex" to "x", "letter x" to "x", "letter why" to "y", "letter y" to "y",
        "letter zee" to "z", "letter zed" to "z", "letter z" to "z"
    )

    private val maxPhraseWords = phrases.keys.maxOf { it.split(' ').size }

    private val commandTable: List<Pair<Set<String>, VoiceAction>> = listOf(
        setOf("сохранить", "сохрани", "сохраняй", "сохранить файл", "сохрани файл", "сохранить документ", "save", "save file", "save document", "save current file", "save current document") to VoiceAction.Save,
        setOf("запустить", "запусти", "выполнить", "выполни", "пуск", "запуск", "запусти код", "запустить код", "выполни код", "выполнить код", "запусти программу", "старт", "run", "run code", "run it", "run program", "run current file", "execute", "execute code", "start", "start program", "run script") to VoiceAction.Run,
        setOf("отменить", "отмена", "отмени", "отмени последнее", "отменить последнее действие", "undo", "undo last") to VoiceAction.Undo,
        setOf("повторить", "повтори", "вернуть", "верни", "повтори действие", "redo", "redo last") to VoiceAction.Redo,
        setOf("выделить все", "выдели все", "выделить весь текст", "выдели весь текст", "select all", "select everything") to VoiceAction.SelectAll,
        setOf("копировать", "скопировать", "скопируй", "копировать текст", "скопировать текст", "copy", "copy text") to VoiceAction.Copy,
        setOf("вставить", "вставь", "вставить текст", "paste", "paste text") to VoiceAction.Paste,
        setOf("вырезать", "вырежи", "вырезать текст", "cut", "cut text") to VoiceAction.Cut,
        setOf("стереть", "сотри", "удалить символ", "backspace", "delete") to VoiceAction.Backspace,
        setOf("удалить слово", "удали слово", "стереть слово", "delete word") to VoiceAction.DeleteWord,
        setOf("удалить строку", "удали строку", "стереть строку", "delete line") to VoiceAction.DeleteLine,
        setOf("в начало строки", "начало строки", "home", "line start", "start of line") to VoiceAction.LineStart,
        setOf("в конец строки", "конец строки", "end", "line end", "end of line") to VoiceAction.LineEnd,
        setOf("закомментировать", "раскомментировать", "закомментируй", "toggle comment", "comment out", "comment line") to VoiceAction.Comment,
        setOf("стоп", "хватит", "стоп запись", "выключи микрофон", "остановить запись", "останови микрофон", "stop", "stop listening", "stop recording") to VoiceAction.StopListening,
        setOf("закрыть", "закрой", "close") to VoiceAction.Close
    )

    private val findPrefixes = listOf("найти", "найди", "поиск", "искать", "find", "search for", "search")
    private val gotoPrefixes = listOf("перейти к строке", "перейди к строке", "перейти на строку", "перейди на строку", "к строке", "на строку", "строка", "go to line", "goto line", "jump to line", "line")
    private val commandPrefixes = listOf("команда", "command")

    fun normalize(s: String): String = s.lowercase().replace('ё', 'е').replace(Regex("[\\s\\u00A0]+"), " ").trim()

    private fun commandForPhrase(phrase: String, allowFuzzy: Boolean): VoiceAction? {
        for ((names, action) in commandTable) if (phrase in names) return action
        if (!allowFuzzy || phrase.length < 4) return null

        val wordCount = phrase.split(' ').size
        if (wordCount !in 1..3) return null

        fun distance(a: String, b: String): Int {
            val row = IntArray(b.length + 1) { it }
            for (i in a.indices) {
                var diagonal = row[0]
                row[0] = i + 1
                for (j in b.indices) {
                    val above = row[j + 1]
                    row[j + 1] = minOf(
                        row[j + 1] + 1,
                        row[j] + 1,
                        diagonal + if (a[i] == b[j]) 0 else 1
                    )
                    diagonal = above
                }
            }
            return row[b.length]
        }

        val limit = when {
            phrase.length <= 6 -> 1
            phrase.length <= 14 -> 2
            else -> 2
        }
        var bestDistance = Int.MAX_VALUE
        var bestAction: VoiceAction? = null
        for ((names, action) in commandTable) {
            for (name in names) {
                val candidate = normalize(name)
                if (candidate.split(' ').size != wordCount) continue
                val d = distance(phrase, candidate)
                if (d < bestDistance) {
                    bestDistance = d
                    bestAction = action
                }
            }
        }
        return bestAction.takeIf { bestDistance in 1..limit }
    }

    fun parseCommand(utterance: String): VoiceAction? {
        var n = normalize(utterance).trim().trimEnd('.', '!', '?', ',', ';', ':')
        var forced = false
        for (p in commandPrefixes) {
            if (n.startsWith("$p ")) {
                n = n.removePrefix("$p ").trim()
                forced = true
                break
            }
        }
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
                val origWords = utterance.trim().trimEnd('.', '!', '?', ',', ';', ':').split(Regex("\\s+"))
                val skip = p.split(' ').size + if (forced) 1 else 0
                return VoiceAction.Find(origWords.drop(skip).joinToString(" ").trim())
            }
        }
        val commandText = n.removeSuffix(" пожалуйста").removeSuffix(" please").trim()
        return commandForPhrase(commandText, allowFuzzy = true)
    }

    fun bestCandidate(utterances: List<String>): String {
        val candidates = utterances.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return candidates.firstOrNull { parseCommand(it) != null } ?: candidates.firstOrNull().orEmpty()
    }

    fun recognitionHints(): List<String> {
        val commands = commandTable.flatMap { it.first }
        val codeWords = listOf(
            "print", "println", "input", "range", "len", "console", "console log", "log",
            "function", "func", "def", "lambda", "return", "yield", "class", "new", "this", "self",
            "import", "from", "as", "package", "public", "private", "protected", "static", "final",
            "if", "else", "elif", "for", "foreach", "while", "do", "break", "continue", "pass",
            "try", "except", "catch", "finally", "throw", "throws", "raise", "assert",
            "const", "let", "var", "async", "await", "true", "false", "null", "none", "undefined",
            "int", "long", "short", "float", "double", "decimal", "bool", "boolean", "char",
            "string", "str", "void", "object", "list", "dict", "map", "array", "tuple", "set",
            "extends", "implements", "interface", "enum", "main", "super", "global", "nonlocal",
            "with", "using", "python", "javascript", "typescript", "java", "kotlin", "c sharp",
            "c plus plus", "html", "css", "sql", "json", "xml", "regex", "open tag", "close tag"
        )
        val priorityPhrases = listOf(
            "скобка открыть", "открыть скобку", "скобка", "open paren", "open parenthesis",
            "close paren", "close parenthesis", "open brace", "close brace",
            "open bracket", "close bracket", "quote", "semicolon", "colon", "comma",
            "dot", "equals", "double equals", "not equal", "new line", "indent", "underscore",
            "нужна программа", "кусок кода", "часть кода", "блок кода", "фрагмент кода",
            "получить имя", "ввод имени", "веб форма", "форма обратной связи",
            "модальное окно", "диалоговое окно", "создай html страницу", "css оформление",
            "подключи библиотеку", "импортируй пакет", "сгенерируй функцию",
            "requests", "numpy", "pandas", "flask", "fastapi", "tkinter",
            "axios", "react", "express", "lodash", "date-fns",
            "сделай мне окно", "сделай окошко", "покажи окошко", "покажи окно программы",
            "выведи окно", "открой окошко", "покажи форму", "сделай интерфейс",
            "собери экран", "нарисуй интерфейс", "мне надо окно", "хочу чтобы было окно",
            "show window", "display window", "open window", "make me a window",
            "show form", "build interface", "show interface", "create a window"
        )
        return (priorityPhrases + commands + codeWords + phrases.keys).distinct().take(260)
    }

    fun map(utterance: String, preferredLanguage: Lang = Lang.PLAIN, fileName: String? = null, voiceMode: String = "blocks", literalMode: String? = null): List<VoiceAction> {
        val trimmed = utterance.trim()
        if (trimmed.isEmpty()) return emptyList()
        val normalized = normalize(trimmed)
        if (literalMode != null && listOf("конец ввода", "конец текста", "конец комментария", "завершить ввод", "end input", "end text", "end comment").contains(normalized)) {
            return listOf(VoiceAction.SetLiteralMode(null))
        }
        val marker = listOf("это ввод" to "text", "это текст" to "text", "буквальный ввод" to "text", "literal input" to "text", "this is input" to "text", "это комментарий" to "comment", "комментарий" to "comment", "this is a comment" to "comment")
            .firstOrNull { normalized == it.first || normalized.startsWith(it.first + " ") }
        if (marker != null) {
            val rest = trimmed.substringAfter(' ', "").substringAfter(' ', "").trim()
            val actions = mutableListOf<VoiceAction>(VoiceAction.SetLiteralMode(marker.second))
            if (rest.isNotBlank()) actions.add(VoiceAction.Insert(if (marker.second == "comment") commentText(rest, preferredLanguage) else rest))
            return actions
        }
        if (literalMode != null) return listOf(VoiceAction.Insert(if (literalMode == "comment") commentText(trimmed, preferredLanguage) else trimmed))
        if (voiceMode == "off") return listOf(VoiceAction.Insert(trimmed))
        VoiceCodeTemplates.generate(trimmed, preferredLanguage, fileName, fullConstructs = voiceMode == "constructs")?.let {
            return listOf(VoiceAction.InsertBlock(it.title, it.code))
        }
        parseCommand(trimmed)?.let { return listOf(it) }
        val fixed = fixCapitalization(trimmed).let { if (it.length > 1 && it.endsWith(".") && !it.endsWith("..") && it[it.length - 2].isLetter()) it.dropLast(1) else it }
        return dictate(fixed)
    }

    private fun commentText(text: String, lang: Lang): String = when (lang) {
        Lang.HTML -> "<!-- $text -->"
        Lang.JAVASCRIPT, Lang.TYPESCRIPT, Lang.JAVA, Lang.KOTLIN, Lang.CPP -> "// $text"
        else -> "# $text"
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
                "kebab" -> group.joinToString("-") { it.lowercase() }
                "flat" -> group.joinToString("") { it.lowercase() }
                "lower" -> group.joinToString("_") { it.lowercase() }
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
