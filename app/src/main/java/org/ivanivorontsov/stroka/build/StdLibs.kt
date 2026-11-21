package org.ivanivorontsov.stroka.build

import java.io.File
object StdLibs {
    fun parse(stdJs: String): Map<String, String> {
        var t = stdJs.trim()
        if (t.startsWith("module.exports")) {
            val eq = t.indexOf('=')
            if (eq >= 0) t = t.substring(eq + 1).trim()
        }
        if (t.endsWith(";")) t = t.substring(0, t.length - 1).trim()
        return JsonTxt.parseStringMap(t)
    }

    fun scan(source: String): List<String> {
        val out = ArrayList<String>()
        val re = Regex("""подключи\s+(?:как\s+[^\s"]+\s+)?["']([^"']+)["']|подключи\s+["']([^"']+)["']""")
        val re2 = Regex("""подключи\s+["']([^"']+)["']""")
        for (m in re2.findAll(source)) out.add(m.groupValues[1])
        return out
    }

    fun normalizeStdName(path: String): String? {
        val sm = Regex("""^(?:стд|std)/([^/\\]+?)(?:\.pon)?$""", RegexOption.IGNORE_CASE).matchEntire(path.trim())
        if (sm != null) return sm.groupValues[1].lowercase()
        val bare = path.trim().removeSuffix(".pon").removeSuffix(".PON")
        if (!bare.contains('/') && !bare.contains('\\')) return bare.lowercase()
        return null
    }
}

class CompileEnv(
    val fileName: String = "программа.pon",
    val std: Map<String, String> = emptyMap(),
    val projectFiles: Map<String, String> = emptyMap(),
    val projectRoot: File? = null,
    val cacheDir: File? = null,
    val allowDownload: Boolean = true,
    val log: (String) -> Unit = {}
)

fun CompileEnv.withStd(assets: BuildAssets): CompileEnv {
    if (std.isNotEmpty()) return this
    if (assets.stdText.isBlank()) return this
    return CompileEnv(fileName, StdLibs.parse(assets.stdText), projectFiles, projectRoot, cacheDir ?: assets.cacheDir, allowDownload, log)
}
