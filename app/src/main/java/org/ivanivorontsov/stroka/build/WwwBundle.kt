
package org.ivanivorontsov.stroka.build

import java.io.File

object WwwBundle {
    fun fromRuntime(runtimeDir: File, yadroJs: File, programJs: String): Map<String, ByteArray> {
        fun need(f: File): ByteArray {
            if (!f.isFile) throw BuildException("Нет файла оболочки: ${f.absolutePath}")
            return f.readBytes()
        }
        return mapOf(
            "index.html" to need(File(runtimeDir, "index.html")),
            "runtime.js" to need(File(runtimeDir, "runtime.js")),
            "shell.js" to need(File(runtimeDir, "shell.js")),
            "grafika.js" to need(File(runtimeDir, "grafika.js")),
            "yadro.js" to need(yadroJs),
            "program.js" to programJs.toByteArray(Charsets.UTF_8)
        )
    }
}
