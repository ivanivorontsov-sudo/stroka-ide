package org.ivanivorontsov.stroka.build

import java.io.File

class BuildAssets(
    val yadroText: String,
    val runtime: Map<String, ByteArray>,
    val mainJava: String,
    val userKey: UserKey,
    val exeStub: ByteArray,
    val toolchain: Toolchain,
    val stdText: String = "",
    val libDir: java.io.File? = null,
    val cacheDir: java.io.File? = null,
    val setupStub: ByteArray = ByteArray(0),
    val qjsLinuxAmd64: ByteArray = ByteArray(0)
)

object OneFileBuild {
    fun buildApk(
        source: String,
        fileName: String,
        label: String,
        work: File,
        assets: BuildAssets,
        outApk: File,
        log: (String) -> Unit = {},
        env: CompileEnv = CompileEnv()
    ): File {
        val err = assets.toolchain.check()
        if (err != null) throw BuildException(err)
        work.mkdirs()
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".java") || looksLikeAndroidJava(source) ->
                buildJavaApk(source, fileName, label, work, assets, outApk, log)
            lower.endsWith(".py") ->
                PythonApkBuilder.build(source, fileName, label, work, assets, outApk, log)
            lower.endsWith(".kt") || lower.endsWith(".kts") ->
                throw BuildException("Сборка Kotlin в APK на телефоне пока требует исходник на Java (один файл Activity) или программу на «Понятном». Сохраните программу как .java или .pon.")
            else -> buildWebApk(source, fileName, label, work, assets, outApk, log, env)
        }
    }

    fun buildExe(
        source: String,
        fileName: String,
        label: String,
        work: File,
        assets: BuildAssets,
        outExe: File,
        log: (String) -> Unit = {},
        env: CompileEnv = CompileEnv()
    ): File {
        log("Компилирую программу для Windows")
        val lower = fileName.lowercase()
        val js: String
        val mode: String
        if (lower.endsWith(".js")) {
            js = withJsLibs(source, assets, env, log)
            mode = PonyatnyJs.detectMode(source, source)
        } else {
            js = PonyatnyJs.compile(source, assets.yadroText, env.withStd(assets))
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


    fun buildSetup(
        source: String,
        fileName: String,
        label: String,
        work: File,
        assets: BuildAssets,
        outSetup: File,
        log: (String) -> Unit = {},
        env: CompileEnv = CompileEnv()
    ): File {
        if (assets.setupStub.size < 64) throw BuildException("Нет шаблона установщика Windows.")
        log("Собираю установщик Windows")
        val exe = File(work, "$label.exe")
        buildExe(source, fileName, label, File(work, "exe"), assets, exe, log, env)
        val files = linkedMapOf(
            exe.name to exe.readBytes(),
            "meta.txt" to "title=$label\nid=${DebPacker.debName(label)}\nexe=${exe.name}\npublisher=Строка\nversion=1.0\n".toByteArray(Charsets.UTF_8)
        )
        return SetupPacker.pack(assets.setupStub, files, outSetup)
    }

    fun buildDeb(
        source: String,
        fileName: String,
        label: String,
        work: File,
        assets: BuildAssets,
        outDeb: File,
        log: (String) -> Unit = {},
        env: CompileEnv = CompileEnv()
    ): File {
        if (assets.qjsLinuxAmd64.size < 64) {
            throw BuildException("Нет встроенного QuickJS для Linux x86_64. Пакет .deb для ARM в этой версии не собирается.")
        }
        val lower = fileName.lowercase()
        val js: String
        if (lower.endsWith(".js")) js = withJsLibs(source, assets, env, log)
        else js = PonyatnyJs.compile(source, assets.yadroText, env.withStd(assets))
        work.mkdirs()
        val files = linkedMapOf("program.js" to js.toByteArray(Charsets.UTF_8))
        val icon = PngIcon.launcher(48)
        return DebPacker.build(label, "1.0", assets.qjsLinuxAmd64, files, icon, outDeb, log)
    }

    fun buildWebApk(
        source: String,
        fileName: String,
        label: String,
        work: File,
        assets: BuildAssets,
        outApk: File,
        log: (String) -> Unit = {},
        env: CompileEnv = CompileEnv()
    ): File {
        log("Компилирую «Понятный»")
        val lower = fileName.lowercase()
        val js = when {
            lower.endsWith(".js") -> withJsLibs(source, assets, env, log)
            lower.endsWith(".html") || lower.endsWith(".htm") -> null
            else -> PonyatnyJs.compile(source, assets.yadroText, env.withStd(assets))
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
        val jars = LibResolver.javaJars(LibResolver.javaImports(java), assets.libDir, assets.cacheDir, true, log)
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
            log = log,
            extraJars = jars
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

    fun withJsLibs(source: String, assets: BuildAssets, env: CompileEnv, log: (String) -> Unit): String {
        val libs = LibResolver.jsFiles(LibResolver.jsRequires(source), assets.libDir, assets.cacheDir, true, log)
        if (libs.isEmpty()) return source
        val b = StringBuilder()
        for ((_, data) in libs) {
            b.append(data.toString(Charsets.UTF_8)).append("\n")
        }
        b.append(source)
        return b.toString()
    }
}
