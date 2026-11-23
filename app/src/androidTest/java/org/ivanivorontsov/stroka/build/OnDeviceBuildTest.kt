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
        val ponApk = OneFileBuild.buildApk(pon, "калькулятор.pon", "Калькулятор", File(work, "pon"), assets, File(outDir, "Калькулятор.apk"), log = { log.append(it).append('\n'); Unit })
        val ponMs = (System.nanoTime() - t1) / 1_000_000L
        File(outDir, "time-ponyatny.txt").writeText("$ponMs\n")

        work.deleteRecursively()
        val t2 = System.nanoTime()
        val javaApk = OneFileBuild.buildApk(java, "Калькулятор.java", "Калькулятор", File(work, "java"), assets, File(outDir, "Калькулятор-java.apk"), log = { log.append(it).append('\n'); Unit })
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
        verifyPonyatnyCalculatorCallbacks(inst, ponApk)
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

            val fields = device.wait(Until.findObjects(By.clazz("android.widget.EditText")), 5_000)
            assertTrue("Expected two calculator input fields", fields != null && fields.size >= 2)
            val firstField = fields!![0]
            val secondField = fields[1]
            firstField.setText("7")
            secondField.setText("5")

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
            secondField.setText("0")
            tapAndExpect("Разделить", "Деление на ноль")
        } finally {
            runCatching { device.executeShellCommand("am force-stop $packageName") }
            runCatching { device.executeShellCommand("pm uninstall $packageName") }
        }
    }
    private fun verifyPonyatnyCalculatorCallbacks(inst: android.app.Instrumentation, apk: File) {
        val device = UiDevice.getInstance(inst)
        val packageName = ApkPacker.packageName("Калькулятор")
        runCatching { device.executeShellCommand("pm uninstall $packageName") }
        val install = device.executeShellCommand("pm install -r \"\${apk.absolutePath}\"")
        assertTrue("Generated Понятный APK did not install: $install", install.contains("Success"))
        try {
            val launch = device.executeShellCommand("am start -n $packageName/.Main")
            assertTrue("Generated Понятный Activity did not start: $launch", launch.contains("Starting"))
            assertTrue("Понятный calculator screen did not appear", device.wait(Until.hasObject(By.text("Калькулятор")), 10_000))

            fun tap(label: String) {
                val target = device.wait(Until.findObject(By.text(label)), 5_000)
                assertTrue("Понятный calculator button '$label' missing", target != null)
                target!!.click()
            }

            tap("7")
            tap("+")
            tap("5")
            tap("=")
            assertTrue(
                "Понятный calculator did not calculate 7 + 5",
                device.wait(Until.hasObject(By.text("12")), 5_000)
            )
        } finally {
            runCatching { device.executeShellCommand("am force-stop $packageName") }
            runCatching { device.executeShellCommand("pm uninstall $packageName") }
        }
    }

}
