package org.ivanivorontsov.stroka.build

import org.eclipse.jdt.internal.compiler.batch.Main
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object JavaCompile {
    fun compile(
        sources: List<File>,
        bootclasspath: File,
        outDir: File,
        classpath: List<File> = emptyList(),
        lambdaStubs: File? = null
    ) {
        if (sources.isEmpty()) throw BuildException("Нет Java-исходников для компиляции.")
        outDir.mkdirs()
        for (f in sources) {
            val text = f.readText(Charsets.UTF_8)
            val plain = JavaTextBlocks.desugar(text)
            if (plain != text) f.writeText(plain, Charsets.UTF_8)
        }
        val out = StringWriter()
        val err = StringWriter()
        val main = Main(PrintWriter(out, true), PrintWriter(err, true), false, null, null)
        val bootEntries = listOfNotNull(bootclasspath, lambdaStubs?.takeIf { it.isFile })
        val args = ArrayList<String>()
        args.addAll(listOf(
            // ECJ 3.18 is the last Android-compatible line used here: newer ECJ
            // initializes javax.lang.model.SourceVersion, which Android ART lacks.
            // Android's SDK requires core-lambda-stubs alongside android.jar
            // for Java 8 invokedynamic lambda compilation.
            "-source", "1.8",
            "-target", "1.8",
            "-encoding", "UTF-8",
            "-bootclasspath", bootEntries.joinToString(File.pathSeparator) { it.absolutePath },
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
