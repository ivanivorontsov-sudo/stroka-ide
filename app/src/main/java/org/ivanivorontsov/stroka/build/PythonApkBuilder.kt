package org.ivanivorontsov.stroka.build

import java.io.File
import java.util.zip.ZipFile

object PythonApkBuilder {
    @Volatile
    var runtimeApkOverride: File? = null

    fun build(
        source: String,
        fileName: String,
        label: String,
        work: File,
        assets: BuildAssets,
        outApk: File,
        log: (String) -> Unit = {}
    ): File {
        log("Собираю Python-приложение")
        val runtime = findRuntimeApk(assets)
            ?: throw BuildException(
                "Не найден APK Строки с средой Python (Chaquopy). " +
                    "Сборка .py в отдельный APK берёт среду из установленной Строки."
            )
        val extras = LinkedHashMap<String, ByteArray>()
        ZipFile(runtime).use { z ->
            val names = z.entries().toList().map { it.name }
            val abi = when {
                names.any { it.startsWith("lib/arm64-v8a/libpython") } -> "arm64-v8a"
                names.any { it.startsWith("lib/${Toolchain.hostAbi()}/libpython") } -> Toolchain.hostAbi()
                else -> names.mapNotNull { n ->
                    val m = Regex("^lib/([^/]+)/libpython").find(n)
                    m?.groupValues?.get(1)
                }.firstOrNull() ?: Toolchain.hostAbi()
            }
            log("Беру среду Python из Строки (ABI $abi)")
            val wanted = names.filter { n ->
                n.startsWith("assets/chaquopy/") ||
                    n == "lib/$abi/libpython3.12.so" ||
                    n == "lib/$abi/libchaquopy_java.so" ||
                    (n.startsWith("lib/$abi/") && (n.contains("python") || n.contains("chaquopy")) && n.endsWith(".so"))
            }
            if (wanted.none { it.startsWith("lib/$abi/libpython") }) {
                throw BuildException("В среде Строки нет библиотеки Python для $abi.")
            }
            for (n in wanted) {
                val data = z.getInputStream(z.getEntry(n)).readBytes()
                extras[n] = data
            }
        }
        extras["assets/py/program.py"] = programBytes(source)
        extras["assets/py/stroka_tkinter.py"] = loadShim(assets)
        extras["assets/py/stroka.py"] = loadShim(assets, "stroka.py")
        extras["assets/py/stroka_py_app.py"] = bootstrap().toByteArray(Charsets.UTF_8)
        extras["assets/py/FILENAME.txt"] = fileName.toByteArray(Charsets.UTF_8)
        val jars = ArrayList<File>()
        val chaquopyJar = assets.libDir?.let { File(it, "python/chaquopy_java-16.1.0.jar") }
        if (chaquopyJar != null && chaquopyJar.isFile) jars.add(chaquopyJar)
        if (jars.isEmpty()) {
            throw BuildException("Нет chaquopy_java.jar для упаковки Python-приложения.")
        }
        val main = pythonMain(assets)
        val pkg = ApkPacker.packageName(label)
        return RealApkBuilder.build(
            work = File(work, "apk"),
            label = label,
            pkg = pkg,
            versionName = "1.0",
            versionCode = 1,
            www = emptyMap(),
            mainJava = main,
            toolchain = assets.toolchain,
            key = assets.userKey,
            outApk = outApk,
            log = log,
            extraJars = jars,
            extras = extras,
            extractNativeLibs = true
        )
    }

    fun programBytes(source: String): ByteArray {
        val text = source.removePrefix("\uFEFF")
        val nul = text.indexOf('\u0000')
        if (nul >= 0) {
            val line = text.substring(0, nul).count { it == '\n' } + 1
            throw BuildException("В тексте программы есть нулевой символ (строка $line). Удалите его и сохраните файл заново.")
        }
        return text.toByteArray(Charsets.UTF_8)
    }

    fun findRuntimeApk(assets: BuildAssets): File? {
        runtimeApkOverride?.let { if (it.isFile) return it }
        System.getenv("STROKA_RUNTIME_APK")?.let { val f = File(it); if (f.isFile) return f }
        PonyatnyJs.appContext?.let {
            val p = File(it.packageCodePath)
            if (p.isFile) return p
        }
        assets.libDir?.let { lib ->
            val root = lib.parentFile?.parentFile?.parentFile
            val candidates = listOf(
                File(root, "build/outputs/apk/debug/app-debug.apk"),
                File(root, "build/outputs/apk/release/app-release.apk"),
                File("/home/box/work/stroka-ide/app/build/outputs/apk/debug/app-debug.apk"),
                File("/home/box/work/stroka-release/Stroka-1.8.0.apk")
            )
            candidates.firstOrNull { it.isFile }?.let { return it }
        }
        return null
    }

