
package org.ivanivorontsov.stroka.build

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

class ZipEntryData(
    val name: String,
    val method: Int,
    val crc: Int,
    val csize: Int,
    val usize: Int,
    val raw: ByteArray
) {
    fun data(): ByteArray {
        if (method == 0) return raw
        if (method != 8) throw IllegalStateException("Непонятный метод сжатия ZIP: $method в «$name»")
        val inf = Inflater(true)
        inf.setInput(raw)
        val out = ByteArrayOutputStream(if (usize > 0) usize else raw.size * 2)
        val buf = ByteArray(16384)
        while (!inf.finished()) {
            val n = inf.inflate(buf)
            if (n == 0 && inf.needsInput()) throw IllegalStateException("Повреждён сжатый файл «$name» в шаблоне")
            if (n > 0) out.write(buf, 0, n)
        }
        inf.end()
        return out.toByteArray()
    }

    companion object {
        fun of(name: String, data: ByteArray, store: Boolean): ZipEntryData {
            val raw = if (store) data else deflate(data)
            return ZipEntryData(name, if (store) 0 else 8, Crc32.of(data), raw.size, data.size, raw)
        }

        private fun deflate(data: ByteArray): ByteArray {
            val d = Deflater(9, true)
            d.setInput(data)
            d.finish()
            val out = ByteArrayOutputStream(data.size)
            val buf = ByteArray(16384)
            while (!d.finished()) {
                val n = d.deflate(buf)
                if (n > 0) out.write(buf, 0, n)
            }
            d.end()
            return out.toByteArray()
        }
    }
}

class ZipArchive(val entries: List<ZipEntryData>) {
    fun write(): ByteArray {
        val body = ByteArrayOutputStream()
        val cd = ByteArrayOutputStream()
        var off = 0
        for (e in entries) {
            val nb = e.name.toByteArray(Charsets.UTF_8)
            var pad = 0
            if (e.method == 0) {
                val al = if (e.name.endsWith(".so")) 4096 else 4
                pad = (al - ((off + 30 + nb.size) % al)) % al
            }
            val h = ByteArray(30)
            u32(h, 0, 0x04034b50)
            u16(h, 4, 20)
            u16(h, 6, 0x0800)
            u16(h, 8, e.method)
            u16(h, 10, 0)
            u16(h, 12, 0x21)
            u32(h, 14, e.crc)
            u32(h, 18, e.csize)
            u32(h, 22, e.usize)
            u16(h, 26, nb.size)
            u16(h, 28, pad)
            body.write(h)
            body.write(nb)
            if (pad > 0) body.write(ByteArray(pad))
            body.write(e.raw)
            val c = ByteArray(46)
            u32(c, 0, 0x02014b50)
            u16(c, 4, 20)
            u16(c, 6, 20)
            u16(c, 8, 0x0800)
            u16(c, 10, e.method)
            u16(c, 12, 0)
            u16(c, 14, 0x21)
            u32(c, 16, e.crc)
            u32(c, 20, e.csize)
            u32(c, 24, e.usize)
            u16(c, 28, nb.size)
            u32(c, 42, off)
            cd.write(c)
            cd.write(nb)
            off += 30 + nb.size + pad + e.raw.size
        }
        val catalog = cd.toByteArray()
        val eo = ByteArray(22)
        u32(eo, 0, 0x06054b50)
        u16(eo, 8, entries.size)
        u16(eo, 10, entries.size)
        u32(eo, 12, catalog.size)
        u32(eo, 16, off)
        val packed = body.toByteArray()
        return packed + catalog + eo
    }

    companion object {
        fun read(buf: ByteArray): ZipArchive {
            var e = buf.size - 22
            while (e >= 0 && le32(buf, e) != 0x06054b50) e--
            if (e < 0) throw IllegalStateException("Файл не является ZIP/APK")
            val n = le16(buf, e + 10)
            var p = le32(buf, e + 16)
            val out = ArrayList<ZipEntryData>(n)
            repeat(n) {
                if (le32(buf, p) != 0x02014b50) throw IllegalStateException("Повреждён каталог ZIP")
                val method = le16(buf, p + 10)
                val crc = le32(buf, p + 16)
                val csize = le32(buf, p + 20)
                val usize = le32(buf, p + 24)
                val nl = le16(buf, p + 28)
                val xl = le16(buf, p + 30)
                val cl = le16(buf, p + 32)
                val lho = le32(buf, p + 42)
                val name = String(buf, p + 46, nl, Charsets.UTF_8)
                val lnl = le16(buf, lho + 26)
                val lxl = le16(buf, lho + 28)
                val ds = lho + 30 + lnl + lxl
                if (ds + csize > buf.size) throw IllegalStateException("Повреждён файл «$name» в ZIP")
                val raw = buf.copyOfRange(ds, ds + csize)
                out.add(ZipEntryData(name, method, crc, csize, usize, raw))
                p += 46 + nl + xl + cl
            }
            return ZipArchive(out)
        }

        fun le16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8)
        fun le32(b: ByteArray, o: Int): Int =
            (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or
                ((b[o + 2].toInt() and 0xff) shl 16) or ((b[o + 3].toInt() and 0xff) shl 24)

        fun u16(b: ByteArray, o: Int, v: Int) {
            b[o] = (v and 0xff).toByte()
            b[o + 1] = ((v ushr 8) and 0xff).toByte()
        }

        fun u32(b: ByteArray, o: Int, v: Int) {
            b[o] = (v and 0xff).toByte()
            b[o + 1] = ((v ushr 8) and 0xff).toByte()
            b[o + 2] = ((v ushr 16) and 0xff).toByte()
            b[o + 3] = ((v ushr 24) and 0xff).toByte()
        }
    }
}
