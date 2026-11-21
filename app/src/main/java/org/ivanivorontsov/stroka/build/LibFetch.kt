package org.ivanivorontsov.stroka.build

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object LibFetch {
    fun sha256(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(data)
        return md.digest().joinToString("") { b -> "%02x".format(b) }
    }

    fun sha1(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(data)
        return md.digest().joinToString("") { b -> "%02x".format(b) }
    }

    fun get(url: String, limit: Int = 40 * 1024 * 1024): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", "Stroka/1.8")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw BuildException("Не удалось скачать «$url»: код $code.")
            val out = java.io.ByteArrayOutputStream()
            conn.inputStream.use { inp ->
                val buf = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val n = inp.read(buf)
                    if (n <= 0) break
                    total += n
                    if (total > limit) throw BuildException("Файл с «$url» слишком большой.")
                    out.write(buf, 0, n)
                }
            }
            return out.toByteArray()
        } catch (e: BuildException) {
            throw e
        } catch (e: Exception) {
            throw BuildException("Нет сети или не удалось скачать «$url»: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    fun getOptional(url: String): ByteArray? {
        return try {
            get(url)
        } catch (_: Exception) {
            null
        }
    }
}

object LibResolver {
    fun bundledFile(libDir: File?, bundled: String): File? {
        if (libDir == null) return null
        val f = File(libDir, bundled)
        return if (f.isFile) f else null
    }

    fun cacheFile(cacheDir: File?, kind: String, name: String): File? {
        if (cacheDir == null) return null
        return File(File(cacheDir, kind), name)
    }

    fun verify(data: ByteArray, sha256: String?, sha1: String?): Boolean {
        if (sha256 != null && LibFetch.sha256(data) != sha256.lowercase()) return false
        if (sha1 != null && LibFetch.sha1(data) != sha1.lowercase()) return false
        return true
    }

    fun loadOrDownload(item: LibItem, libDir: File?, cacheDir: File?, allowDownload: Boolean, log: (String) -> Unit): ByteArray {
        if (item.bundled != null) {
            val local = bundledFile(libDir, item.bundled)
            if (local != null) {
                val data = local.readBytes()
                if (item.sha256 == null || LibFetch.sha256(data) == item.sha256) return data
            }
        }
        val cacheName = when (item.kind) {
            LibKind.JAVA -> item.maven?.fileName() ?: "${item.id}.jar"
            LibKind.JS -> item.npm?.file ?: "${item.id}.js"
            LibKind.PONYATNY -> "${item.id.replace('/', '-')}.pon"
        }
        val cached = cacheFile(cacheDir, item.kind.name.lowercase(), cacheName)
        if (cached != null && cached.isFile) {
            val data = cached.readBytes()
            if (item.sha256 == null || LibFetch.sha256(data) == item.sha256) return data
        }
        if (!allowDownload) {
            throw BuildException("Нет сети и библиотека «${item.title}» не сохранена в кэше. Подключитесь к интернету или используйте встроенную библиотеку.")
        }
        log("Скачиваю ${item.title}")
        val data: ByteArray
        try {
            data = when {
                item.maven != null -> {
                    val jar = LibFetch.get(item.maven.url())
                    val sha1txt = LibFetch.getOptional(item.maven.url() + ".sha1")?.toString(Charsets.US_ASCII)?.trim()?.split(Regex("\\s+"))?.firstOrNull()
                    if (sha1txt != null && LibFetch.sha1(jar) != sha1txt.lowercase()) {
                        throw BuildException("Контрольная сумма SHA-1 библиотеки «${item.title}» не совпала с Maven Central.")
                    }
                    if (item.sha256 != null && LibFetch.sha256(jar) != item.sha256) {
                        throw BuildException("Контрольная сумма SHA-256 библиотеки «${item.title}» не совпала.")
                    }
                    jar
                }
                item.npm != null -> {
                    val body = LibFetch.get(item.npm.cdnUrl())
                    if (item.sha256 != null && LibFetch.sha256(body) != item.sha256) {
                        throw BuildException("Контрольная сумма SHA-256 библиотеки «${item.title}» не совпала.")
                    }
                    body
                }
                else -> throw BuildException("Неизвестно, откуда брать библиотеку «${item.title}».")
            }
        } catch (e: BuildException) {
            if (e.message?.startsWith("Нет сети") == true || e.message?.contains("не удалось скачать") == true || e.message?.startsWith("Не удалось скачать") == true) {
                throw BuildException("Нет сети и библиотека «${item.title}» не сохранена в кэше. Подключитесь к интернету или используйте встроенную библиотеку.")
            }
            throw e
        }
        if (cached != null) {
            cached.parentFile?.mkdirs()
            cached.writeBytes(data)
        }
        return data
    }

