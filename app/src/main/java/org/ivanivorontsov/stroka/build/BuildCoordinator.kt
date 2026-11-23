package org.ivanivorontsov.stroka.build

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.nio.charset.StandardCharsets

object BuildCoordinator {
    fun assetText(ctx: Context, path: String): String =
        ctx.assets.open(path).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

    fun assetBytes(ctx: Context, path: String): ByteArray =
        ctx.assets.open(path).use { it.readBytes() }

    fun yadroJs(ctx: Context): String = assetText(ctx, "ponyatny/yadro.js")

    fun runScript(source: String, yadro: String, env: CompileEnv = CompileEnv()): String {
        val prepared = LibResolver.preparePonyatny(source, env)
        val quoted = JsonTxt.quote(source)
        val stdJson = JsonTxt.objectOf(prepared.std)
        val filesJson = JsonTxt.objectOf(prepared.projectFiles)
        return yadro + "\n" + PonyatnyJs.resolverJs() + "\n" +
            "var STROKA_STD = " + stdJson + ";\n" +
            "var STROKA_FILES = " + filesJson + ";\n" +
            "(function(){\n" +
            "  var src = " + quoted + ";\n" +
            "  var js = PonyatnyYadro.compile(src, { file: " + PonyatnyJs.json(prepared.fileName) + ", resolve: function(p, from){ return strokaResolve(p, from, STROKA_STD, STROKA_FILES); } });\n" +
            "  var __R = PonyatnyYadro.makeRuntime({\n" +
            "    write: function(s){ console.log(String(s)); },\n" +
            "    writeErr: function(s){ console.error(String(s)); },\n" +
            "    ask: function(q){ var v = window.prompt(q || '\u0412\u0432\u043e\u0434', ''); return v == null ? '' : v; },\n" +
            "    maxSteps: 5000000,\n" +
            "    args: [],\n" +
            "    system: window.StrokaSystem || null\n" +
            "  });\n" +
            "  (0, eval)(js);\n" +
            "})();\n"
    }

    fun prepare(ctx: Context): BuildAssets {
        val store = File(ctx.filesDir, "toolchain")
        store.mkdirs()
        val jar = File(store, "android.jar")
        val sums = try { assetText(ctx, "toolchain/SHA256SUMS.txt") } catch (_: Exception) { "" }
        val expected = sums.lineSequence()
            .map { it.trim().split(Regex("\\s+"), 2) }
            .firstOrNull { it.size == 2 && it[1].endsWith("android.jar") }
            ?.get(0)
        val needCopy = !jar.isFile || (expected != null && Toolchain.sha256(jar) != expected)
        if (needCopy) {
            ctx.assets.open("toolchain/android.jar").use { inp ->
                jar.outputStream().use { out -> inp.copyTo(out) }
            }
        }
        val lambdaStubs = File(store, "core-lambda-stubs.jar")
        if (!lambdaStubs.isFile) {
            ctx.assets.open("toolchain/core-lambda-stubs.jar").use { inp ->
                lambdaStubs.outputStream().use { out -> inp.copyTo(out) }
            }
        }
        val native = File(ctx.applicationInfo.nativeLibraryDir)
        val tc = Toolchain.fromNativeAndJar(native, jar)
            ?: throw BuildException(
                "Не найдены встроенные aapt2/zipalign в ${native.absolutePath}. " +
                    "Переустановите Строку. На этом устройстве нужны библиотеки под архитектуру телефона (arm64-v8a или x86_64)."
            )
        val err = tc.check()
        if (err != null) throw BuildException(err)
        val keyFile = File(store, "user-apps.p12")
        if (!keyFile.isFile) keyFile.writeBytes(assetBytes(ctx, "templates/user-apps.p12"))
        val runtime = linkedMapOf(
            "index.html" to assetBytes(ctx, "runtime/index.html"),
            "runtime.js" to assetBytes(ctx, "runtime/runtime.js"),
            "shell.js" to assetBytes(ctx, "runtime/shell.js")
        )
        val libDir = extractLibs(ctx, File(ctx.filesDir, "libs"))
        return BuildAssets(
            yadroText = yadroJs(ctx),
            runtime = runtime,
            mainJava = assetText(ctx, "toolchain/Main.java"),
            userKey = UserKey.load(keyFile),
            exeStub = assetBytes(ctx, "templates/stroka-qjs-stub.exe"),
            toolchain = tc,
            stdText = try { assetText(ctx, "ponyatny/std.js") } catch (_: Exception) { "" },
            libDir = libDir,
            cacheDir = File(ctx.filesDir, "lib-cache"),
            kotlinStdlib = File(libDir, "kotlin/kotlin-stdlib-2.4.0.jar").takeIf { it.isFile },
            setupStub = try { assetBytes(ctx, "templates/stroka-setup-stub.exe") } catch (_: Exception) { ByteArray(0) },
            qjsLinuxAmd64 = try { assetBytes(ctx, "templates/qjs-linux-x86_64") } catch (_: Exception) { ByteArray(0) }
        )
    }

