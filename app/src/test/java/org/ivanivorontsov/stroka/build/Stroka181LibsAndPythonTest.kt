package org.ivanivorontsov.stroka.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class Stroka181LibsAndPythonTest {
    
    private fun runCompiled(root: File, compiled: String, work: File, err: String): String {
        val yadro = File(root, "app/src/main/assets/ponyatny/yadro.js").readText(Charsets.UTF_8)
        val boot = "var module = { exports: {} };\nvar exports = module.exports;\n" + yadro + "\n" +
            "var PonyatnyYadro = module.exports;\n" +
            "var __R = PonyatnyYadro.makeRuntime({\n" +
            "  write: function(s){ process.stdout.write(String(s) + \"\\n\"); },\n" +
            "  writeErr: function(s){ process.stderr.write(String(s) + \"\\n\"); },\n" +
            "  ask: function(q){ return \"\"; },\n" +
            "  maxSteps: 5000000,\n" +
            "  args: []\n" +
            "});\n" + compiled
        val jsFile = File(work, "run.js")
        jsFile.writeText(boot, Charsets.UTF_8)
        val node = File("/usr/local/bin/node").takeIf { it.isFile } ?: File("/usr/bin/node")
        return Proc.require(listOf(node.absolutePath, jsFile.absolutePath), work, err = err)
    }


    private val stdPrograms = linkedMapOf(
        "математика" to "подключи \"стд/математика\"\nскажи факториал(5)\nскажи чётное(4)\n",
        "строки" to "подключи \"стд/строки\"\nскажи палиндром(\"шалаш\")\nскажи с_большой(\"привет\")\n",
        "списки" to "подключи \"стд/списки\"\nскажи уникальные([1, 1, 2, 3])\n",
        "случай" to "подключи \"стд/случай\"\nскажи кубик()\n",
        "время" to "подключи \"стд/время\"\nскажи две_цифры(7)\nскажи високосный(2024)\n",
        "консоль" to "подключи \"стд/консоль\"\nскажи деньги(10)\n",
        "файлы" to "подключи \"стд/файлы\"\nскажи \"готово\"\n"
    )

    @Test
    fun compilesAndBuildsEachStdLibraryForApkAndExeAndRuns() {
        val root = PonyatnyCalculatorBuildTest.locateRoot()
        val assets = BuildCoordinator.fromProject(root)
        val env0 = CompileEnv().withStd(assets)
        val node = File("/usr/local/bin/node").takeIf { it.isFile } ?: File("/usr/bin/node")
        assumeTrue("Нужен node, чтобы выполнить программу «Понятный»", node.isFile)
        val workRoot = File(root, "app/build/test-181-std")
        workRoot.deleteRecursively()
        workRoot.mkdirs()
        for ((name, src) in stdPrograms) {
            val env = CompileEnv(fileName = "$name.pon", std = env0.std)
            val js = PonyatnyJs.compile(src, assets.yadroText, env)
            assertTrue("JS для стд/$name пуст", js.isNotBlank())
            val dir = File(workRoot, name)
            dir.mkdirs()
            if (name != "файлы") {
                val out = runCompiled(root, js, dir, "Не выполнилась программа со стд/$name")
                println("стд/$name run: ${out.trim()}")
                when (name) {
                    "математика" -> {
                        assertTrue(out.contains("120"))
                        assertTrue(out.contains("истина") || out.contains("true") || out.contains("да"))
                    }
                    "строки" -> assertTrue(out.contains("истина") || out.contains("true") || out.contains("Привет") || out.contains("привет"))
                    "списки" -> assertTrue(out.contains("1") && out.contains("2") && out.contains("3"))
                    "время" -> {
                        assertTrue(out.contains("07"))
                        assertTrue(out.contains("истина") || out.contains("true") || out.contains("да"))
                    }
                    "консоль" -> assertTrue(out.isNotBlank())
                    "случай" -> assertTrue(out.isNotBlank())
                }
            }
            val apk = OneFileBuild.buildApk(src, "$name.pon", "Стд$name", File(dir, "apk"), assets, File(dir, "$name.apk"))
            assertTrue(apk.isFile && apk.length() > 1000)
            val exe = OneFileBuild.buildExe(src, "$name.pon", "Стд$name", File(dir, "exe"), assets, File(dir, "$name.exe"))
            assertTrue(exe.isFile && exe.length() > assets.exeStub.size)
            println("стд/$name APK ${apk.length()} EXE ${exe.length()}")
        }
    }

    @Test
    fun compilesRelativePonyatnyImportIntoApkAndExeAndRuns() {
        val root = PonyatnyCalculatorBuildTest.locateRoot()
        val assets = BuildCoordinator.fromProject(root)
        val node = File("/usr/local/bin/node").takeIf { it.isFile } ?: File("/usr/bin/node")
        assumeTrue(node.isFile)
        val work = File(root, "app/build/test-181-rel")
        work.deleteRecursively()
        work.mkdirs()
        val lib = "функция удвоить(н)\n    верни н * 2\nконец\n"
        val src = "подключи \"lib.pon\"\nскажи удвоить(21)\n"
        File(work, "lib.pon").writeText(lib, Charsets.UTF_8)
        File(work, "main.pon").writeText(src, Charsets.UTF_8)
        val env = CompileEnv(
            fileName = "main.pon",
            std = CompileEnv().withStd(assets).std,
            projectFiles = mapOf("main.pon" to src, "lib.pon" to lib),
            projectRoot = work
        )
        val js = PonyatnyJs.compile(src, assets.yadroText, env)
        val out = runCompiled(root, js, work, "Не выполнился относительный импорт")
        assertTrue("ожидали 42, получили: $out", out.contains("42"))
        val apk = OneFileBuild.buildApk(src, "main.pon", "Относительный", File(work, "apk"), assets, File(work, "rel.apk"), env = env)
        val exe = OneFileBuild.buildExe(src, "main.pon", "Относительный", File(work, "exe"), assets, File(work, "rel.exe"), env = env)
        assertTrue(apk.isFile && apk.length() > 1000)
        assertTrue(exe.isFile && exe.length() > assets.exeStub.size)
    }

    @Test
    fun buildsJavaApkWithBundledCommonsLang() {
        val root = PonyatnyCalculatorBuildTest.locateRoot()
        val assets = BuildCoordinator.fromProject(root)
        val src = File(root, "tools/fixtures/CommonsLangDemo.java").readText(Charsets.UTF_8)
        val work = File(root, "app/build/test-181-javalib")
        work.deleteRecursively()
        val apk = OneFileBuild.buildApk(src, "CommonsLangDemo.java", "LangDemo", work, assets, File(work, "lang.apk"))
        assertTrue(apk.isFile && apk.length() > 1000)
        ZipFile(apk).use { z ->
            val names = z.entries().toList().map { it.name }
            assertTrue("в APK нет classes.dex", names.any { it == "classes.dex" || it.endsWith("classes.dex") })
        }
    }

    @Test
    fun loadsBundledJavaLibAndReportsOfflineError() {
        val root = PonyatnyCalculatorBuildTest.locateRoot()
        val assets = BuildCoordinator.fromProject(root)
        val jars = LibResolver.javaJars(listOf("org.apache.commons.lang3.StringUtils"), assets.libDir, assets.cacheDir, false) { }
        assertTrue("встроенный Commons Lang должен находиться без сети", jars.any { it.name.contains("commons-lang3") })
        try {
            LibResolver.javaJars(listOf("org.apache.commons.io.FileUtils"), assets.libDir, File(root, "app/build/empty-lib-cache").apply {
                deleteRecursively(); mkdirs()
            }, false) { }
            throw AssertionError("ожидали ошибку офлайн для незакэшированной библиотеки")
        } catch (e: BuildException) {
            val m = e.message ?: ""
            assertTrue(m, m.contains("Нет сети") && m.contains("не сохранена в кэше"))
        }
    }

    @Test
    fun tkinterCalculatorWorksOnHostPython() {
        val root = PonyatnyCalculatorBuildTest.locateRoot()
        val py = File("/usr/bin/python3")
        assumeTrue(py.isFile)
        val script = File(root, "tools/tkinter_host_test.py")
        val out = Proc.require(listOf(py.absolutePath, script.absolutePath, root.absolutePath), root, err = "Хост-тест tkinter не прошёл")
        assertTrue(out, out.contains("OK 15"))
    }

    @Test
    fun packsPythonTkinterCalculatorApk() {
        val root = PonyatnyCalculatorBuildTest.locateRoot()
        val assets = BuildCoordinator.fromProject(root)
        val runtime = PythonApkBuilder.findRuntimeApk(assets)
        assumeTrue("Нужен APK Строки со средой Chaquopy (debug или 1.8.0)", runtime != null && runtime.isFile)
        PythonApkBuilder.runtimeApkOverride = runtime
        val src = File(root, "tools/fixtures/калькулятор-tkinter.py").readText(Charsets.UTF_8)
        val work = File(root, "app/build/test-181-pyapk")
        work.deleteRecursively()
        val apk = OneFileBuild.buildApk(src, "калькулятор-tkinter.py", "Калькулятор", work, assets, File(work, "py-calc.apk"))
        assertTrue(apk.isFile)
        println("Python APK: ${apk.length()} байт")
        ZipFile(apk).use { z ->
            val names = z.entries().toList().map { it.name }
            assertTrue(names.contains("assets/py/program.py"))
            assertTrue(names.contains("assets/py/stroka_tkinter.py"))
            assertTrue(names.contains("assets/py/stroka_py_app.py"))
            assertTrue(names.any { it.startsWith("lib/") && it.contains("libpython") && it.endsWith(".so") })
            assertTrue(names.any { it.contains("chaquopy") })
            assertTrue(names.any { it == "classes.dex" || it.endsWith("classes.dex") })
            val prog = z.getInputStream(z.getEntry("assets/py/program.py")).readBytes().toString(Charsets.UTF_8)
            assertTrue(prog.contains("tkinter"))
        }
        PonyatnyCalculatorBuildTest.copyEvidence(root, apk, "Калькулятор-python.apk")
    }
}
