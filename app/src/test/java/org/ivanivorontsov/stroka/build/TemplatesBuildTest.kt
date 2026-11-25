package org.ivanivorontsov.stroka.build

import org.ivanivorontsov.stroka.core.Templates
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TemplatesBuildTest {
    @Test
    fun androidTemplatesBuildApks() {
        val root = PonyatnyCalculatorBuildTest.locateRoot()
        val assets = BuildCoordinator.fromProject(root)
        for (id in listOf("ponyatny-start", "ponyatny-calc", "ponyatny-game", "java-calc")) {
            val t = Templates.all.first { it.id == id }
            val work = File(root, "app/build/test-template-$id")
            work.deleteRecursively()
            val log = StringBuilder()
            val apk = OneFileBuild.buildApk(t.files.getValue(t.main), t.main, t.title.substringBefore(':'), work, assets, File(work, "$id.apk"), log = { log.append(it).append('\n'); Unit })
            assertTrue("$id: $log", apk.isFile && apk.length() > 1000)
            val dump = Proc.require(listOf(assets.toolchain.aapt2.absolutePath, "dump", "badging", apk.absolutePath), work, err = "aapt2 dump")
            assertTrue("$id: $dump", dump.contains("launchable-activity"))
        }
    }

    @Test
    fun everyTemplateIsSmallAndCommented() {
        for (t in Templates.all) {
            val main = t.files.getValue(t.main)
            assertTrue("${t.id} слишком длинный", main.lines().size <= 90)
            assertTrue("${t.id} без комментариев", Regex("""(^|\s)(#|//|/\*|;|<!--)""").containsMatchIn(main))
        }
    }
}
