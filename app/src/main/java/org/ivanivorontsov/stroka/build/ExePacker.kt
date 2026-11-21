
package org.ivanivorontsov.stroka.build

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ExePacker {
    private val MAGIC = "IVKPACK1".toByteArray(Charsets.US_ASCII)

    fun pack(runner: ByteArray, name: String, id: String, files: Map<String, ByteArray>, out: File): File {
        val payload = payload(files, name, id)
        val trailer = ByteArray(13)
        System.arraycopy(MAGIC, 0, trailer, 0, 8)
        ZipArchive.u32(trailer, 8, runner.size)
        trailer[12] = 0
        out.parentFile?.mkdirs()
        val all = ByteArray(runner.size + payload.size + trailer.size)
        System.arraycopy(runner, 0, all, 0, runner.size)
        System.arraycopy(payload, 0, all, runner.size, payload.size)
        System.arraycopy(trailer, 0, all, runner.size + payload.size, trailer.size)
        out.writeBytes(all)
        return out
    }

    fun payload(files: Map<String, ByteArray>, name: String, id: String): ByteArray {
        val all = ArrayList<Pair<String, ByteArray>>(files.size + 2)
        all.add("__name" to name.toByteArray(Charsets.UTF_8))
        all.add("__id" to id.toByteArray(Charsets.UTF_8))
        for ((n, d) in files) all.add(n.replace('\\', '/') to d)
        val out = ByteArrayOutputStream()
        val c = ByteArray(4)
        ZipArchive.u32(c, 0, all.size)
        out.write(c)
        for ((n, d) in all) {
            val nb = n.toByteArray(Charsets.UTF_8)
            val h = ByteArray(2)
            ZipArchive.u16(h, 0, nb.size)
            val l = ByteArray(4)
            ZipArchive.u32(l, 0, d.size)
            out.write(h)
            out.write(nb)
            out.write(l)
            out.write(d)
        }
        return out.toByteArray()
    }

    fun inspect(buf: ByteArray): Triple<Int, ByteArray, Boolean>? {
        if (buf.size < 13) return null
        val off = buf.size - 13
        if (!buf.copyOfRange(off, off + 8).contentEquals(MAGIC)) return null
        val base = ZipArchive.le32(buf, off + 8)
        if (base < 0 || base >= buf.size - 13) return null
        val inst = buf[off + 12].toInt() != 0
        return Triple(base, buf.copyOfRange(base, off), inst)
    }
}
