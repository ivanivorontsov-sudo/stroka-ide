package org.ivanivorontsov.stroka.build

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Та же Java-программа с текстовым блоком, что в тесте на устройстве (androidTest/assets/test/Калькулятор.java). */
class JavaTextBlockApkTest {
    @Test
    fun calculatorWithTextBlockBuilds() {
        val root = PonyatnyCalculatorBuildTest.locateRoot()
        val src = File(root, "app/src/androidTest/assets/test/Калькулятор.java").readText()
        assertTrue(src.contains("\"\"\""))
        val work = File(root, "app/build/test-java-textblock"); work.deleteRecursively()
        val apk = OneFileBuild.buildApk(src, "Калькулятор.java", "Калькулятор", work, BuildCoordinator.fromProject(root), File(work, "k.apk"))
        assertTrue(apk.length() > 1000)
    }
}
