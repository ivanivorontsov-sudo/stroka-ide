package org.ivanivorontsov.stroka.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File

sealed class DocRef {
    abstract val key: String
    abstract val name: String

    data class Local(val path: String) : DocRef() {
        override val key get() = "file:$path"
        override val name: String get() = File(path).name
    }

    data class Saf(val uri: String, val displayName: String) : DocRef() {
        override val key get() = "saf:$uri"
        override val name: String get() = displayName
    }

    fun encode(): String = when (this) {
        is Local -> "L\u0001$path"
        is Saf -> "S\u0001$uri\u0001$displayName"
    }

    companion object {
        fun decode(s: String): DocRef? {
            val p = s.split('\u0001')
            return when (p.firstOrNull()) {
                "L" -> if (p.size >= 2) Local(p[1]) else null
                "S" -> if (p.size >= 3) Saf(p[1], p[2]) else null
                else -> null
            }
        }
    }
}

interface FNode {
    val name: String
    val isDir: Boolean
    val key: String
    val localDir: File?
    fun children(): List<FNode>
    fun createFile(name: String): FNode?
    fun createDir(name: String): FNode?
    fun rename(newName: String): Boolean
    fun delete(): Boolean
    fun ref(): DocRef?
}

class LocalNode(val file: File, private val label: String? = null) : FNode {
    override val name: String get() = label ?: file.name
    override val isDir: Boolean get() = file.isDirectory
    override val key: String get() = "file:" + file.absolutePath
    override val localDir: File? get() = if (file.isDirectory) file else file.parentFile

    override fun children(): List<FNode> = (file.listFiles() ?: emptyArray())
        .filter { it.name != ".git" }
        .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
        .map { LocalNode(it) }

    override fun createFile(name: String): FNode? {
        val f = File(file, name)
        f.parentFile?.mkdirs()
        return if (f.exists() || f.createNewFile()) LocalNode(f) else null
    }

    override fun createDir(name: String): FNode? {
        val f = File(file, name)
        return if (f.exists() || f.mkdirs()) LocalNode(f) else null
    }

    override fun rename(newName: String): Boolean = file.renameTo(File(file.parentFile, newName))
    override fun delete(): Boolean = file.deleteRecursively()
    override fun ref(): DocRef? = if (file.isFile) DocRef.Local(file.absolutePath) else null
}

class SafNode(private val ctx: Context, val doc: DocumentFile, private val label: String? = null, val treeUri: Uri? = null) : FNode {
    override val name: String get() = label ?: (doc.name ?: "?")
    override val isDir: Boolean get() = doc.isDirectory
    override val key: String get() = "saf:" + doc.uri.toString()
    override val localDir: File? get() = null

    override fun children(): List<FNode> = doc.listFiles()
        .filter { it.name != ".git" }
        .sortedWith(compareBy<DocumentFile>({ !it.isDirectory }, { (it.name ?: "").lowercase() }))
        .map { SafNode(ctx, it) }

    override fun createFile(name: String): FNode? {
        val existing = doc.findFile(name)
        if (existing != null) return SafNode(ctx, existing)
        val created = doc.createFile(mimeFor(name), name) ?: return null
        return SafNode(ctx, created)
    }

    override fun createDir(name: String): FNode? = (doc.findFile(name) ?: doc.createDirectory(name))?.let { SafNode(ctx, it) }
    override fun rename(newName: String): Boolean = doc.renameTo(newName)
    override fun delete(): Boolean = doc.delete()
    override fun ref(): DocRef? = if (doc.isFile) DocRef.Saf(doc.uri.toString(), doc.name ?: "файл") else null

    companion object {
        fun mimeFor(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "html", "htm" -> "text/html"
                "css" -> "text/css"
                "js", "mjs" -> "text/javascript"
                "json" -> "application/json"
                "xml" -> "text/xml"
                "md" -> "text/markdown"
                else -> "application/octet-stream"
            }
        }

        fun displayName(ctx: Context, tree: Uri): String {
            return try {
                DocumentFile.fromTreeUri(ctx, tree)?.name ?: DocumentsContract.getTreeDocumentId(tree)
            } catch (e: Exception) {
                tree.lastPathSegment ?: "Папка"
            }
        }
    }
}
