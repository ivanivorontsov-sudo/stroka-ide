package org.ivanivorontsov.stroka.build

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class PonyatnyCalculatorBuildTest {
    @Test
    fun compilesCalculatorAndBuildsSignedApk() {
        val root = locateRoot()
        val assets = BuildCoordinator.fromProject(root)
        val src = File(root, "tools/fixtures/калькулятор.pon").readText(Charsets.UTF_8)
        val work = File(root, "app/build/test-calc")
        work.deleteRecursively()
        val out = File(work, "Калькулятор.apk")
        val t0 = System.nanoTime()
        val apk = OneFileBuild.buildApk(src, "калькулятор.pon", "Калькулятор", work, assets, out)
        val ms = (System.nanoTime() - t0) / 1_000_000L
        println("Понятный APK: $ms мс, ${apk.length()} байт")
        assertTrue("APK должен существовать", apk.isFile && apk.length() > 1000)
        val dump = Proc.require(
            listOf(assets.toolchain.aapt2.absolutePath, "dump", "badging", apk.absolutePath),
            work,
            err = "aapt2 dump не прочитал собранный APK."
        )
        assertTrue(dump.contains("application-label:'Калькулятор'"))
        assertTrue(dump.contains("package: name='org.stroka.app.kalkulyator'"))
        copyEvidence(root, apk, "Калькулятор.apk")
    }

    @Test
    fun compilesJavaCalculatorApk() {
        val root = locateRoot()
        val assets = BuildCoordinator.fromProject(root)
        val src = File(root, "tools/fixtures/Калькулятор.java").readText(Charsets.UTF_8)
        val work = File(root, "app/build/test-java-calc")
        work.deleteRecursively()
        val out = File(work, "Калькулятор-java.apk")
        val t0 = System.nanoTime()
        val apk = OneFileBuild.buildApk(src, "Калькулятор.java", "Калькулятор", work, assets, out)
        val ms = (System.nanoTime() - t0) / 1_000_000L
        println("Java APK: $ms мс, ${apk.length()} байт")
        assertTrue(apk.isFile && apk.length() > 1000)
        val dump = Proc.require(
            listOf(assets.toolchain.aapt2.absolutePath, "dump", "badging", apk.absolutePath),
            work,
            err = "aapt2 dump не прочитал Java APK."
        )
        assertTrue(dump.contains("application-label:'Калькулятор'"))
        assertTrue(
            "Android launcher must point at the source Activity package",
            dump.contains("launchable-activity: name='org.stroka.app.jcalc.Main'")
        )
        val dexBytes = ZipFile(apk).use { zip ->
            val entry = zip.getEntry("classes.dex") ?: error("Java APK has no classes.dex")
            zip.getInputStream(entry).use { it.readBytes() }
        }
        val dexText = String(dexBytes, Charsets.ISO_8859_1)
        assertTrue("Main Activity must be present in DEX", dexText.contains("Lorg/stroka/app/jcalc/Main;"))
        assertTrue("Anonymous click listener must be present in DEX", dexText.contains("Lorg/stroka/app/jcalc/Main\$1;"))
        copyEvidence(root, apk, "Калькулятор-java.apk")
    }

    @Test
    fun packsRealWindowsExe() {
        val root = locateRoot()
        val assets = BuildCoordinator.fromProject(root)
        assumeTrue("Нужен шаблон EXE", assets.exeStub.size > 64)
        val src = File(root, "tools/fixtures/калькулятор.pon").readText(Charsets.UTF_8)
        val work = File(root, "app/build/test-exe")
        work.deleteRecursively()
        val out = File(work, "Калькулятор.exe")
        val t0 = System.nanoTime()
        val exe = OneFileBuild.buildExe(src, "калькулятор.pon", "Калькулятор", work, assets, out)
        val ms = (System.nanoTime() - t0) / 1_000_000L
        println("Понятный EXE: $ms мс, ${exe.length()} байт")
        assertTrue(exe.isFile && exe.length() > assets.exeStub.size)
        val buf = exe.readBytes()
        assertEquals('M'.code.toByte(), buf[0])
        assertEquals('Z'.code.toByte(), buf[1])
        val magic = "STRQJS01".toByteArray(Charsets.US_ASCII)
        assertTrue(buf.copyOfRange(buf.size - 16, buf.size - 8).contentEquals(magic))
        copyEvidence(root, exe, "Калькулятор.exe")
    }

    @Test
    fun ponyatnySystemCallsAreRecognizedAsBuiltins() {
        val root = locateRoot()
        val yadro = File(root, "app/src/main/assets/ponyatny/yadro.js").readText(Charsets.UTF_8)
        val src = """
            уведомление("Готово")
            вибрация(80)
            открой_ссылку("https://example.com")
            скопируй_в_буфер("текст")
            пусть буфер = прочитай_буфер()
            пусть устройство = из_json(информация_устройства())
            пиши заряд_батареи()
            пиши сеть_доступна()
            открой_настройки()
        """.trimIndent()
        val js = PonyatnyJs.compile(src, yadro)
        assertTrue("Ponyatny Android API calls should compile", js.isNotBlank())
    }

    @Test
    fun rhinoCompilesPonyatnyWithoutNode() {
        val root = locateRoot()
        val yadro = File(root, "app/src/main/assets/ponyatny/yadro.js").readText(Charsets.UTF_8)
        val src = File(root, "tools/fixtures/калькулятор.pon").readText(Charsets.UTF_8)
        val js = PonyatnyJs.compile(src, yadro)
        assertTrue(js.contains("\$сложить"))
        assertTrue(js.contains("\$разделить"))
        assertEquals("calc", PonyatnyJs.detectMode(src, js))
    }

    companion object {
        fun locateRoot(): File {
            var d = File(".").canonicalFile
            repeat(8) {
                if (File(d, "app/src/main/assets/ponyatny/yadro.js").isFile && File(d, "tools/fixtures/калькулятор.pon").isFile) return d
                d = d.parentFile ?: return File(".").canonicalFile
            }
            return File(".").canonicalFile
        }

        fun copyEvidence(root: File, src: File, name: String) {
            val ev = File(System.getenv("STROKA_EVIDENCE") ?: "/home/box/work/stroka-evidence")
            if (!ev.isDirectory && !ev.mkdirs()) return
            if (!ev.canWrite()) return
            src.copyTo(File(ev, name), overwrite = true)
        }
    }
}
