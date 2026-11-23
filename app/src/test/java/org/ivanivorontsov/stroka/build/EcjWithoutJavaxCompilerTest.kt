package org.ivanivorontsov.stroka.build

import org.eclipse.jdt.internal.compiler.batch.Main
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EcjWithoutJavaxCompilerTest {
    @Test
    fun compilesJavaWhenJavaCompilerModuleIsAbsent() {
        val root = PonyatnyCalculatorBuildTest.locateRoot()
        val boot = File(root, "app/src/main/assets/toolchain/android.jar")
        assertTrue("Нужен android.jar", boot.isFile)
        val lambdaStubs = File(root, "app/src/main/assets/toolchain/core-lambda-stubs.jar")
        assertTrue("Нужен core-lambda-stubs.jar", lambdaStubs.isFile)
        val work = File(root, "app/build/test-ecj-no-compiler")
        work.deleteRecursively()
        work.mkdirs()
        val src = File(work, "Hello.java")
        src.writeText(
            "public class Hello {\n" +
                "    public static String hi() {\n" +
                "        Runnable task = () -> {};\n" +
                "        task.run();\n" +
                "        return \"ok\";\n" +
                "    }\n" +
                "}\n"
        )
        val out = File(work, "classes")
        val java = File(System.getProperty("java.home"), "bin/java")

        // Gradle runs tests in an isolated worker. java.class.path contains only
        // the worker bootstrap JAR there, not the test runtime dependencies.
        // Build the child classpath from the actual locations of the classes
        // needed by this small compiler probe.
        val cp = listOf(
            EcjLimitedCompileMain::class.java,
            JavaCompile::class.java,
            Main::class.java,
            kotlin.Unit::class.java
        ).mapNotNull { type ->
            type.protectionDomain.codeSource?.location?.toURI()?.let(::File)?.absolutePath
        }.distinct().joinToString(File.pathSeparator)

        val pb = ProcessBuilder(
            java.absolutePath,
            "--limit-modules", "java.base,jdk.unsupported,java.logging,java.xml,java.desktop",
            "-cp", cp,
            "org.ivanivorontsov.stroka.build.EcjLimitedCompileMain",
            src.absolutePath,
            boot.absolutePath,
            out.absolutePath,
            lambdaStubs.absolutePath
        )
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val output = proc.inputStream.bufferedReader().use { it.readText() }
        val code = proc.waitFor()
        assertTrue(
            "ECJ без модуля java.compiler (как на ART), exit=$code:\n$output\nclasspath=$cp",
            code == 0 && output.contains("OK")
        )
        assertTrue(File(out, "Hello.class").isFile)
    }
}

object EcjLimitedCompileMain {
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            Class.forName("javax.lang.model.SourceVersion")
            System.err.println("JAVA_COMPILER_MODULE_PRESENT")
            kotlin.system.exitProcess(3)
        } catch (_: ClassNotFoundException) {
            // Expected: java.compiler is intentionally excluded from this JVM.
        }

        try {
            JavaCompile.compile(listOf(File(args[0])), File(args[1]), File(args[2]), lambdaStubs = File(args[3]))
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
