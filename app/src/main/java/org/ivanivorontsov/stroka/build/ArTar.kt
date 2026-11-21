package org.ivanivorontsov.stroka.build

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

object ArTar {
    fun ar(members: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("!<arch>\n".toByteArray(Charsets.US_ASCII))
        for ((name, data) in members) {
            if (name.length > 16) throw BuildException("Слишком длинное имя в ar: $name")
            val hdr = ByteArray(60)
            fun field(off: Int, len: Int, s: String) {
                val b = s.toByteArray(Charsets.US_ASCII)
                for (i in 0 until len) hdr[off + i] = if (i < b.size) b[i] else ' '.code.toByte()
            }
            field(0, 16, name)
            field(16, 12, "0")
            field(28, 6, "0")
            field(34, 6, "0")
            field(40, 8, "100644")
            field(48, 10, data.size.toString())
            hdr[58] = '`'.code.toByte()
            hdr[59] = '\n'.code.toByte()
            out.write(hdr)
            out.write(data)
            if (data.size % 2 != 0) out.write(0)
        }
        return out.toByteArray()
    }

    fun tar(files: Map<String, ByteArray>, modeExec: Set<String> = emptySet()): ByteArray {
        val out = ByteArrayOutputStream()
        val dirs = LinkedHashSet<String>()
        for (name in files.keys) {
            val n = name.trimStart('/').replace('\\', '/')
            var i = n.indexOf('/')
            while (i > 0) {
                dirs.add(n.substring(0, i + 1))
                i = n.indexOf('/', i + 1)
            }
        }
        for (d in dirs.sorted()) out.write(ustar(d, ByteArray(0), 0x40ed, true))
        for ((raw, data) in files) {
            val n = raw.trimStart('/').replace('\\', '/')
            val mode = if (n in modeExec || n.startsWith("usr/bin/")) 0x81ed else 0x81a4
            out.write(ustar(n, data, mode, false))
        }
        out.write(ByteArray(1024))
        return out.toByteArray()
    }

    fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun ustar(name: String, data: ByteArray, mode: Int, dir: Boolean): ByteArray {
        val hdr = ByteArray(512)
        val path = if (dir && !name.endsWith("/")) "$name/" else name
        val nb = path.toByteArray(Charsets.US_ASCII)
        if (nb.size > 100) throw BuildException("Слишком длинный путь в tar: $path")
        System.arraycopy(nb, 0, hdr, 0, nb.size)
        oct(hdr, 100, 8, mode)
        oct(hdr, 108, 8, 0)
        oct(hdr, 116, 8, 0)
        oct(hdr, 124, 12, data.size)
        oct(hdr, 136, 12, 0)
        hdr[156] = if (dir) '5'.code.toByte() else '0'.code.toByte()
        val magic = "ustar\u000000".toByteArray(Charsets.US_ASCII)
        System.arraycopy(magic, 0, hdr, 257, 8)
        for (i in 148 until 156) hdr[i] = ' '.code.toByte()
        var sum = 0
        for (b in hdr) sum += b.toInt() and 0xff
        oct(hdr, 148, 8, sum)
        hdr[155] = ' '.code.toByte()
        val out = ByteArrayOutputStream()
        out.write(hdr)
        if (!dir && data.isNotEmpty()) {
            out.write(data)
            val pad = (512 - (data.size % 512)) % 512
            if (pad > 0) out.write(ByteArray(pad))
        }
        return out.toByteArray()
    }

    private fun oct(b: ByteArray, off: Int, len: Int, v: Int) {
        val s = v.toString(8)
        val start = off + len - 2 - s.length
        for (i in 0 until len) b[off + i] = '0'.code.toByte()
        if (start >= off) System.arraycopy(s.toByteArray(Charsets.US_ASCII), 0, b, start, s.length)
        b[off + len - 1] = 0
    }
}