    fun loadShim(assets: BuildAssets, file: String = "stroka_tkinter.py"): ByteArray {
        val ctx = PonyatnyJs.appContext
        if (ctx != null) {
            try { return ctx.assets.open("python/$file").readBytes() } catch (_: Exception) {}
        }
        val rootCandidates = listOf(
            assets.libDir,
            assets.libDir?.parentFile,
            assets.libDir?.parentFile?.parentFile,
            assets.libDir?.parentFile?.parentFile?.parentFile,
            File("/home/box/work/stroka-ide/app/src/main"),
            File("/home/box/work/stroka-ide/app/src/main/assets"),
            File("/home/box/work/stroka-ide")
        )
        val rels = listOf(
            "python/$file",
            "assets/python/$file",
            "src/main/python/$file",
            "src/main/assets/python/$file",
            "app/src/main/python/$file",
            "app/src/main/assets/python/$file"
        )
        for (r in rootCandidates) {
            if (r == null) continue
            for (rel in rels) {
                val f = File(r, rel)
                if (f.isFile) return f.readBytes()
            }
        }
        throw BuildException("Не найден модуль $file.")
    }

    fun pythonMain(assets: BuildAssets): String {
        val ctx = PonyatnyJs.appContext
        if (ctx != null) {
            try { return BuildCoordinator.assetText(ctx, "toolchain/PythonMain.java") } catch (_: Exception) {}
        }
        val f = File(assets.libDir?.parentFile ?: File("."), "toolchain/PythonMain.java")
        if (f.isFile) return f.readText(Charsets.UTF_8)
        val alt = File("/home/box/work/stroka-ide/app/src/main/assets/toolchain/PythonMain.java")
        if (alt.isFile) return alt.readText(Charsets.UTF_8)
        throw BuildException("Нет шаблона PythonMain.java")
    }

    fun bootstrap(): String = """
import builtins, io, os, sys, traceback

_job = {}


class _Console(io.TextIOBase):
    def __init__(self, activity, err):
        self._a = activity
        self._err = err

    def writable(self):
        return True

    def write(self, s):
        s = str(s)
        if s:
            self._a.consoleWrite(s, self._err)
        return len(s)

    def flush(self):
        pass


def prepare(activity):
    py = os.path.join(activity.getFilesDir().getAbsolutePath(), "py")
    if py not in sys.path:
        sys.path.insert(0, py)
    sys.stdout = _Console(activity, False)
    sys.stderr = _Console(activity, True)
    name = "program.py"
    try:
        with open(os.path.join(py, "FILENAME.txt"), "r", encoding="utf-8-sig") as f:
            name = f.read().strip() or name
    except OSError:
        pass
    path = os.path.join(py, "program.py")
    with open(path, "r", encoding="utf-8-sig") as f:
        src = f.read()
    import stroka
    native = stroka.uses_native_ui(src)
    stroka._bind(activity, native)

    def _input(prompt=""):
        if prompt:
            sys.stdout.write(str(prompt))
        if stroka.is_native():
            sys.stderr.write("\ninput() недоступен в программе с интерфейсом Android: используйте поля EditText.\n")
            return ""
        line = activity.consoleInput(str(prompt))
        if line is None:
            raise EOFError("ввод закрыт")
        return str(line)

    builtins.input = _input
    from stroka_tkinter import install
    install(activity)
    sys.argv = [name]
    _job.update(src=src, name=name, path=path, activity=activity)
    return "native" if native else "console"


def _execute(ui):
    activity = _job["activity"]
    g = {"__name__": "__main__", "__file__": _job["path"], "__builtins__": builtins, "activity": activity}
    try:
        exec(compile(_job["src"], _job["name"], "exec"), g)
    except SystemExit:
        pass
    except BaseException:
        traceback.print_exc()
        if ui:
            activity.showConsole()
    finally:
        try:
            sys.stdout.flush()
        except Exception:
            pass
        if not ui:
            activity.consoleDone()


def run(activity=None):
    if not _job:
        prepare(activity)
    _execute(False)


def run_native(activity=None):
    _execute(True)
""".trimIndent() + "\n"
}