    fun fromProject(root: File): BuildAssets {
        val tc = Toolchain.fromProject(root)
            ?: throw BuildException("Не найден набор сборки (aapt2, zipalign, android.jar).")
        val err = tc.check()
        if (err != null) throw BuildException(err)
        val yadro = File(root, "app/src/main/assets/ponyatny/yadro.js")
        val runtimeDir = File(root, "app/src/main/assets/runtime")
        val key = UserKey.load(File(root, "app/src/main/assets/templates/user-apps.p12"))
        val stub = File(root, "app/src/main/assets/templates/stroka-qjs-stub.exe")
        return BuildAssets(
            yadroText = yadro.readText(Charsets.UTF_8),
            runtime = mapOf(
                "index.html" to File(runtimeDir, "index.html").readBytes(),
                "runtime.js" to File(runtimeDir, "runtime.js").readBytes(),
                "shell.js" to File(runtimeDir, "shell.js").readBytes()
            ),
            mainJava = File(root, "app/src/main/assets/toolchain/Main.java").readText(Charsets.UTF_8),
            userKey = key,
            exeStub = stub.readBytes(),
            toolchain = tc,
            stdText = File(root, "app/src/main/assets/ponyatny/std.js").readText(Charsets.UTF_8),
            libDir = File(root, "app/src/main/assets/libs"),
            cacheDir = File(root, "app/build/lib-cache"),
            kotlinStdlib = File(root, "app/src/main/assets/libs/kotlin/kotlin-stdlib-2.4.0.jar").takeIf { it.isFile },
            setupStub = File(root, "app/src/main/assets/templates/stroka-setup-stub.exe").let { if (it.isFile) it.readBytes() else ByteArray(0) },
            qjsLinuxAmd64 = File(root, "app/src/main/assets/templates/qjs-linux-x86_64").let { if (it.isFile) it.readBytes() else ByteArray(0) }
        )
    }

    fun extractLibs(ctx: Context, dest: File): File {
        dest.mkdirs()
        fun copyDir(prefix: String) {
            val names = ctx.assets.list(prefix)
            if (names == null || names.isEmpty()) {
                try {
                    val rel = if (prefix.startsWith("libs/")) prefix.substring(5) else prefix
                    val target = File(dest, rel)
                    target.parentFile?.mkdirs()
                    ctx.assets.open(prefix).use { inp -> target.outputStream().use { out -> inp.copyTo(out) } }
                } catch (_: Exception) {
                }
                return
            }
            for (n in names) copyDir("$prefix/$n")
        }
        try { copyDir("libs") } catch (_: Exception) {}
        return dest
    }

    fun writeToTree(ctx: Context, tree: Uri, name: String, data: ByteArray): Uri {
        val dir = DocumentFile.fromTreeUri(ctx, tree) ?: throw BuildException("Не удалось открыть выбранную папку.")
        val existing = dir.findFile(name)
        existing?.delete()
        val mime = when {
            name.endsWith(".apk", true) -> "application/vnd.android.package-archive"
            name.endsWith(".exe", true) -> "application/vnd.microsoft.portable-executable"
            else -> "application/octet-stream"
        }
        val created = dir.createFile(mime, name) ?: throw BuildException("Не удалось создать файл «$name» в выбранной папке. Проверьте доступ к папке.")
        val uri = created.uri
        ctx.contentResolver.openOutputStream(uri, "w")?.use { it.write(data) }
            ?: throw BuildException("Не удалось записать «$name» в выбранную папку.")
        return uri
    }

    fun mimeOpen(ctx: Context, uri: Uri) {
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(uri, ctx.contentResolver.getType(uri) ?: "*/*")
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Открыть"))
    }
}