    fun javaJars(imports: Iterable<String>, libDir: File?, cacheDir: File?, allowDownload: Boolean, log: (String) -> Unit): List<File> {
        val needed = LinkedHashSet<String>()
        for (imp in imports) {
            val item = LibCatalog.javaForImport(imp) ?: continue
            needed.add(item.id)
            needed.addAll(item.dependsOn)
        }
        if (needed.isEmpty()) return emptyList()
        val out = ArrayList<File>()
        val work = cacheDir ?: File(System.getProperty("java.io.tmpdir"), "stroka-libs")
        for (id in needed) {
            val item = LibCatalog.items.first { it.id == id }
            val data = loadOrDownload(item, libDir, work, allowDownload, log)
            val dest = File(File(work, "use"), item.maven?.fileName() ?: "$id.jar")
            dest.parentFile?.mkdirs()
            dest.writeBytes(data)
            out.add(dest)
        }
        return out
    }

    fun jsFiles(names: Iterable<String>, libDir: File?, cacheDir: File?, allowDownload: Boolean, log: (String) -> Unit): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        for (n in names) {
            val item = LibCatalog.jsForName(n) ?: continue
            val data = loadOrDownload(item, libDir, cacheDir, allowDownload, log)
            val name = item.npm?.file ?: "$n.js"
            out["libs/$name"] = data
        }
        return out
    }

    fun preparePonyatny(source: String, env: CompileEnv, libDir: File? = null): CompileEnv {
        val std = LinkedHashMap(env.std)
        val files = LinkedHashMap(env.projectFiles)
        val seen = HashSet<String>()
        fun addSource(text: String) {
            for (raw in StdLibs.scan(text)) {
                if (!seen.add(raw)) continue
                val stdName = StdLibs.normalizeStdName(raw)
                val looksStd = raw.startsWith("стд/") || raw.startsWith("std/") || (stdName != null && (stdName in std || LibCatalog.pony(stdName) != null) && !isProjectPath(raw, files, env.projectRoot))
                if (looksStd && stdName != null) {
                    if (stdName in std) {
                        addSource(std[stdName]!!)
                        continue
                    }
                    val item = LibCatalog.pony(stdName)
                    val fromDisk = env.projectRoot?.let { File(File(it, "стд"), "$stdName.pon") }
                    if (fromDisk != null && fromDisk.isFile) {
                        val t = fromDisk.readText(Charsets.UTF_8)
                        std[stdName] = t
                        addSource(t)
                        continue
                    }
                    if (item != null && item.sha256 != null && stdName in std) continue
                    if (item != null) {
                        val cached = LibResolver.cacheFile(env.cacheDir, "ponyatny", "$stdName.pon")
                        if (cached != null && cached.isFile) {
                            val t = cached.readText(Charsets.UTF_8)
                            if (item.sha256 == null || LibFetch.sha256(t.toByteArray(Charsets.UTF_8)) == item.sha256) {
                                std[stdName] = t
                                addSource(t)
                                continue
                            }
                        }
                    }
                    if (!env.allowDownload) {
                        throw BuildException("Нет сети и библиотека «стд/$stdName» не сохранена в кэше. Подключитесь к интернету или используйте встроенную библиотеку.")
                    }
                    env.log("Скачиваю стд/$stdName")
                    val url = LibCatalog.PONYATNY_STD_URL + stdName + ".pon"
                    val data = try {
                        LibFetch.get(url, 2 * 1024 * 1024)
                    } catch (_: Exception) {
                        throw BuildException("Нет сети и библиотека «стд/$stdName» не сохранена в кэше. Подключитесь к интернету или используйте встроенную библиотеку.")
                    }
                    val text = data.toString(Charsets.UTF_8)
                    if (text.startsWith("<!") || text.contains("404")) {
                        throw BuildException("Библиотека «стд/$stdName» не найдена в официальном репозитории «Понятный».")
                    }
                    if (item != null && item.sha256 != null && LibFetch.sha256(data) != item.sha256) {
                        throw BuildException("Контрольная сумма библиотеки «стд/$stdName» не совпала.")
                    }
                    val c = LibResolver.cacheFile(env.cacheDir, "ponyatny", "$stdName.pon")
                    if (c != null) {
                        c.parentFile?.mkdirs()
                        c.writeBytes(data)
                    }
                    std[stdName] = text
                    addSource(text)
                    continue
                }
                val resolved = resolveProject(raw, files, env.projectRoot, env.fileName)
                    ?: throw BuildException("Файл «$raw» не найден. Подключаются файлы .pon из проекта или библиотеки «стд/…».")
                files[resolved.first] = resolved.second
                addSource(resolved.second)
            }
        }
        addSource(source)
        return CompileEnv(env.fileName, std, files, env.projectRoot, env.cacheDir, env.allowDownload, env.log)
    }

    private fun isProjectPath(raw: String, files: Map<String, String>, root: File?): Boolean {
        return resolveProject(raw, files, root, "программа.pon") != null
    }

    fun resolveProject(raw: String, files: Map<String, String>, root: File?, from: String): Pair<String, String>? {
        var rel = raw.replace('\\', '/')
        if (!rel.endsWith(".pon", true)) rel += ".pon"
        val baseDir = from.replace('\\', '/').substringBeforeLast('/', "")
        val joined = if (rel.startsWith("/")) rel.trimStart('/') else {
            val parts = ArrayList<String>()
            if (baseDir.isNotEmpty()) parts.addAll(baseDir.split('/').filter { it.isNotEmpty() })
            for (p in rel.split('/')) {
                when (p) {
                    "", "." -> {}
                    ".." -> { if (parts.isNotEmpty()) parts.removeAt(parts.size - 1) else return null }
                    else -> parts.add(p)
                }
            }
            parts.joinToString("/")
        }
        if (joined in files) return joined to files[joined]!!
        val alt = joined.removeSuffix(".pon")
        if (alt in files) return alt to files[alt]!!
        if (root != null) {
            val f = File(root, joined)
            if (f.isFile && f.name.endsWith(".pon", true)) {
                val can = f.canonicalFile
                val rootCan = root.canonicalFile
                if (can.path.startsWith(rootCan.path + File.separator) || can.path == rootCan.path) {
                    return joined to f.readText(Charsets.UTF_8)
                }
            }
        }
        return null
    }

    fun javaImports(source: String): List<String> =
        Regex("""^\s*import\s+([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)\s*;""", RegexOption.MULTILINE)
            .findAll(source).map { it.groupValues[1] }.toList()

    fun jsRequires(source: String): List<String> {
        val out = ArrayList<String>()
        for (re in listOf(
            Regex("""require\(\s*['"]([^'"]+)['"]\s*\)"""),
            Regex("""from\s+['"]([^'"]+)['"]""")
        )) {
            for (m in re.findAll(source)) out.add(m.groupValues[1])
        }
        return out
    }
}
