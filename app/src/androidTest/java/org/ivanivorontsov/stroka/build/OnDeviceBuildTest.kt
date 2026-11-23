package org.ivanivorontsov.stroka.build

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
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

        // Install the exact Java APK produced by the on-device builder and exercise its
        // real Activity callbacks, rather than only checking that DEX files exist.
        verifyJavaCalculatorCallbacks(inst, javaApk)
    }

    private fun verifyJavaCalculatorCallbacks(inst: android.app.Instrumentation, apk: File) {
        val device = UiDevice.getInstance(inst)
        val packageName = "org.stroka.app.jcalc"
        runCatching { device.executeShellCommand("pm uninstall $packageName") }
        val install = device.executeShellCommand("pm install -r \"${apk.absolutePath}\"")
        assertTrue("Generated Java APK did not install: $install", install.contains("Success"))
        try {
            val launch = device.executeShellCommand("am start -n $packageName/.Main")
            assertTrue("Generated Java Activity did not start: $launch", launch.contains("Starting"))
            assertTrue("Calculator screen did not appear", device.wait(Until.hasObject(By.text("Калькулятор")), 10_000))

            val first = device.wait(Until.findObject(By.clazz("android.widget.EditText").instance(0)), 5_000)
            val second = device.wait(Until.findObject(By.clazz("android.widget.EditText").instance(1)), 5_000)
            assertTrue("First input field missing", first != null)
            assertTrue("Second input field missing", second != null)
            first!!.setText("7")
            second!!.setText("5")
            device.pressBack()

            fun tapAndExpect(button: String, expected: String) {
                val target = device.wait(Until.findObject(By.text(button)), 5_000)
                assertTrue("Button '$button' missing", target != null)
                target!!.click()
                assertTrue("Button '$button' did not produce '$expected'", device.wait(Until.hasObject(By.text(expected)), 5_000))
            }

            tapAndExpect("Сложить", "12")
            tapAndExpect("Вычесть", "2")
            tapAndExpect("Умножить", "35")
            tapAndExpect("Разделить", "1.4")
            second.setText("0")
            device.pressBack()
            tapAndExpect("Разделить", "Деление на ноль")
        } finally {
            runCatching { device.executeShellCommand("am force-stop $packageName") }
            runCatching { device.executeShellCommand("pm uninstall $packageName") }
        }
    }
}
