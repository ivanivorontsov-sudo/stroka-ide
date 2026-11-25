package org.ivanivorontsov.stroka.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class Stroka182PackagesTest {
    private val root = PonyatnyCalculatorBuildTest.locateRoot()

    private fun have(cmd: String): Boolean = try {
        val p = ProcessBuilder("sh", "-c", "command -v $cmd").redirectErrorStream(true).start()
        p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0
    } catch (_: Exception) { false }

    private fun run(cmd: List<String>, dir: File, input: String = ""): Pair<Int, String> {
        val p = ProcessBuilder(cmd).directory(dir).redirectErrorStream(true).start()
        p.outputStream.use { it.write(input.toByteArray(Charsets.UTF_8)) }
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
        assertTrue("процесс завис: $cmd", p.waitFor(60, TimeUnit.SECONDS))
        return p.exitValue() to out
    }

    private fun payload(buf: ByteArray, magic: String): Map<String, ByteArray> {
        val off = buf.size - 16
        assertEquals(magic, String(buf, off, 8, Charsets.US_ASCII))
        var p = ZipArchive.le32(buf, off + 8)
        val count = ZipArchive.le32(buf, p)
        p += 4
        val out = LinkedHashMap<String, ByteArray>()
        repeat(count) {
            val nlen = (buf[p].toInt() and 0xff) or ((buf[p + 1].toInt() and 0xff) shl 8)
            p += 2
            val name = String(buf, p, nlen, Charsets.UTF_8)
            p += nlen
            val dlen = ZipArchive.le32(buf, p)
            p += 4
            out[name] = buf.copyOfRange(p, p + dlen)
            p += dlen
        }
        return out
    }

    @Test
    fun exeCarriesPonyatnyRuntime() {
        val assets = BuildCoordinator.fromProject(root)
        val src = File(root, "tools/fixtures/калькулятор.pon").readText(Charsets.UTF_8)
        val work = File(root, "app/build/test-182-exe")
        work.deleteRecursively()
        val exe = OneFileBuild.buildExe(src, "калькулятор.pon", "Калькулятор", work, assets, File(work, "Калькулятор.exe"))
        val files = payload(exe.readBytes(), "STRQJS01")
        assertTrue(files.keys.toString(), files.keys.containsAll(listOf("yadro.js", "boot.js", "program.js", "meta.txt")))
        assertTrue(String(files["boot.js"]!!, Charsets.UTF_8).contains("makeRuntime"))
        assertTrue(String(files["meta.txt"]!!, Charsets.UTF_8).contains("mode=calc"))
        PonyatnyCalculatorBuildTest.copyEvidence(root, exe, "Калькулятор-1.8.2.exe")
    }

    @Test
    fun setupExeWrapsProgram() {
        val assets = BuildCoordinator.fromProject(root)
        assumeTrue("нужен шаблон установщика", assets.setupStub.size > 64)
        val src = File(root, "tools/fixtures/калькулятор.pon").readText(Charsets.UTF_8)
        val work = File(root, "app/build/test-182-setup")
        work.deleteRecursively()
        val t0 = System.nanoTime()
        val setup = OneFileBuild.buildSetup(src, "калькулятор.pon", "Калькулятор", work, assets, File(work, "Калькулятор-Setup.exe"))
        println("Setup.exe: ${(System.nanoTime() - t0) / 1_000_000L} мс, ${setup.length()} байт")
        val buf = setup.readBytes()
        assertEquals('M'.code.toByte(), buf[0])
        assertEquals('Z'.code.toByte(), buf[1])
        val inner = SetupPacker.inspect(buf)!!.second
        assertTrue(inner.keys.toString(), inner.containsKey("app/Калькулятор.exe") && inner.containsKey("meta.txt"))
        val meta = String(inner["meta.txt"]!!, Charsets.UTF_8)
        assertTrue(meta, meta.contains("exe=Калькулятор.exe") && meta.contains("title=Калькулятор"))
        val innerExe = payload(inner["app/Калькулятор.exe"]!!, "STRQJS01")
        assertTrue(innerExe.containsKey("yadro.js"))
        PonyatnyCalculatorBuildTest.copyEvidence(root, setup, "Калькулятор-Setup.exe")
    }

    private fun buildAndExtractDeb(name: String, label: String, work: File): File {
        val assets = BuildCoordinator.fromProject(root)
        assumeTrue("нужен QuickJS для Linux", assets.qjsLinuxAmd64.size > 64)
        val src = File(root, "tools/fixtures/$name").readText(Charsets.UTF_8)
        work.deleteRecursively()
        val t0 = System.nanoTime()
        val deb = OneFileBuild.buildDeb(src, name, label, work, assets, File(work, "$label.deb"), env = CompileEnv(fileName = name))
        println(".deb $label: ${(System.nanoTime() - t0) / 1_000_000L} мс, ${deb.length()} байт")
        assertEquals("!<arch>\n", String(deb.readBytes(), 0, 8, Charsets.US_ASCII))
        assumeTrue("нужен dpkg-deb", have("dpkg-deb"))
        val info = run(listOf("dpkg-deb", "--info", deb.absolutePath), work)
        assertEquals(info.second, 0, info.first)
        assertTrue(info.second, info.second.contains("Architecture: amd64"))
        val x = File(work, "x")
        val ex = run(listOf("dpkg-deb", "-x", deb.absolutePath, x.absolutePath), work)
        assertEquals(ex.second, 0, ex.first)
        return deb
    }

    @Test
    fun debCalculatorRunsOnLinux() {
        val work = File(root, "app/build/test-182-deb-calc")
        val deb = buildAndExtractDeb("калькулятор.pon", "Калькулятор", work)
        val pkg = DebPacker.debName("Калькулятор")
        val dir = File(work, "x/usr/lib/stroka-apps/$pkg")
        val qjs = File(dir, "qjs")
        assertTrue(qjs.canExecute())
        val arch = System.getProperty("os.arch")
        assumeTrue("запуск только на x86_64", arch == "amd64" || arch == "x86_64")
        val r = run(listOf(qjs.absolutePath, "--std", File(dir, "main.js").absolutePath, "--stroka-check", "+", "7", "8"), work)
        assertEquals(r.second, 0, r.first)
        assertEquals("15", r.second.trim())
        val d = run(listOf(qjs.absolutePath, "--std", File(dir, "main.js").absolutePath, "--stroka-check", "/", "1", "0"), work)
        assertTrue(d.second, d.second.contains("Деление на ноль"))
        val i = run(listOf(qjs.absolutePath, "--std", File(dir, "main.js").absolutePath), work, "6\n*\n7\n\n")
        assertTrue(i.second, i.second.contains("= 42"))
        PonyatnyCalculatorBuildTest.copyEvidence(root, deb, "Калькулятор.deb")
    }

    @Test
    fun debConsoleProgramWithStdLib() {
        val work = File(root, "app/build/test-182-deb-hello")
        buildAndExtractDeb("привет.pon", "Привет", work)
        val arch = System.getProperty("os.arch")
        assumeTrue(arch == "amd64" || arch == "x86_64")
        val bin = File(work, "x/usr/bin/${DebPacker.debName("Привет")}")
        assertTrue(bin.isFile)
        val dir = File(work, "x/usr/lib/stroka-apps/${DebPacker.debName("Привет")}")
        val r = run(listOf(File(dir, "qjs").absolutePath, "--std", File(dir, "main.js").absolutePath), work)
        assertEquals(r.second, 0, r.first)
        assertTrue(r.second, r.second.contains("Привет из Строка"))
        assertTrue(r.second, r.second.contains("5"))
    }
}
