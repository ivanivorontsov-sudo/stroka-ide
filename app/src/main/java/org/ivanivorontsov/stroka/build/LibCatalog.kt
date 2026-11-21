package org.ivanivorontsov.stroka.build

enum class LibKind { PONYATNY, JAVA, JS }

class MavenCoord(
    val group: String,
    val artifact: String,
    val version: String
) {
    fun fileName(): String = "$artifact-$version.jar"
    fun url(): String {
        val g = group.replace('.', '/')
        return "https://repo1.maven.org/maven2/$g/$artifact/$version/${fileName()}"
    }
}

class NpmCoord(
    val name: String,
    val version: String,
    val file: String
) {
    fun cdnUrl(): String = "https://cdn.jsdelivr.net/npm/$name@$version/$file"
    fun registryUrl(): String = "https://registry.npmjs.org/$name/$version"
}

class LibItem(
    val id: String,
    val kind: LibKind,
    val title: String,
    val license: String,
    val packages: List<String>,
    val bundled: String? = null,
    val sha256: String? = null,
    val maven: MavenCoord? = null,
    val npm: NpmCoord? = null,
    val dependsOn: List<String> = emptyList()
)

object LibCatalog {
    const val PONYATNY_STD_URL = "https://raw.githubusercontent.com/ivanivorontsov-sudo/ponyatny/main/src/biblioteka/"

    val items: List<LibItem> = listOf(
        LibItem("стд/математика", LibKind.PONYATNY, "Понятный: математика", "лицензия «Понятный»", listOf("математика"), sha256 = "c6c114ad0a4e28d5f20370180fc8637503b1b723bce786d6b60deb0cdf04e1e7"),
        LibItem("стд/строки", LibKind.PONYATNY, "Понятный: строки", "лицензия «Понятный»", listOf("строки"), sha256 = "7959b026f1c7e33c82ed0adb921ada7cad25e298a142c523884252f001665d8c"),
        LibItem("стд/списки", LibKind.PONYATNY, "Понятный: списки", "лицензия «Понятный»", listOf("списки"), sha256 = "c550738c69deae2227572e889c51d0d6539053af69b8709aafdb6b987cebc188"),
        LibItem("стд/случай", LibKind.PONYATNY, "Понятный: случай", "лицензия «Понятный»", listOf("случай"), sha256 = "c53775ee96266b2e768aa6b2492311d48041850d90a292e62a09db70277dff0d"),
        LibItem("стд/время", LibKind.PONYATNY, "Понятный: время", "лицензия «Понятный»", listOf("время"), sha256 = "e08497b6b946c099fa275a1a892c186b680ff3c5adbca3cc1485640c58d72c6b"),
        LibItem("стд/консоль", LibKind.PONYATNY, "Понятный: консоль", "лицензия «Понятный»", listOf("консоль"), sha256 = "f971ec8a9b8818a0b9e01c7ba90a557edbff2384d9c4bcc3709ceb68b2365a77"),
        LibItem("стд/файлы", LibKind.PONYATNY, "Понятный: файлы", "лицензия «Понятный»", listOf("файлы"), sha256 = "f683e8761d9a919e6e6be06c5b94309b4e60b5ab86c92d8ca241506c9b15aa00"),
        LibItem(
            "commons-lang3", LibKind.JAVA, "Apache Commons Lang 3.17.0", "Apache License 2.0",
            packages = listOf("org.apache.commons.lang3"),
            bundled = "java/commons-lang3-3.17.0.jar",
            sha256 = "6ee731df5c8e5a2976a1ca023b6bb320ea8d3539fbe64c8a1d5cb765127c33b4",
            maven = MavenCoord("org.apache.commons", "commons-lang3", "3.17.0")
        ),
        LibItem(
            "commons-text", LibKind.JAVA, "Apache Commons Text 1.13.0", "Apache License 2.0",
            packages = listOf("org.apache.commons.text"),
            bundled = "java/commons-text-1.13.0.jar",
            sha256 = "1e323a501127df78ed0987f345d69d65d0ea7fa3d4fb5b3f84aaeba3a8b20f38",
            maven = MavenCoord("org.apache.commons", "commons-text", "1.13.0"),
            dependsOn = listOf("commons-lang3")
        ),
        LibItem(
            "commons-math3", LibKind.JAVA, "Apache Commons Math 3.6.1", "Apache License 2.0",
            packages = listOf("org.apache.commons.math3"),
            bundled = "java/commons-math3-3.6.1.jar",
            sha256 = "1e56d7b058d28b65abd256b8458e3885b674c1d588fa43cd7d1cbb9c7ef2b308",
            maven = MavenCoord("org.apache.commons", "commons-math3", "3.6.1")
        ),
        LibItem(
            "gson", LibKind.JAVA, "Google Gson 2.11.0", "Apache License 2.0",
            packages = listOf("com.google.gson"),
            bundled = "java/gson-2.11.0.jar",
            sha256 = "57928d6e5a6edeb2abd3770a8f95ba44dce45f3b23b7a9dc2b309c581552a78b",
            maven = MavenCoord("com.google.code.gson", "gson", "2.11.0")
        ),
        LibItem(
            "commons-io", LibKind.JAVA, "Apache Commons IO 2.16.1", "Apache License 2.0",
            packages = listOf("org.apache.commons.io"),
            maven = MavenCoord("commons-io", "commons-io", "2.16.1")
        ),
        LibItem(
            "commons-codec", LibKind.JAVA, "Apache Commons Codec 1.17.1", "Apache License 2.0",
            packages = listOf("org.apache.commons.codec"),
            maven = MavenCoord("commons-codec", "commons-codec", "1.17.1")
        ),
        LibItem(
            "dayjs", LibKind.JS, "Day.js 1.11.13", "MIT License",
            packages = listOf("dayjs"),
            bundled = "js/dayjs.min.js",
            sha256 = "9cfdb93f38afcf2d076abecd66d32bfd3383cdf1967654ebc26a26605daf4173",
            npm = NpmCoord("dayjs", "1.11.13", "dayjs.min.js")
        )
    )

    fun javaForImport(imp: String): LibItem? {
        val p = imp.trim().trimEnd(';').removePrefix("import ").trim()
        return items.filter { it.kind == LibKind.JAVA }.firstOrNull { lib ->
            lib.packages.any { p == it || p.startsWith("$it.") }
        }
    }

    fun jsForName(name: String): LibItem? {
        val n = name.trim()
        return items.filter { it.kind == LibKind.JS }.firstOrNull { n == it.id || n in it.packages }
    }

    fun pony(name: String): LibItem? {
        val n = name.lowercase()
        return items.filter { it.kind == LibKind.PONYATNY }.firstOrNull { n in it.packages || it.id.endsWith("/$n") }
    }

    fun licenseText(): String = items.joinToString("\n") { "• ${it.title} — ${it.license}" }
}
