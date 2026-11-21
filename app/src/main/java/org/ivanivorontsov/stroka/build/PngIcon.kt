
package org.ivanivorontsov.stroka.build

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

object PngIcon {
    fun launcher(size: Int = 48): ByteArray {
        val rgba = ByteArray(size * size * 4)
        val r0 = 22
        val cx = (size - 1) / 2.0
        for (y in 0 until size) for (x in 0 until size) {
            val dx = x - cx
            val dy = y - cx
            val i = (y * size + x) * 4
            if (dx * dx + dy * dy <= r0 * r0) {
                rgba[i] = 61
                rgba[i + 1] = 139.toByte()
                rgba[i + 2] = 253.toByte()
                rgba[i + 3] = 255.toByte()
            }
        }
        return png(size, size, rgba)
    }

    fun png(w: Int, h: Int, rgba: ByteArray): ByteArray {
        fun chunk(tag: ByteArray, data: ByteArray): ByteArray {
            val crc = CRC32()
            crc.update(tag)
            crc.update(data)
            val out = ByteArrayOutputStream()
            out.write(u32(data.size))
            out.write(tag)
            out.write(data)
            out.write(u32(crc.value.toInt()))
            return out.toByteArray()
        }
        val raw = ByteArrayOutputStream()
        val row = w * 4
        for (y in 0 until h) {
            raw.write(0)
            raw.write(rgba, y * row, row)
        }
        val def = Deflater(9)
        def.setInput(raw.toByteArray())
        def.finish()
        val comp = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!def.finished()) {
            val n = def.deflate(buf)
            if (n > 0) comp.write(buf, 0, n)
        }
        def.end()
        val ihdr = u32(w) + u32(h) + byteArrayOf(8, 6, 0, 0, 0)
        val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        return sig + chunk(byteArrayOf(0x49, 0x48, 0x44, 0x52), ihdr) + chunk(byteArrayOf(0x49, 0x44, 0x41, 0x54), comp.toByteArray()) + chunk(byteArrayOf(0x49, 0x45, 0x4e, 0x44), byteArrayOf())
    }

    private fun u32(v: Int): ByteArray = byteArrayOf(
        ((v ushr 24) and 0xff).toByte(),
        ((v ushr 16) and 0xff).toByte(),
        ((v ushr 8) and 0xff).toByte(),
        (v and 0xff).toByte()
    )
}
