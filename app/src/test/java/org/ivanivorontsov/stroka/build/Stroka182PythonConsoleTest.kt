package org.ivanivorontsov.stroka.build

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

class Stroka182PythonConsoleTest {
    private val root = PonyatnyCalculatorBuildTest.locateRoot()

    @Test
    fun programBytesAreExactUtf8() {
        val src = File(root, "tools/fixtures/main.py").readText(Charsets.UTF_8)
        assertArrayEquals(src.toByteArray(Charsets.UTF_8), PythonApkBuilder.programBytes(src))
        assertArrayEquals(src.toByteArray(Charsets.UTF_8), PythonApkBuilder.programBytes("\uFEFF" + src))
        val bad = try { PythonApkBuilder.programBytes("print(1)\nprint(\u0000)\n"); null } catch (e: BuildException) { e.message }
        assertTrue(bad ?: "нет ошибки", bad != null && bad.contains("строка 2"))
    }

    @Test
    fun builtApkCarriesIdenticalSource() {
        val assets = BuildCoordinator.fromProject(root)
        val runtime = PythonApkBuilder.findRuntimeApk(assets)
        assumeTrue("Нужен APK Строки со средой Chaquopy", runtime != null && runtime.isFile)
        PythonApkBuilder.runtimeApkOverride = runtime
        val file = File(root, "tools/fixtures/main.py")
        val src = file.readText(Charsets.UTF_8)
        val work = File(root, "app/build/test-182-pymain")
        work.deleteRecursively()
        val apk = OneFileBuild.buildApk(src, "main.py", "Привет Python", work, assets, File(work, "main-py.apk"))
        ZipFile(apk).use { z ->
            val prog = z.getInputStream(z.getEntry("assets/py/program.py")).readBytes()
            assertArrayEquals(file.readBytes(), prog)
            assertFalse(prog.any { it.toInt() == 0 })
            val boot = z.getInputStream(z.getEntry("assets/py/stroka_py_app.py")).readBytes().toString(Charsets.UTF_8)
            assertFalse(boot.contains("am.open"))
            assertTrue(boot.contains("utf-8-sig"))
            val dex = z.getInputStream(z.getEntry("classes.dex")).readBytes().toString(Charsets.ISO_8859_1)
            assertTrue(dex.contains("consoleInput"))
            assertTrue(dex.contains("consoleWrite"))
        }
        PonyatnyCalculatorBuildTest.copyEvidence(root, apk, "main-py-1.8.2.apk")
    }

    @Test
    fun bootstrapRunsScriptWithConsoleInput() {
        val python = listOf("python3", "python").firstOrNull { cmd ->
            try { ProcessBuilder(cmd, "--version").start().waitFor(10, TimeUnit.SECONDS) } catch (_: Exception) { false }
        }
        assumeTrue("нужен python3", python != null)
        val work = File(root, "app/build/test-182-pyhost")
        work.deleteRecursively()
        val py = File(work, "files/py")
        py.mkdirs()
        File(py, "stroka_py_app.py").writeText(PythonApkBuilder.bootstrap(), Charsets.UTF_8)
        File(py, "program.py").writeBytes(PythonApkBuilder.programBytes("\uFEFF" + File(root, "tools/fixtures/main.py").readText(Charsets.UTF_8)))
        File(py, "FILENAME.txt").writeText("main.py", Charsets.UTF_8)
        File(py, "stroka_tkinter.py").writeText("def install(cb):\n    pass\n", Charsets.UTF_8)
        File(root, "app/src/main/python/stroka.py").copyTo(File(py, "stroka.py"))
        val driver = File(work, "driver.py")
        driver.writeText(
            """
            import sys
            ARGS = list(sys.argv)
            sys.path.insert(0, sys.argv[1])
            import stroka_py_app
            out = []
            asked = []
            done = []
            class D:
                def getAbsolutePath(self):
                    return ARGS[2]
            class A:
                def getFilesDir(self):
                    return D()
                def consoleWrite(self, s, err):
                    out.append(("E:" if err else "") + s)
                def consoleInput(self, prompt):
                    asked.append(prompt)
                    return "Иван"
                def consoleDone(self):
                    done.append(1)
            mode = stroka_py_app.prepare(A())
            stroka_py_app.run(A())
            sys.__stdout__.write("".join(out) + "\n")
            sys.__stdout__.write("ASKED=" + "|".join(asked) + "\n")
            sys.__stdout__.write("DONE=" + str(len(done)) + "\n")
            sys.__stdout__.write("MODE=" + mode + "\n")
            import stroka
            ui = open(ARGS[3], encoding="utf-8").read()
            sys.__stdout__.write("NATIVE=" + str(stroka.uses_native_ui(ui)) + "\n")
            """.trimIndent() + "\n",
            Charsets.UTF_8
        )
        val pb = ProcessBuilder(python!!, driver.absolutePath, py.absolutePath, File(work, "files").absolutePath, File(root, "tools/fixtures/android-ui.py").absolutePath)
            .directory(work).redirectErrorStream(true)
        pb.environment()["PYTHONIOENCODING"] = "utf-8"
        val p = pb.start()
        val text = p.inputStream.readBytes().toString(Charsets.UTF_8)
        assertTrue(p.waitFor(60, TimeUnit.SECONDS))
        println(text)
        assertEquals(text, 0, p.exitValue())
        assertTrue(text, text.contains("Привет, «Строка» — это Python на телефоне!"))
        assertTrue(text, text.contains("3 → 9"))
        assertTrue(text, text.contains("Как тебя зовут? "))
        assertTrue(text, text.contains("Рад знакомству, Иван! Квадрат 12 — 144."))
        assertTrue(text, text.contains("ASKED=Как тебя зовут? "))
        assertTrue(text, text.contains("DONE=1"))
        assertFalse(text, text.contains("E:"))
        assertTrue(text, text.contains("MODE=console"))
        assertTrue(text, text.contains("NATIVE=True"))
    }

    @Test
    fun pythonWithAndroidApiIsNotRoutedToJava() {
        val assets = BuildCoordinator.fromProject(root)
        val runtime = PythonApkBuilder.findRuntimeApk(assets)
        assumeTrue("Нужен APK Строки со средой Chaquopy", runtime != null && runtime.isFile)
        PythonApkBuilder.runtimeApkOverride = runtime
        val file = File(root, "tools/fixtures/android-ui.py")
        val src = file.readText(Charsets.UTF_8)
        assertTrue(src.contains("android.app.Activity"))
        val work = File(root, "app/build/test-182-pyui")
        work.deleteRecursively()
        val apk = OneFileBuild.buildApk(src, "main.py", "Кнопки", work, assets, File(work, "knopki.apk"))
        ZipFile(apk).use { z ->
            assertArrayEquals(file.readBytes(), z.getInputStream(z.getEntry("assets/py/program.py")).readBytes())
            assertTrue(z.getEntry("assets/py/stroka.py") != null)
            val dex = z.getInputStream(z.getEntry("classes.dex")).readBytes().toString(Charsets.ISO_8859_1)
            assertTrue(dex.contains("run_native"))
            assertTrue(dex.contains("showConsole"))
        }
        assertTrue(org.ivanivorontsov.stroka.run.PythonRunner.usesNativeUi(src))
        assertFalse(org.ivanivorontsov.stroka.run.PythonRunner.usesNativeUi(File(root, "tools/fixtures/main.py").readText(Charsets.UTF_8)))
        PonyatnyCalculatorBuildTest.copyEvidence(root, apk, "knopki-python-1.8.2.apk")
    }
}
