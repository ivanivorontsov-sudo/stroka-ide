package org.ivanivorontsov.stroka.ai

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class AiFileOperation(
    val action: String,
    val path: String,
    val content: String = ""
)

data class AiPatchPlan(
    val summary: String,
    val operations: List<AiFileOperation>
)

/** Strict parser and guarded, backup-first project patch applier. */
object AiPatchEngine {
    private const val MAX_OPERATIONS = 100
    private const val MAX_FILE_CHARS = 300_000
    private val blockedNames = listOf("secret", "credential", "password", "token", "keystore", "local.properties")

    fun parse(raw: String): AiPatchPlan {
        val jsonText = extractJson(raw)
        val root = JSONObject(jsonText)
        val summary = root.optString("summary", "").take(4000)
        val array = root.optJSONArray("operations") ?: JSONArray()
        if (array.length() > MAX_OPERATIONS) throw AiException("Слишком много операций: максимум $MAX_OPERATIONS.")
        val ops = ArrayList<AiFileOperation>()
        val seen = HashSet<String>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: throw AiException("Операция №${i + 1} должна быть объектом JSON.")
            val action = o.optString("action", "").lowercase()
            if (action !in setOf("write", "delete")) throw AiException("Неизвестное действие «$action» в операции №${i + 1}.")
            val path = o.optString("path", "").replace('\\', '/').trim()
            validateRelativePath(path)
            if (!seen.add(path)) throw AiException("Путь повторяется в ответе ИИ: $path")
            val body = o.optString("content", "")
            if (action == "write" && body.length > MAX_FILE_CHARS) throw AiException("Файл $path слишком большой.")
            if (action == "delete" && body.isNotEmpty()) throw AiException("Удаление $path не должно содержать content.")
            ops += AiFileOperation(action, path, body)
        }
        return AiPatchPlan(summary, ops)
    }

    fun validateRelativePath(path: String) {
        if (path.isBlank() || path.length > 240 || path.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(path))
            throw AiException("Недопустимый путь файла: $path")
        val parts = path.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." }) throw AiException("Путь выходит за пределы проекта: $path")
        val lower = parts.joinToString("/").lowercase()
        if (blockedNames.any { lower.contains(it) } || parts.any { it.startsWith(".") && it !in setOf(".github") })
            throw AiException("ИИ не может изменять скрытые файлы, ключи и настройки: $path")
    }

    fun apply(rootDir: File, plan: AiPatchPlan, backupDir: File): List<File> {
        val root = rootDir.canonicalFile
        if (!root.isDirectory) throw AiException("Папка проекта не найдена.")
        data class Prepared(val op: AiFileOperation, val target: File, val staged: File?, val backup: File?)
        val prepared = ArrayList<Prepared>()
        val stamp = System.currentTimeMillis().toString()
        val backupRoot = File(backupDir, stamp)
        try {
            for (op in plan.operations) {
                validateRelativePath(op.path)
                val target = File(root, op.path).canonicalFile
                if (!target.path.startsWith(root.path + File.separator)) throw AiException("Путь вышел за пределы проекта: ${op.path}")
                val existing = target.exists()
                if (existing && !target.isFile) throw AiException("Путь не является обычным файлом: ${op.path}")
                if (op.action == "delete" && !existing) throw AiException("Нельзя удалить отсутствующий файл: ${op.path}")
                if (op.action == "write" && existing && target.length() > 2_000_000L) throw AiException("Существующий файл слишком большой для безопасной замены: ${op.path}")
                val backup = if (existing) File(backupRoot, op.path).also { it.parentFile?.mkdirs(); target.copyTo(it, overwrite = true) } else null
                var staged: File? = null
                if (op.action == "write") {
                    target.parentFile?.mkdirs()
                    staged = File(target.parentFile, ".${target.name}.ai-stage-$stamp")
                    FileOutputStream(staged).use { it.write(op.content.toByteArray(Charsets.UTF_8)); it.fd.sync() }
                }
                prepared += Prepared(op, target, staged, backup)
            }
            val changed = ArrayList<File>()
            for (p in prepared) {
                if (p.op.action == "delete") {
                    if (!p.target.delete()) throw AiException("Не удалось удалить ${p.op.path}")
                } else {
                    val stage = p.staged ?: throw AiException("Нет подготовленного файла ${p.op.path}")
                    if (p.target.exists() && !p.target.delete()) throw AiException("Не удалось заменить ${p.op.path}")
                    if (!stage.renameTo(p.target)) {
                        stage.copyTo(p.target, overwrite = true)
                        stage.delete()
                    }
                }
                changed += p.target
            }
            return changed
        } catch (e: Exception) {
            for (p in prepared.asReversed()) {
                try {
                    if (p.backup != null && p.backup.exists()) {
                        p.target.parentFile?.mkdirs()
                        if (p.target.exists()) p.target.delete()
                        p.backup.copyTo(p.target, overwrite = true)
                    } else if (p.op.action == "write" && p.target.exists()) p.target.delete()
                } catch (_: Exception) { }
                try { p.staged?.delete() } catch (_: Exception) { }
            }
            throw if (e is AiException) e else AiException("Не удалось применить изменения: ${e.message}")
        }
    }

    /** Applies a validated plan in a user-granted SAF tree with private backups and rollback. */
    fun applySaf(context: Context, treeUri: Uri, plan: AiPatchPlan, backupDir: File): Map<String, String?> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: throw AiException("Папка SAF больше недоступна.")
        if (!root.isDirectory || !root.canWrite()) throw AiException("Нет права записи в выбранной SAF-папке.")
        data class Prepared(val op: AiFileOperation, val parent: DocumentFile, val name: String, val oldUri: String, val existed: Boolean, val backup: File?)
        val prepared = ArrayList<Prepared>()
        val createdDirs = ArrayList<DocumentFile>()
        val backupRoot = File(backupDir, "saf-${System.currentTimeMillis()}")
        fun mime(name: String): String = when (name.substringAfterLast(".", "").lowercase()) {
            "kt", "kts", "java", "js", "mjs", "ts", "py", "pon", "txt", "md", "css", "html", "htm", "xml", "json", "gradle", "properties", "yaml", "yml", "sh", "c", "h", "cpp", "hpp", "rs", "go", "sql", "svg" -> "text/plain"
            else -> "application/octet-stream"
        }
        try {
            // Resolve targets and back up all existing files before changing any of them.
            for (op in plan.operations) {
                validateRelativePath(op.path)
                val parts = op.path.split("/")
                var parent = root
                for (segment in parts.dropLast(1)) {
                    val next = parent.findFile(segment)
                    if (next == null) {
                        if (op.action == "delete") throw AiException("Не найдена папка для удаления: $segment")
                        val made = parent.createDirectory(segment) ?: throw AiException("Не удалось создать папку: $segment")
                        createdDirs += made
                        parent = made
                    } else {
                        if (!next.isDirectory) throw AiException("Часть пути не является папкой: $segment")
                        parent = next
                    }
                }
                val name = parts.last()
                val existing = parent.findFile(name)
                if (existing != null && !existing.isFile) throw AiException("Путь не является обычным файлом: ${op.path}")
                if (op.action == "delete" && existing == null) throw AiException("Нельзя удалить отсутствующий файл: ${op.path}")
                if (existing != null && existing.length() > 2_000_000L) throw AiException("Файл слишком большой для безопасной замены: ${op.path}")
                var backup: File? = null
                if (existing != null) {
                    val backupFile = File(backupRoot, op.path)
                    backupFile.parentFile?.mkdirs()
                    context.contentResolver.openInputStream(existing.uri)?.use { input -> backupFile.outputStream().use { output -> input.copyTo(output) } }
                        ?: throw AiException("Не удалось создать резервную копию: ${op.path}")
                    backup = backupFile
                }
                prepared += Prepared(op, parent, name, existing?.uri?.toString().orEmpty(), existing != null, backup)
            }
            val updates = LinkedHashMap<String, String?>()
            for (p in prepared) {
                val current = p.parent.findFile(p.name)
                if (p.op.action == "delete") {
                    if (current == null || !current.delete()) throw AiException("Не удалось удалить ${p.op.path}")
                    updates["saf:${p.oldUri}"] = null
                } else {
                    val target = current ?: p.parent.createFile(mime(p.name), p.name) ?: throw AiException("Не удалось создать файл ${p.op.path}")
                    context.contentResolver.openOutputStream(target.uri, "wt")?.use { it.write(p.op.content.toByteArray(Charsets.UTF_8)); it.flush() }
                        ?: throw AiException("Нет доступа на запись: ${p.op.path}")
                    updates["saf:${if (p.existed) p.oldUri else target.uri.toString()}"] = p.op.content
                }
            }
            return updates
        } catch (e: Exception) {
            for (p in prepared.asReversed()) {
                try {
                    val current = p.parent.findFile(p.name)
                    if (p.existed && p.backup?.isFile == true) {
                        val target = current ?: p.parent.createFile(mime(p.name), p.name)
                        if (target != null) context.contentResolver.openOutputStream(target.uri, "wt")?.use { out -> p.backup.inputStream().use { it.copyTo(out) } }
                    } else if (!p.existed) current?.delete()
                } catch (_: Exception) { }
            }
            createdDirs.asReversed().forEach { try { if (it.listFiles().isEmpty()) it.delete() } catch (_: Exception) { } }
            throw if (e is AiException) e else AiException("Не удалось применить SAF-изменения: ${e.message}")
        }
    }

    private fun extractJson(raw: String): String {
        val t = raw.trim()
        if (t.startsWith("{") && t.endsWith("}")) return t
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE).find(t)
        if (fenced != null) return fenced.groupValues[1].trim()
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        if (start >= 0 && end > start) return t.substring(start, end + 1)
        throw AiException("ИИ не вернул JSON с операциями.")
    }
}
