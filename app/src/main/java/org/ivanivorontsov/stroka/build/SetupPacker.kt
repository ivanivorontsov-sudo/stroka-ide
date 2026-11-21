package org.ivanivorontsov.stroka.build

import java.io.File

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
