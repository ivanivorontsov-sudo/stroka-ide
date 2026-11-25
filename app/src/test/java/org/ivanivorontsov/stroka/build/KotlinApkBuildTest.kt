package org.ivanivorontsov.stroka.build

import org.ivanivorontsov.stroka.core.Templates
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class KotlinApkBuildTest {
    @Test
    fun kotlinTemplateBuildsSignedApk() {
        val root = PonyatnyCalculatorBuildTest.locateRoot()
        val assets = BuildCoordinator.fromProject(root)
        val t = Templates.all.first { it.id == "kotlin" }
        val work = File(root, "app/build/test-kotlin")
        work.deleteRecursively()
        val log = StringBuilder()
        val apk = OneFileBuild.buildApk(t.files.getValue(t.main), t.main, "Счётчик", work, assets, File(work, "Счётчик.apk"), log = { log.append(it).append('\n'); Unit })
        println(log)
        assertTrue(apk.isFile && apk.length() > 1000)
        val dump = Proc.require(listOf(assets.toolchain.aapt2.absolutePath, "dump", "badging", apk.absolutePath), work, err = "aapt2 dump")
        assertTrue(dump, dump.contains("launchable-activity"))
        val dex = ZipFile(apk).use { z -> z.getInputStream(z.getEntry("classes.dex")).readBytes() }
        val text = String(dex, Charsets.ISO_8859_1)
        assertTrue("Kotlin runtime должен быть в DEX", text.contains("Lkotlin/jvm/internal/Intrinsics;"))
    }
}
