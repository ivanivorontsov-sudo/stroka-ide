package org.ivanivorontsov.stroka.build

import java.io.File

/** Параметры установщика Windows, который собирается прямо на телефоне. */
data class SetupOptions(
    val title: String,
    val version: String = "1.0",
    val publisher: String = "",
    val id: String = "",
    val website: String = "",
    val desktopShortcut: Boolean = true,
    val startMenu: Boolean = true,
    val runAfter: Boolean = true,
    val license: String = "",
    val icon: WinIcon.Pixels? = null
)

object SetupPacker {
    val MAGIC = "STRSET01".toByteArray(Charsets.US_ASCII)

    fun pack(stub: ByteArray, files: Map<String, ByteArray>, out: File): File {
        if (stub.size < 64 || stub[0] != 'M'.code.toByte() || stub[1] != 'Z'.code.toByte()) {
            throw BuildException("Повреждён шаблон установщика Windows (нет заголовка PE).")
        }
        val payload = StrokaExe.payload(files)
        val trailer = ByteArray(16)
        System.arraycopy(MAGIC, 0, trailer, 0, 8)
        ZipArchive.u32(trailer, 8, stub.size)
        ZipArchive.u32(trailer, 12, 0)
        out.parentFile?.mkdirs()
        val all = ByteArray(stub.size + payload.size + trailer.size)
        System.arraycopy(stub, 0, all, 0, stub.size)
        System.arraycopy(payload, 0, all, stub.size, payload.size)
        System.arraycopy(trailer, 0, all, stub.size + payload.size, trailer.size)
        out.writeBytes(all)
        if (!out.isFile || out.length() < stub.size.toLong() + 16) throw BuildException("Не удалось записать установщик.")
        return out
    }

    private fun clean(s: String, max: Int) = s.replace('\n', ' ').replace('\r', ' ').trim().take(max)

    /**
     * Собирает Setup.exe: шаблон установщика + программа (файлы под app/) + meta.txt,
     * значок (app.ico, он же значок самого Setup.exe), картинка окна и текст лицензии.
     */
    fun build(stub: ByteArray, program: Map<String, ByteArray>, mainExe: String, opt: SetupOptions, out: File): File {
        if (opt.title.isBlank()) throw BuildException("Укажите название программы для установщика.")
        if (!opt.version.matches(Regex("[0-9A-Za-z][0-9A-Za-z.+_-]{0,31}"))) throw BuildException("Версия установщика: только цифры, буквы и . + _ -")
        if (!program.containsKey(mainExe)) throw BuildException("В установщике нет главного файла $mainExe.")
        val stubCopy = stub.copyOf()
        val files = LinkedHashMap<String, ByteArray>()
        val meta = StringBuilder()
        meta.append("title=").append(clean(opt.title, 120)).append('\n')
        meta.append("version=").append(opt.version).append('\n')
        if (opt.publisher.isNotBlank()) meta.append("publisher=").append(clean(opt.publisher, 120)).append('\n')
        meta.append("id=").append(opt.id.ifBlank { DebPacker.debName(opt.title).let { if (it.startsWith("stroka-")) it else "stroka-$it" } }.filter { it.isLetterOrDigit() || it in "-_." }.take(80)).append('\n')
        if (opt.website.isNotBlank()) meta.append("website=").append(clean(opt.website, 300)).append('\n')
        meta.append("exe=").append(mainExe).append('\n')
        meta.append("desktop=").append(if (opt.desktopShortcut) 1 else 0).append('\n')
        meta.append("startmenu=").append(if (opt.startMenu) 1 else 0).append('\n')
        meta.append("run=").append(if (opt.runAfter) 1 else 0).append('\n')
        if (opt.license.isNotBlank()) {
            files["license.txt"] = opt.license.toByteArray(Charsets.UTF_8)
            meta.append("license=license.txt\n")
        }
        val icon = opt.icon
        if (icon != null) {
            files["app.ico"] = WinIcon.ico(icon)
            files["banner.bmp"] = WinIcon.bmp(150, icon.argb(150))
            meta.append("icon=app.ico\nbanner=banner.bmp\n")
            WinIcon.patchExe(stubCopy, icon)
        }
        files["meta.txt"] = meta.toString().toByteArray(Charsets.UTF_8)
        for ((n, d) in program) files["app/" + n.replace('\\', '/').trimStart('/')] = d
        return pack(stubCopy, files, out)
    }

    fun inspect(buf: ByteArray): Pair<Int, Map<String, ByteArray>>? {
        if (buf.size < 16) return null
        val off = buf.size - 16
        if (!buf.copyOfRange(off, off + 8).contentEquals(MAGIC)) return null
        val base = ZipArchive.le32(buf, off + 8)
        if (base < 0 || base >= off) return null
        var p = base
        val count = ZipArchive.le32(buf, p)
        p += 4
        val files = LinkedHashMap<String, ByteArray>()
        repeat(count) {
            if (p + 2 > off) return@repeat
            val nlen = (buf[p].toInt() and 0xff) or ((buf[p + 1].toInt() and 0xff) shl 8)
            p += 2
            if (p + nlen + 4 > off) return@repeat
            val name = String(buf, p, nlen, Charsets.UTF_8)
            p += nlen
            val dlen = ZipArchive.le32(buf, p)
            p += 4
            if (p + dlen > off) return@repeat
            files[name] = buf.copyOfRange(p, p + dlen)
            p += dlen
        }
        return base to files
    }
}
