package org.ivanivorontsov.stroka.core

enum class Lang(
    val title: String,
    val exts: List<String>,
    val lineComment: String?,
    val blockStart: String?,
    val blockEnd: String?
) {
    PLAIN("Текст", listOf("txt", "log", "cfg", "ini", "toml", "yml", "yaml", "gitignore"), null, null, null),
    PYTHON("Python", listOf("py", "pyw"), "#", null, null),
    JAVASCRIPT("JavaScript", listOf("js", "mjs", "cjs", "jsx"), "//", "/*", "*/"),
    TYPESCRIPT("TypeScript", listOf("ts", "tsx", "mts"), "//", "/*", "*/"),
    HTML("HTML", listOf("html", "htm", "xml", "svg", "xhtml"), null, "<!--", "-->"),
    CSS("CSS", listOf("css", "scss", "less"), null, "/*", "*/"),
    KOTLIN("Kotlin", listOf("kt", "kts"), "//", "/*", "*/"),
    JAVA("Java", listOf("java"), "//", "/*", "*/"),
    C("C", listOf("c", "h"), "//", "/*", "*/"),
    CPP("C++", listOf("cpp", "cc", "cxx", "hpp", "hh", "hxx", "ino"), "//", "/*", "*/"),
    JSON("JSON", listOf("json", "jsonc"), null, null, null),
    MARKDOWN("Markdown", listOf("md", "markdown"), null, null, null),
    GDSCRIPT("GDScript", listOf("gd"), "#", null, null),
    ASM6502("6502 asm", listOf("asm", "s", "a65", "inc", "dasm"), ";", null, null);

    val runnable: Boolean get() = this == PYTHON || this == JAVASCRIPT || this == HTML

    val colonIndents: Boolean get() = this == PYTHON || this == GDSCRIPT

    companion object {
        fun forName(name: String): Lang {
            val lower = name.lowercase()
            if (lower == "makefile" || lower == "dockerfile") return PLAIN
            val ext = lower.substringAfterLast('.', "")
            return entries.firstOrNull { ext in it.exts } ?: PLAIN
        }
    }
}
