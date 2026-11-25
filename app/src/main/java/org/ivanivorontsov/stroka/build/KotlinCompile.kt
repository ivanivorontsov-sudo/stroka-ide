package org.ivanivorontsov.stroka.build

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

/**
 * On-device Kotlin/JVM compilation using the Android-patched compiler.
 *
 * This intentionally targets JVM bytecode. Kotlin/Native, Kotlin/JS and Kotlin/Wasm
 * are separate toolchains and are not silently treated as supported here.
 */
object KotlinCompile {
    fun compile(
        sources: List<File>,
        androidJar: File,
        kotlinStdlib: File,
        outDir: File,
        classpath: List<File> = emptyList()
    ) {
        if (sources.isEmpty()) throw BuildException("Нет Kotlin-исходников для компиляции.")
        if (!androidJar.isFile) throw BuildException("Не найден Android API: ${androidJar.absolutePath}")
        if (!kotlinStdlib.isFile) throw BuildException("Не найдена Kotlin стандартная библиотека. Переустановите или обновите Строку.")
        outDir.mkdirs()

        val cp = (listOf(androidJar, kotlinStdlib) + classpath.filter { it.isFile })
            .distinctBy { it.absolutePath }
            .joinToString(File.pathSeparator) { it.absolutePath }
        val args = arrayListOf(
            // На Android java.home = /apex/com.android.art, там нет классов JDK:
            // без -no-jdk компилятор падает с «No class roots are found in the JDK path».
            // Классы java.* берутся из android.jar в classpath.
            "-no-jdk",
            "-no-stdlib",
            "-no-reflect",
            "-jvm-target", "1.8",
            "-classpath", cp,
            "-d", outDir.absolutePath,
            "-java-parameters"
        )
        sources.forEach { args += it.absolutePath }

        val output = ByteArrayOutputStream()
        val stream = PrintStream(output, true, Charsets.UTF_8.name())
        val exit = try {
            K2JVMCompiler().exec(stream, MessageRenderer.XML, *args.toTypedArray())
        } catch (e: Throwable) {
            throw BuildException("Не удалось запустить компилятор Kotlin: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            stream.flush()
            stream.close()
        }
        if (exit != ExitCode.OK) {
            val diagnostics = output.toString(Charsets.UTF_8.name()).trim()
            throw BuildException("Компилятор Kotlin завершился с кодом $exit.\n${diagnostics.ifBlank { "Нет подробностей от компилятора." }}")
        }
        if (outDir.walkTopDown().none { it.isFile && it.extension == "class" }) {
            throw BuildException("Компилятор Kotlin завершился без ошибок, но не создал .class файлы.")
        }
    }
}
