
package org.ivanivorontsov.stroka.build

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

object Axml {
    fun replace(buf: ByteArray, map: Map<String, String>): ByteArray {
        if (le16(buf, 0) != 3) throw IllegalStateException("AndroidManifest шаблона повреждён (не AXML)")
        val sp = 8
        if (le16(buf, sp) != 1) throw IllegalStateException("В манифесте шаблона нет пула строк")
        val hs = le16(buf, sp + 2)
        val size = le32(buf, sp + 4)
        val cnt = le32(buf, sp + 8)
        val styles = le32(buf, sp + 12)
        val flags = le32(buf, sp + 16)
        val sStart = le32(buf, sp + 20)
        if (styles != 0) throw IllegalStateException("Стили в пуле строк манифеста не поддерживаются")
        val u8 = (flags and 0x100) != 0
        val strs = ArrayList<String>(cnt)
        for (i in 0 until cnt) {
            var p = sp + sStart + le32(buf, sp + hs + i * 4)
            if (u8) {
                var n = buf[p].toInt() and 0xff
                p++
                if ((n and 0x80) != 0) p++
                var bl = buf[p].toInt() and 0xff
                p++
                if ((bl and 0x80) != 0) {
                    bl = ((bl and 0x7f) shl 8) or (buf[p].toInt() and 0xff)
                    p++
                }
                strs.add(String(buf, p, bl, StandardCharsets.UTF_8))
            } else {
                var n = le16(buf, p)
                p += 2
                if ((n and 0x8000) != 0) {
                    n = ((n and 0x7fff) shl 16) or le16(buf, p)
                    p += 2
                }
                strs.add(String(buf, p, n * 2, StandardCharsets.UTF_16LE))
            }
        }
        val ns = strs.map { map[it] ?: it }
        val datas = ns.map { s ->
            val t = s.toByteArray(StandardCharsets.UTF_16LE)
            val n = s.length
            val l = if (n > 0x7fff) {
                val hi = ((n shr 16) and 0x7fff) or 0x8000
                byteArrayOf((hi and 0xff).toByte(), (hi shr 8).toByte(), (n and 0xff).toByte(), (n shr 8).toByte())
            } else {
                byteArrayOf((n and 0xff).toByte(), (n shr 8).toByte())
            }
            l + t + byteArrayOf(0, 0)
        }
        val offs = ByteArray(cnt * 4)
        var o = 0
        datas.forEachIndexed { i, d ->
            ZipArchive.u32(offs, i * 4, o)
            o += d.size
        }
        var sd = datas.fold(ByteArray(0)) { a, b -> a + b }
        if (sd.size % 4 != 0) sd += ByteArray(4 - sd.size % 4)
        val head = buf.copyOfRange(sp, sp + hs)
        val nStart = hs + cnt * 4
        val nSize = nStart + sd.size
        ZipArchive.u32(head, 4, nSize)
        ZipArchive.u32(head, 16, flags and 0x100.inv())
        ZipArchive.u32(head, 20, nStart)
        ZipArchive.u32(head, 24, 0)
        val rest = buf.copyOfRange(sp + size, buf.size)
        val out = ByteArray(8) + head + offs + sd + rest
        System.arraycopy(buf, 0, out, 0, 8)
        ZipArchive.u32(out, 4, out.size)
        return out
    }

    private fun le16(b: ByteArray, o: Int) = ZipArchive.le16(b, o)
    private fun le32(b: ByteArray, o: Int) = ZipArchive.le32(b, o)
}
