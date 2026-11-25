package org.ivanivorontsov.stroka.build

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.ivanivorontsov.stroka.core.Templates
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class KotlinOnDeviceBuildTest {
    @Test
    fun buildsKotlinTemplateApkOnDeviceAndRunsIt() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val ctx = inst.targetContext
        val log = StringBuilder()
        val assets = BuildCoordinator.prepare(ctx)
        val outDir = File(ctx.getExternalFilesDir(null), "build-out").also { it.mkdirs() }
        val work = File(ctx.cacheDir, "ondevice-kotlin")
        work.deleteRecursively()
        val t = Templates.all.first { it.id == "kotlin" }
        val src = t.files.getValue(t.main)
        val out = File(outDir, "Счётчик-kotlin.apk")
        val t0 = System.nanoTime()
        val apk = try {
            OneFileBuild.buildApk(src, t.main, "Счётчик", work, assets, out, log = { log.append(it).append('\n'); Unit })
        } catch (e: Throwable) {
            File(outDir, "kotlin-error.txt").writeText(log.toString() + "\n" + android.util.Log.getStackTraceString(e))
            throw AssertionError("Kotlin APK не собрался на устройстве: ${e.message}\n$log", e)
        }
        val ms = (System.nanoTime() - t0) / 1_000_000L
        File(outDir, "kotlin-log.txt").writeText("kotlin=${ms}ms\n$log")
        assertTrue(apk.isFile && apk.length() > 1000)

        val device = UiDevice.getInstance(inst)
        val pkg = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE).find(src)?.groupValues?.get(1)
            ?: ApkPacker.packageName("Счётчик")
        runCatching { device.executeShellCommand("pm uninstall $pkg") }
        val install = device.executeShellCommand("pm install -r \"${apk.absolutePath}\"")
        assertTrue("Kotlin APK не установился: $install", install.contains("Success"))
        device.executeShellCommand("monkey -p $pkg -c android.intent.category.LAUNCHER 1")
        assertNotNull("Окно Kotlin-приложения не появилось", device.wait(Until.findObject(By.textContains("Нажато")), 20_000))
        val plus = device.findObject(By.text("+1"))
        assertNotNull("Нет кнопки +1", plus)
        plus.click()
        plus.click()
        plus.click()
        assertNotNull("Счётчик не дошёл до 3", device.wait(Until.findObject(By.textContains("Нажато: 3")), 5_000))
        runCatching { device.executeShellCommand("pm uninstall $pkg") }
    }
}
