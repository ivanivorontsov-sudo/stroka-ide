package org.ivanivorontsov.stroka.build

import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import java.io.File

object Dexer {
    fun toDex(classFiles: List<File>, androidJar: File, outDir: File, extraJars: List<File> = emptyList(), minApi: Int = 26): File {
        if (classFiles.isEmpty()) throw BuildException("Компилятор Java не создал .class файлы.")
        outDir.mkdirs()
        val cmd = D8Command.builder()
            .addProgramFiles((classFiles + extraJars.filter { it.isFile }).map { it.toPath() })
            .addLibraryFiles(androidJar.toPath())
            .setMinApiLevel(minApi)
            .setOutput(outDir.toPath(), OutputMode.DexIndexed)
            .build()
        D8.run(cmd)
        val dex = File(outDir, "classes.dex")
        if (!dex.isFile) throw BuildException("d8 не создал classes.dex")
        return dex
    }
}
