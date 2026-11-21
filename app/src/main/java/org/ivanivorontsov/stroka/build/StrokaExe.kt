package org.ivanivorontsov.stroka.build

import java.io.ByteArrayOutputStream
import java.io.File

object StrokaExe {
    private val MAGIC = "STRQJS01".toByteArray(Charsets.US_ASCII)

    fun pack(stub: ByteArray, files: Map<String, ByteArray>, out: File): File {
        if (stub.size < 64 || stub[0] != 'M'.code.toByte() || stub[1] != 'Z'.code.toByte()) {
            throw BuildException("Повреждён шаблон Windows-программы (нет заголовка PE).")
        }
        val payload = payload(files)
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
        if (!out.isFile || out.length() < stub.size.toLong() + 16) throw BuildException("Не удалось записать EXE.")
        return out
    }

    fun payload(files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        val c = ByteArray(4)
        ZipArchive.u32(c, 0, files.size)
        out.write(c)
        for ((n, d) in files) {
            val name = n.replace('\\', '/').toByteArray(Charsets.UTF_8)
            val h = ByteArray(2)
            ZipArchive.u16(h, 0, name.size)
            val l = ByteArray(4)
            ZipArchive.u32(l, 0, d.size)
            out.write(h)
            out.write(name)
            out.write(l)
            out.write(d)
        }
        return out.toByteArray()
    }
}
