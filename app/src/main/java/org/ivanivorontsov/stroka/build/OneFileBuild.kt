package org.ivanivorontsov.stroka.build

import java.io.File

class BuildAssets(
    val yadroText: String,
    val runtime: Map<String, ByteArray>,
    val mainJava: String,
    val userKey: UserKey,
    val exeStub: ByteArray,
    val toolchain: Toolchain
)

object OneFileBuild {
    fun buildApk(
        source: String,
        fileName: String,
        label: String,
        work: File,
        assets: BuildAssets,
        outApk: File,
        log: (String) -> Unit = {}
    ): File {
        val err = assets.toolchain.check()
        if (err != null) throw BuildException(err)
        work.mkdirs()
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".java") || looksLikeAndroidJava(source) ->
                buildJavaApk(source, fileName, label, work, assets, outApk, log)
            lower.endsWith(".kt") || lower.endsWith(".kts") ->
                throw BuildException("Сборка Kotlin в APK на телефоне пока требует исходник на Java (один файл Activity) или программу на «Понятном». Сохраните программу как .java или .pon.")
            else -> buildWebApk(source, fileName, label, work, assets, outApk, log)
        }
    }

    fun buildExe(
        source: String,
        fileName: String,
        label: String,
        work: File,
        assets: BuildAssets,
        outExe: File,
        log: (String) -> Unit = {}
    ): File {
        log("Компилирую программу для Windows")
        val lower = fileName.lowercase()
        val js: String
        val mode: String
        if (lower.endsWith(".js")) {
            js = source
            mode = PonyatnyJs.detectMode(source, source)
        } else {
            js = PonyatnyJs.compile(source, assets.yadroText)
            mode = PonyatnyJs.detectMode(source, js)
        }
        work.mkdirs()
        val files = linkedMapOf(
            "program.js" to js.toByteArray(Charsets.UTF_8),
            "meta.txt" to "title=$label\nmode=$mode\n".toByteArray(Charsets.UTF_8)
        )
        if (mode != "calc") {
            files["boot.js"] = (
                "if (typeof console === 'object' && console.log) { }\n"
            ).toByteArray(Charsets.UTF_8)
        }
        return StrokaExe.pack(assets.exeStub, files, outExe)
    }

    fun buildWebApk(
        source: String,
        fileName: String,
        label: String,
        work: File,
        assets: BuildAssets,
        outApk: File,
        log: (String) -> Unit = {}
    ): File {
        log("Компилирую «Понятный»")
        val lower = fileName.lowercase()
        val js = when {
            lower.endsWith(".js") -> source
            lower.endsWith(".html") || lower.endsWith(".htm") -> null
            else -> PonyatnyJs.compile(source, assets.yadroText)
        }
        val www = HashMap<String, ByteArray>()
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            www["index.html"] = source.toByteArray(Charsets.UTF_8)
        } else {
            val compiled = js ?: throw BuildException("Нет JavaScript для оболочки.")
            val mode = PonyatnyJs.detectMode(source, compiled)
            val wrap = PonyatnyJs.wrap(compiled, label, mode)
            www.putAll(assets.runtime)
            www["yadro.js"] = assets.yadroText.toByteArray(Charsets.UTF_8)
            www["program.js"] = wrap.toByteArray(Charsets.UTF_8)
        }
        val pkg = ApkPacker.packageName(label)
        val java = assets.mainJava.ifBlank { throw BuildException("Нет шаблона Main.java") }
        return RealApkBuilder.build(
            work = File(work, "apk"),
            label = label,
            pkg = pkg,
            versionName = "1.0",
            versionCode = 1,
            www = www,
            mainJava = java,
            toolchain = assets.toolchain,
            key = assets.userKey,
            outApk = outApk,
            log = log
        )
    }

    fun buildJavaApk(
        source: String,
        fileName: String,
        label: String,
        work: File,
        assets: BuildAssets,
        outApk: File,
        log: (String) -> Unit = {}
    ): File {
        log("Собираю Java-приложение")
        val pkg = packageOf(source) ?: ApkPacker.packageName(label)
        val className = classOf(source) ?: "Main"
        if (!source.contains("extends Activity") && !source.contains("extends android.app.Activity")) {
            throw BuildException("Для APK из Java нужен класс, который наследует android.app.Activity (например public class Main extends Activity).")
        }
        val java = if (source.contains("package ")) source else "package $pkg;\n\n$source"
        return RealApkBuilder.build(
            work = File(work, "apk"),
            label = label,
            pkg = pkg,
            versionName = "1.0",
            versionCode = 1,
            www = emptyMap(),
            mainJava = java.replaceFirst(Regex("public\\s+class\\s+" + Regex.escape(className)), "public class Main"),
            toolchain = assets.toolchain,
            key = assets.userKey,
            outApk = outApk,
            log = log
        )
    }

    fun looksLikeAndroidJava(source: String): Boolean =
        source.contains("android.app.Activity") || (source.contains("extends Activity") && source.contains("import android."))

    fun packageOf(source: String): String? =
        Regex("""^\s*package\s+([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)\s*;""", RegexOption.MULTILINE)
            .find(source)?.groupValues?.get(1)

    fun classOf(source: String): String? =
        Regex("""\bclass\s+([A-Za-z_]\w*)""").find(source)?.groupValues?.get(1)

    fun buildApk(
        source: String,
        fileName: String,
        label: String,
        work: File,
        yadroJs: File,
        runtimeDir: File,
        mainJava: String,
        toolchain: Toolchain,
        key: UserKey,
        outApk: File,
        node: File = File("/usr/local/bin/node"),
        log: (String) -> Unit = {}
    ): File {
        val assets = BuildAssets(
            yadroText = yadroJs.readText(Charsets.UTF_8),
            runtime = WwwBundle.fromRuntime(runtimeDir, yadroJs, " ").filterKeys { it != "program.js" && it != "yadro.js" },
            mainJava = mainJava,
            userKey = key,
            exeStub = ByteArray(0),
            toolchain = toolchain
        )
        return buildApk(source, fileName, label, work, assets, outApk, log)
    }
}
