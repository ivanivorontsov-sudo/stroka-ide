package org.ivanivorontsov.stroka.build

import org.ivanivorontsov.stroka.core.Templates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class SetupInstallerTest {
    @Test
    fun setupWithIconAndOptions() {
        val root = PonyatnyCalculatorBuildTest.locateRoot()
        val assets = BuildCoordinator.fromProject(root)
        assumeTrue("нужен шаблон установщика", assets.setupStub.size > 64)
        val t = Templates.all.first { it.id == "ponyatny-calc" }
        val work = File(root, "app/build/test-setup-icon")
        work.deleteRecursively()
        val red = WinIcon.Pixels { n -> IntArray(n * n) { 0xFFD03030.toInt() } }
        val opts = SetupOptions(
            title = "Калькулятор", version = "2.0", publisher = "Иван Воронцов",
            desktopShortcut = true, startMenu = false, runAfter = false,
            license = "Можно пользоваться бесплатно.", icon = red
        )
        val log = StringBuilder()
        val setup = OneFileBuild.buildSetup(t.files.getValue(t.main), t.main, "Калькулятор", work, assets, File(work, "Калькулятор-2.0-Setup.exe"), { log.append(it).append('\n'); Unit }, options = opts)
        val buf = setup.readBytes()
        assertEquals('M'.code.toByte(), buf[0])
        val inner = SetupPacker.inspect(buf)!!.second
        val meta = String(inner.getValue("meta.txt"), Charsets.UTF_8)
        for (line in listOf("title=Калькулятор", "version=2.0", "publisher=Иван Воронцов", "desktop=1", "startmenu=0", "run=0"))
            assertTrue("$line нет в\n$meta", meta.lines().contains(line))
        assertTrue(inner.keys.toString(), inner.containsKey("license.txt") && inner.containsKey("app.ico"))
        assertTrue(log.toString(), log.contains("Значок программы заменён"))
        val ico = inner.getValue("app.ico")
        assertEquals(4, ico[4].toInt())
        assertTrue("значок установщика тоже заменён", WinIcon.patchExe(buf.copyOf(), red) > 0)
        val out = File(root, "app/build/setup-evidence"); out.mkdirs()
        setup.copyTo(File(out, setup.name), overwrite = true)
    }
}
