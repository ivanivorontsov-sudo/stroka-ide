package org.ivanivorontsov.stroka.build

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EcjWithoutJavaxCompilerTest {
    @Test
    fun compilesJavaWhenJavaCompilerModuleIsAbsent() {
        val root = PonyatnyCalculatorBuildTest.locateRoot()
        val boot = File(root, "app/src/main/assets/toolchain/android.jar")
        assertTrue("Нужен android.jar", boot.isFile)
        val work = File(root, "app/build/test-ecj-no-compiler")
        work.deleteRecursively()
        work.mkdirs()
        val src = File(work, "Hello.java")
        src.writeText(
            "public class Hello {\n" +
                "    public static String hi() { return \"ok\"; }\n" +
                "}\n"
        )
        val out = File(work, "classes")
        val java = File(System.getProperty("java.home"), "bin/java")
        val cp = System.getProperty("java.class.path")
        val pb = ProcessBuilder(
            java.absolutePath,
            "--limit-modules", "java.base,jdk.unsupported,java.logging",
            "-cp", cp,
            "org.ivanivorontsov.stroka.build.EcjLimitedCompileMain",
            src.absolutePath,
            boot.absolutePath,
            out.absolutePath
        )
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val text = proc.inputStream.bufferedReader().use { it.readText() }
        val code = proc.waitFor()
        assertTrue("ECJ без модуля java.compiler (как на ART):\n$text", code == 0 && text.contains("OK"))
        assertTrue(File(out, "Hello.class").isFile)
    }
}

object EcjLimitedCompileMain {
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            Class.forName("javax.lang.model.SourceVersion")
            JavaCompile.compile(listOf(File(args[0])), File(args[1]), File(args[2]))
            if (!File(args[2], "Hello.class").isFile) {
                System.err.println("NO_CLASS")
                kotlin.system.exitProcess(2)
            }
            println("OK")
        } catch (e: Throwable) {
            e.printStackTrace()
            kotlin.system.exitProcess(1)
        }
    }
}
