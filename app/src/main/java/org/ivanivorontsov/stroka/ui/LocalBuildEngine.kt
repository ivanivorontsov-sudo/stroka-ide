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
import org.json.JSONObject

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
        // A Gradle Android project may keep its only build file in an app/
        // module (common for single-module projects and imported source trees).
        // Inspect the root and immediate module directories before considering
        // index.html, otherwise an Android project containing web assets is
        // incorrectly sent to the static-site packager.
        val hasGradleBuild = project.walkTopDown().maxDepth(2).any { file ->
            file.isFile && (file.name == "settings.gradle" ||
                file.name == "settings.gradle.kts" ||
                file.name == "build.gradle" ||
                file.name == "build.gradle.kts")
        }
        if (hasGradleBuild) return ProjectKind.ANDROID_GRADLE
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

    /**
     * Creates an offline web application for a «Понятный» project. The bundled
     * compiler runs in the browser runtime; no Node.js or Termux is required.
     */
    fun buildPonyatnyWebsite(context: Context, project: File): BuildResult {
        require(project.isDirectory) { "Папка проекта не найдена." }
        val root = project.canonicalFile
        val ignored = setOf(".git", ".gradle", ".idea", "build", "node_modules", ".cache")
        val ponyFiles = root.walkTopDown()
            .onEnter { dir -> dir == root || (dir.name !in ignored && dir.canonicalPath.startsWith(root.path + File.separator)) }
            .filter { it.isFile && it.extension.equals("pon", true) }
            .map { it.canonicalFile }
            .filter { it.path.startsWith(root.path + File.separator) }
            .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
            .toList()
        require(ponyFiles.isNotEmpty()) { "В проекте не найдено файлов .pon." }
        require(ponyFiles.size <= 256) { "Слишком много файлов .pon (максимум 256 для локальной веб-сборки)." }
        ponyFiles.forEach { require(it.length() <= 1_048_576L) { "Файл слишком большой: ${it.name}" } }
        val entry = ponyFiles.firstOrNull { it.name.equals("main.pon", true) } ?: ponyFiles.first()
        val source = entry.readText(Charsets.UTF_8)
        val entryName = entry.relativeTo(root).invariantSeparatorsPath

        val modules = JSONObject()
        ponyFiles.forEach { file ->
            modules.put(file.relativeTo(root).invariantSeparatorsPath, file.readText(Charsets.UTF_8))
        }
        val standard = context.assets.open("ponyatny/std.json").bufferedReader().use { it.readText() }
        val stage = File(context.cacheDir, "stroka-ponyatny-web")
        if (stage.exists()) stage.deleteRecursively()
        check(stage.mkdirs()) { "Не удалось подготовить каталог веб-сборки." }
        context.assets.open("ponyatny/yadro.js").use { input ->
            File(stage, "ponyatny.js").outputStream().use { input.copyTo(it) }
        }
        File(stage, "std.json").writeText(standard, Charsets.UTF_8)
        File(stage, "program.pon").writeText(source, Charsets.UTF_8)

        val appJs = """
(() => {
  const source = ${JSONObject.quote(source)};
  const fileName = ${JSONObject.quote(entryName)};
  const modules = ${modules};
  const standard = $standard;
  const output = document.getElementById("output");
  const editor = document.getElementById("source");
  const status = document.getElementById("status");
  editor.value = source;
  const fileData = new Map();
  const storageKey = "stroka-ponyatny-files-v1";
  try {
    const saved = JSON.parse(localStorage.getItem(storageKey) || "{}");
    Object.keys(saved).forEach(key => fileData.set(key, String(saved[key])));
  } catch (ignored) {}
  function safeFile(path) {
    const parts = [];
    String(path || "").replace(/\\/g, "/").split("/").forEach(part => {
      if (!part || part === ".") return;
      if (part === "..") parts.pop(); else parts.push(part);
    });
    return parts.join("/");
  }
  function persistFiles() {
    try { localStorage.setItem(storageKey, JSON.stringify(Object.fromEntries(fileData))); } catch (ignored) {}
  }
  const virtualFs = {
    readFileSync(path) {
      const key = safeFile(path);
      if (!fileData.has(key)) throw new Error("Файл не найден: " + key);
      return fileData.get(key);
    },
    writeFileSync(path, value) {
      fileData.set(safeFile(path), String(value));
      persistFiles();
    },
    appendFileSync(path, value) {
      const key = safeFile(path);
      fileData.set(key, (fileData.get(key) || "") + String(value));
      persistFiles();
    },
    existsSync(path) { return fileData.has(safeFile(path)); }
  };
  const write = value => { output.textContent += String(value) + "\n"; };
  function cleanPath(value, importer) {
    const raw = String(value || "").replace(/\\/g, "/");
    const parts = (raw.startsWith("./") || raw.startsWith("../"))
      ? String(importer || "").replace(/\\/g, "/").split("/").slice(0, -1) : [];
    for (const part of raw.split("/")) {
      if (!part || part === ".") continue;
      if (part === "..") parts.pop(); else parts.push(part);
    }
    let key = parts.join("/");
    if (!/\.pon$/i.test(key)) key += ".pon";
    return key;
  }
  function resolve(request, importer) {
    const raw = String(request || "").replace(/\\/g, "/");
    const key = cleanPath(raw, importer);
    if (Object.prototype.hasOwnProperty.call(modules, key)) {
      return { id: "project:" + key, name: key, source: modules[key] };
    }
    const direct = raw.replace(/^\.\//, "");
    const directKey = /\.pon$/i.test(direct) ? direct : direct + ".pon";
    if (Object.prototype.hasOwnProperty.call(modules, directKey)) {
      return { id: "project:" + directKey, name: directKey, source: modules[directKey] };
    }
    const standardKey = raw.replace(/^стд\//, "").replace(/\.pon$/i, "");
    if (Object.prototype.hasOwnProperty.call(standard, standardKey)) {
      return { id: "std:" + standardKey, name: "стд/" + standardKey + ".pon", source: standard[standardKey] };
    }
    return null;
  }
  document.getElementById("run").addEventListener("click", () => {
    output.textContent = "";
    try {
      window.PonyatnyYadro.run(editor.value, {
        file: fileName, resolve, write,
        writeErr: value => write("Ошибка: " + value),
        ask: question => window.prompt(String(question || "Введите значение:"), "") ?? "",
        fs: virtualFs, maxSteps: 1000000, guard: true
      });
      status.textContent = "Выполнено";
    } catch (error) {
      status.textContent = "Ошибка";
      write(error && error.message ? error.message : error);
    }
  });
  document.getElementById("save").addEventListener("click", () => {
    const link = document.createElement("a");
    const url = URL.createObjectURL(new Blob([editor.value], {type:"text/plain;charset=utf-8"}));
    link.href = url; link.download = fileName.split("/").pop() || "main.pon"; link.click();
    URL.revokeObjectURL(url);
  });
})();
""".trimIndent()
        File(stage, "app.js").writeText(appJs, Charsets.UTF_8)
        val html = """<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="theme-color" content="#111827"><title>Программа на языке «Понятный»</title>
<style>
*{box-sizing:border-box}body{margin:0;padding:clamp(16px,4vw,40px);background:#0c1427;color:#f4f6ff;font:16px/1.6 system-ui}
main{max-width:1000px;margin:auto}.tag{color:#b9b1ff;font-size:12px;letter-spacing:2px;font-weight:800}
h1{font-size:clamp(30px,6vw,54px);line-height:1.05;letter-spacing:-2px;margin:12px 0}
p{color:#aebbd5}.card{padding:clamp(16px,3vw,28px);border:1px solid #2b3a59;border-radius:22px;background:linear-gradient(145deg,#172440,#111c32);box-shadow:0 24px 60px #0004}
textarea{width:100%;min-height:280px;resize:vertical;background:#080f1e;color:#dbeafe;border:1px solid #354666;border-radius:14px;padding:16px;font:14px/1.7 ui-monospace,monospace}
button{border:0;border-radius:999px;padding:13px 20px;margin:12px 8px 12px 0;background:#bcebd9;color:#102b36;font-weight:800}
button.secondary{background:#263753;color:#edf2ff}pre{min-height:90px;white-space:pre-wrap;overflow-wrap:anywhere;background:#080f1e;border:1px solid #354666;border-radius:14px;padding:16px;color:#c8f3df}
#status{color:#b9b1ff;font-size:13px}.foot{font-size:12px;color:#7789a8;margin-top:20px}
</style></head><body><main><span class="tag">OFFLINE · КОМПИЛЯТОР ВНУТРИ САЙТА</span>
<h1>Ваша программа на языке «Понятный»</h1><p>Изменяйте исходный код, запускайте его прямо в браузере. Компилятор и стандартные модули уже включены — интернет и Node.js не нужны. Файлы программы сохраняются в локальном хранилище браузера.</p>
<section class="card"><h2>Исходный код</h2><textarea id="source" spellcheck="false"></textarea><br>
<button id="run">▶ Запустить программу</button><button class="secondary" id="save">Скачать .pon</button><span id="status">Готово</span>
<h2>Результат выполнения</h2><pre id="output"></pre></section>
<div class="foot">Собрано локально в приложении «Строка». Компилятор «Понятный» и стандартная библиотека работают офлайн.</div>
</main><script src="ponyatny.js"></script><script src="app.js"></script></body></html>"""
        File(stage, "index.html").writeText(html, Charsets.UTF_8)
        return buildStaticWebsite(context, stage)
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
