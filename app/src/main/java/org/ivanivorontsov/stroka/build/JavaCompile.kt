package org.ivanivorontsov.stroka.build

import org.eclipse.jdt.internal.compiler.batch.Main
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object JavaCompile {
    fun compile(sources: List<File>, bootclasspath: File, outDir: File, classpath: List<File> = emptyList()) {
        if (sources.isEmpty()) throw BuildException("Нет Java-исходников для компиляции.")
        outDir.mkdirs()
        val out = StringWriter()
        val err = StringWriter()
        val main = Main(PrintWriter(out, true), PrintWriter(err, true), false, null, null)
        val args = ArrayList<String>()
        args.addAll(listOf(
            // Android's android.jar is the platform boot classpath. ECJ's
            // modular system-library mode expects a JDK image, which Android
            // runtimes do not ship. Java 8 compliance still supports lambdas,
            // method references, streams, default methods and try-with-resources.
            "-source", "1.8",
            "-target", "1.8",
            "-encoding", "UTF-8",
            "-bootclasspath", bootclasspath.absolutePath,
            "-d", outDir.absolutePath,
            "-proc:none",
            "-nowarn",
            "-g:none"
        ))
        if (classpath.isNotEmpty()) {
            args.add("-classpath")
            args.add(classpath.joinToString(File.pathSeparator) { it.absolutePath })
        }
        sources.forEach { args.add(it.absolutePath) }
        val ok = main.compile(args.toTypedArray())
        if (!ok) {
            val msg = (err.toString() + out.toString()).trim().ifBlank { "компилятор Java вернул ошибку без текста" }
            throw BuildException("Компилятор Java не смог собрать программу.\n$msg")
        }
    }
}
