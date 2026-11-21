package org.ivanivorontsov.stroka.ui

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Embedded, no-shell build tasks. This first backend creates a deployable static
 * website archive entirely with Android/Java APIs; it does not invoke Termux,
 * a shell, Node, or a cloud runner.
 *
 * Native Android APK/AAB projects are deliberately classified separately:
 * they need an Android-host executable aapt2 + D8 + signing toolchain and are
 * routed to the existing Termux/GitHub backends until that toolchain is present.
 */
object LocalBuildEngine {
    enum class ProjectKind { ANDROID_GRADLE, STATIC_WEBSITE, PONYATNY, PYTHON, UNKNOWN }

    data class BuildResult(
        val uri: Uri?,
        val localPath: String?,
        val files: Int,
        val bytes: Long
    )

    fun classify(project: File): ProjectKind {
        if (!project.isDirectory) return ProjectKind.UNKNOWN
        if (File(project, "settings.gradle").isFile ||
            File(project, "settings.gradle.kts").isFile ||
            File(project, "build.gradle").isFile ||
            File(project, "build.gradle.kts").isFile
        ) return ProjectKind.ANDROID_GRADLE
        if (File(project, "index.html").isFile || File(project, "index.htm").isFile) {
            return ProjectKind.STATIC_WEBSITE
        }
        if (project.walkTopDown().maxDepth(3).any {
                it.isFile && it.extension.equals("pon", ignoreCase = true)
            }) return ProjectKind.PONYATNY
        if (project.walkTopDown().maxDepth(3).any {
                it.isFile && it.extension.equals("py", ignoreCase = true)
            }) return ProjectKind.PYTHON
        return ProjectKind.UNKNOWN
    }

    fun buildStaticWebsite(context: Context, project: File): BuildResult {
        require(project.isDirectory) { "Папка проекта не найдена." }
        val root = project.canonicalFile
        require(File(root, "index.html").isFile || File(root, "index.htm").isFile) {
            "Для локальной сборки сайта нужен index.html или index.htm в корне проекта."
        }

        val ignored = setOf(".git", ".gradle", ".idea", ".kotlin", "node_modules", "build", "out", ".cache", ".next", "coverage", ".venv", "venv")
        val output = File(context.cacheDir, "stroka-static-site.zip")
        var count = 0
        var total = 0L
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            root.walkTopDown()
                .onEnter { directory ->
                    directory == root || (directory.name !in ignored &&
                        (!directory.name.startsWith(".") || directory.name == ".well-known") &&
                        directory.canonicalPath.startsWith(root.path + File.separator))
                }
                .filter { it.isFile && !it.name.equals(".env", true) && !it.name.startsWith(".env.") &&
                    !it.name.endsWith(".jks", true) && !it.name.endsWith(".keystore", true) &&
                    !it.name.endsWith(".p12", true) && !it.name.endsWith(".pem", true) &&
                    it.name != "id_rsa" && it.name != "id_ed25519" }
                .forEach { file ->
                    val canonical = file.canonicalFile
                    require(canonical.path.startsWith(root.path + File.separator)) {
                        "Обнаружен файл за пределами папки проекта: ${file.name}"
                    }
                    val relative = canonical.relativeTo(root).invariantSeparatorsPath
                    if (relative.isBlank() || relative == "stroka-build-info.txt") return@forEach
                    zip.putNextEntry(ZipEntry(relative))
                    canonical.inputStream().buffered().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            zip.write(buffer, 0, read)
                            total += read
                        }
                    }
                    zip.closeEntry()
                    count++
                }
            zip.putNextEntry(ZipEntry("stroka-build-info.txt"))
            zip.write(("Built locally by Строка\\nFiles: $count\\nSource: static website\\n").toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        require(count > 0) { "В проекте не найдено файлов для упаковки." }

        return if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "stroka-website-build.zip")
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/StrokaBuild")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val existing = resolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
                arrayOf("stroka-website-build.zip", "Download/StrokaBuild/"),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) Uri.withAppendedPath(collection, cursor.getLong(0).toString()) else null
            }
            val uri = existing ?: resolver.insert(collection, values)
                ?: throw IllegalStateException("Не удалось создать ZIP в Загрузках.")
            try {
                resolver.openOutputStream(uri, "wt")?.use { out ->
                    output.inputStream().buffered().use { it.copyTo(out) }
                } ?: throw IllegalStateException("Не удалось записать ZIP в Загрузки.")
                resolver.update(uri, ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }, null, null)
            } catch (error: Exception) {
                if (existing == null) resolver.delete(uri, null, null)
                throw error
            }
            BuildResult(uri, null, count, total)
        } else {
            val downloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.cacheDir
            val destination = File(downloads, "stroka-website-build.zip")
            output.copyTo(destination, overwrite = true)
            BuildResult(null, destination.absolutePath, count, total)
        }
    }
}
