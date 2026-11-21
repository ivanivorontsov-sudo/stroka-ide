package org.ivanivorontsov.stroka.build

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OnDeviceBuildTest {
    @Test
    fun buildsPonyatnyAndJavaCalculators() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val ctx = inst.targetContext
        val testCtx = inst.context
        val log = StringBuilder()
        val assets = BuildCoordinator.prepare(ctx)
        val outDir = File(ctx.getExternalFilesDir(null), "build-out").also { it.mkdirs() }
        val work = File(ctx.cacheDir, "ondevice-test")
        val pon = testCtx.assets.open("test/калькулятор.pon").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val java = testCtx.assets.open("test/Калькулятор.java").bufferedReader(Charsets.UTF_8).use { it.readText() }

        work.deleteRecursively()
        val t1 = System.nanoTime()
        val ponApk = OneFileBuild.buildApk(pon, "калькулятор.pon", "Калькулятор", File(work, "pon"), assets, File(outDir, "Калькулятор.apk")) { log.append(it).append('\n') }
        val ponMs = (System.nanoTime() - t1) / 1_000_000L
        File(outDir, "time-ponyatny.txt").writeText("$ponMs\n")

        work.deleteRecursively()
        val t2 = System.nanoTime()
        val javaApk = OneFileBuild.buildApk(java, "Калькулятор.java", "Калькулятор", File(work, "java"), assets, File(outDir, "Калькулятор-java.apk")) { log.append(it).append('\n') }
        val javaMs = (System.nanoTime() - t2) / 1_000_000L
        File(outDir, "time-java.txt").writeText("$javaMs\n")

        val t3 = System.nanoTime()
        val exe = OneFileBuild.buildExe(pon, "калькулятор.pon", "Калькулятор", File(work, "exe"), assets, File(outDir, "Калькулятор.exe"))
        val exeMs = (System.nanoTime() - t3) / 1_000_000L
        File(outDir, "time-exe.txt").writeText("$exeMs\n")
        File(outDir, "log.txt").writeText("pon=${ponMs}ms java=${javaMs}ms exe=${exeMs}ms\n$log")

        assertTrue(ponApk.isFile && ponApk.length() > 1000)
        assertTrue(javaApk.isFile && javaApk.length() > 1000)
        assertTrue(exe.isFile && exe.length() > assets.exeStub.size)
    }
}
