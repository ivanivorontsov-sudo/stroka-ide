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
            "-source", "17",
            "-target", "17",
            "-encoding", "UTF-8",
            "-d", outDir.absolutePath,
            "-proc:none",
            "-nowarn",
            "-g:none"
        ))
        // ECJ rejects -bootclasspath for Java 9+ compliance. Android's
        // android.jar is the platform API surface, so put it first on the
        // ordinary classpath together with bundled/user dependencies.
        args.add("-classpath")
        args.add((listOf(bootclasspath) + classpath).joinToString(File.pathSeparator) { it.absolutePath })
        sources.forEach { args.add(it.absolutePath) }
        val ok = main.compile(args.toTypedArray())
        if (!ok) {
            val msg = (err.toString() + out.toString()).trim().ifBlank { "компилятор Java вернул ошибку без текста" }
            throw BuildException("Компилятор Java не смог собрать программу.\n$msg")
        }
    }
}
