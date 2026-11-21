package org.ivanivorontsov.stroka.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Delegates native ARM64 builds to the user's Termux installation.
 * The app never executes downloaded binaries in its own process.
 */
object LocalTermuxBuild {
    private const val TERMUX_PACKAGE = "com.termux"
    private const val RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val RUN_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val RUN_COMMAND_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val RUN_COMMAND_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val RUN_COMMAND_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val RUN_COMMAND_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"

    data class Staged(val zipPath: String, val runnerPath: String)

    fun prepare(context: Context) {
        publishDownload(context, "setup-termux.sh", "text/x-shellscript") { out ->
            context.assets.open("setup-termux.sh").use { it.copyTo(out) }
        }
        val intent = Intent(RUN_COMMAND).apply {
            setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
            action = RUN_COMMAND
            putExtra(RUN_COMMAND_PATH, "/data/data/com.termux/files/usr/bin/bash")
            putExtra(RUN_COMMAND_ARGUMENTS, arrayOf("/sdcard/Download/StrokaBuild/setup-termux.sh"))
            putExtra(RUN_COMMAND_WORKDIR, "/data/data/com.termux/files/home")
            putExtra(RUN_COMMAND_BACKGROUND, false)
            putExtra(RUN_COMMAND_SESSION_ACTION, "0")
        }
        try {
            context.startService(intent)
        } catch (e: SecurityException) {
            throw IllegalStateException("Разрешите для «Строки» дополнительное разрешение «Запуск команд в Termux», а в Termux включите allow-external-apps=true.", e)
        }
    }

    fun start(context: Context, project: File, target: String) {
        require(target == "android-apk" || target == "android-aab") {
            "Локальная сборка на телефоне пока поддерживает Android APK и AAB."
        }
        require(project.isDirectory) { "Папка проекта не найдена: ${project.absolutePath}" }
        context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
            ?: throw IllegalStateException("Termux не установлен. Установите Termux из F-Droid или официального GitHub.")
        val staged = stage(context, project)
        val intent = Intent(RUN_COMMAND).apply {
            setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
            putExtra(RUN_COMMAND_PATH, "/data/data/com.termux/files/usr/bin/bash")
            putExtra(RUN_COMMAND_ARGUMENTS, arrayOf(staged.runnerPath, staged.zipPath, target))
            putExtra(RUN_COMMAND_WORKDIR, "/data/data/com.termux/files/home")
            // Background execution avoids Android's restriction on launching a
            // terminal Activity from another app's background service.
            putExtra(RUN_COMMAND_BACKGROUND, true)
        }
        try {
            context.startService(intent)
            Toast.makeText(context, "Команда передана Termux. Журнал: Загрузки/StrokaBuild/output/build.log. APK появится там после завершения.", Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            throw IllegalStateException(
                "Android заблокировал запуск Termux. Разрешите для «Строки» дополнительное разрешение «Запуск команд в Termux», а в Termux включите allow-external-apps=true.",
                e
            )
        } catch (e: Exception) {
            throw IllegalStateException("Не удалось запустить Termux: ${e.message}", e)
        }
    }

    private fun stage(context: Context, project: File): Staged {
        val zip = File(context.cacheDir, "stroka-project.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            val root = project.canonicalFile
            root.walkTopDown().filter { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                relative.isNotEmpty() && relative.split('/').none {
                    it == ".git" || it == ".gradle" || it == "build" || it == "node_modules" ||
                        it == ".idea" || it == ".kotlin"
                } && file.isFile
            }.forEach { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                out.putNextEntry(ZipEntry(relative))
                file.inputStream().buffered().use { it.copyTo(out) }
                out.closeEntry()
            }
            // A single .pon file is a complete build input: bundle the compiler and
            // project generator so Termux can synthesize the missing Android project.
            val ponyToolchain = listOf(
                "ponyatny/build-ponyatny.mjs" to "tools/build-ponyatny.mjs",
                "ponyatny/yadro.js" to "app/src/main/assets/ponyatny/yadro.js",
                "ponyatny/std.json" to "app/src/main/assets/ponyatny/std.json"
            )
            ponyToolchain.forEach { (assetPath, archivePath) ->
                out.putNextEntry(ZipEntry(archivePath))
                context.assets.open(assetPath).use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        publishDownload(context, "project.zip", "application/zip") { out ->
            zip.inputStream().buffered().use { it.copyTo(out) }
        }
        publishDownload(context, "build-local.sh", "text/x-shellscript") { out ->
            context.assets.open("build-local.sh").use { it.copyTo(out) }
        }
        publishDownload(context, "stroka_runner.py", "text/x-python") { out ->
            context.assets.open("stroka_runner.py").use { it.copyTo(out) }
        }
        publishDownload(context, "stroka_tkinter.py", "text/x-python") { out ->
            context.assets.open("stroka_tkinter.py").use { it.copyTo(out) }
        }
        publishDownload(context, "setup-termux.sh", "text/x-shellscript") { out ->
            context.assets.open("setup-termux.sh").use { it.copyTo(out) }
        }
        val downloads = "/sdcard/Download/StrokaBuild"
        return Staged("$downloads/project.zip", "$downloads/build-local.sh")
    }

    private fun publishDownload(
        context: Context,
        name: String,
        mime: String,
        write: (java.io.OutputStream) -> Unit
    ): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Downloads.RELATIVE_PATH, "Download/StrokaBuild")
        }
        // Reuse the existing staged file instead of letting MediaStore silently
        // rename repeated uploads to "project (1).zip" while the runner expects
        // the stable path "project.zip".
        val relativePath = "Download/StrokaBuild/"
        val existing = resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            if (Build.VERSION.SDK_INT >= 29)
                "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?"
            else "${MediaStore.Downloads.DISPLAY_NAME}=?",
            if (Build.VERSION.SDK_INT >= 29) arrayOf(name, relativePath) else arrayOf(name),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) Uri.withAppendedPath(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                cursor.getLong(0).toString()
            ) else null
        }
        val uri = existing ?: resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Не удалось создать файл в Загрузках: $name")
        try {
            resolver.openOutputStream(uri, "wt")?.use(write)
                ?: throw IllegalStateException("Не удалось записать файл: $name")
        } catch (e: Exception) {
            if (existing == null) resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }
}
