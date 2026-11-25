package org.ivanivorontsov.stroka.build

import java.io.ByteArrayOutputStream

/**
 * Иконки и картинки для Windows без нативных инструментов: всё собирается на телефоне.
 * Пиксели передаются как ARGB (как Bitmap.getPixels на Android), строка за строкой сверху вниз.
 */
object WinIcon {
    val SIZES = intArrayOf(16, 32, 48, 256)

    fun interface Pixels {
        fun argb(size: Int): IntArray
    }

    private fun dib(n: Int, px: IntArray): ByteArray {
        require(px.size == n * n) { "Нужно ${n * n} пикселей, получено ${px.size}" }
        val maskRow = ((n + 31) / 32) * 4
        val out = ByteArray(40 + n * n * 4 + maskRow * n)
        writeHeader(out, 0, n, n * n * 4 + maskRow * n)
        var p = 40
        for (y in n - 1 downTo 0) {
            for (x in 0 until n) {
                val c = px[y * n + x]
                out[p] = (c and 0xff).toByte()
                out[p + 1] = ((c ushr 8) and 0xff).toByte()
                out[p + 2] = ((c ushr 16) and 0xff).toByte()
                out[p + 3] = ((c ushr 24) and 0xff).toByte()
                p += 4
            }
        }
        return out
    }

    private fun writeHeader(b: ByteArray, off: Int, n: Int, imageSize: Int) {
        ZipArchive.u32(b, off, 40)
        ZipArchive.u32(b, off + 4, n)
        ZipArchive.u32(b, off + 8, n * 2)
        ZipArchive.u16(b, off + 12, 1)
        ZipArchive.u16(b, off + 14, 32)
        ZipArchive.u32(b, off + 16, 0)
        ZipArchive.u32(b, off + 20, imageSize)
        for (i in 24 until 40) b[off + i] = 0
    }

    /** Файл .ico (16, 32, 48 и 256 точек) — для ярлыков и списка программ Windows. */
    fun ico(pixels: Pixels): ByteArray {
        val images = SIZES.map { dib(it, pixels.argb(it)) }
        val out = ByteArrayOutputStream()
        val head = ByteArray(6 + 16 * images.size)
        ZipArchive.u16(head, 0, 0)
        ZipArchive.u16(head, 2, 1)
        ZipArchive.u16(head, 4, images.size)
        var off = head.size
        for ((i, n) in SIZES.withIndex()) {
            val e = 6 + 16 * i
            head[e] = (n % 256).toByte()
            head[e + 1] = (n % 256).toByte()
            head[e + 2] = 0
            head[e + 3] = 0
            ZipArchive.u16(head, e + 4, 1)
            ZipArchive.u16(head, e + 6, 32)
            ZipArchive.u32(head, e + 8, images[i].size)
            ZipArchive.u32(head, e + 12, off)
            off += images[i].size
        }
        out.write(head)
        images.forEach { out.write(it) }
        return out.toByteArray()
    }

    /** 32-битный BMP для картинки слева в окне установщика. */
    fun bmp(n: Int, px: IntArray): ByteArray {
        require(px.size == n * n)
        val data = n * n * 4
        val out = ByteArray(54 + data)
        out[0] = 'B'.code.toByte(); out[1] = 'M'.code.toByte()
        ZipArchive.u32(out, 2, out.size)
        ZipArchive.u32(out, 10, 54)
        ZipArchive.u32(out, 14, 40)
        ZipArchive.u32(out, 18, n)
        ZipArchive.u32(out, 22, n)
        ZipArchive.u16(out, 26, 1)
        ZipArchive.u16(out, 28, 32)
        ZipArchive.u32(out, 34, data)
        var p = 54
        for (y in n - 1 downTo 0) for (x in 0 until n) {
            val c = px[y * n + x]
            val a = (c ushr 24) and 0xff
            fun blend(v: Int) = (v * a + 255 * (255 - a)) / 255
            out[p] = blend(c and 0xff).toByte()
            out[p + 1] = blend((c ushr 8) and 0xff).toByte()
            out[p + 2] = blend((c ushr 16) and 0xff).toByte()
            out[p + 3] = 0
            p += 4
        }
        return out
    }

    /**
     * Заменяет картинку значка внутри готового шаблона EXE. В шаблонах (stroka-qjs-stub.exe и
     * stroka-setup-stub.exe) значок хранится как 32-битные DIB размером 16, 32, 48 и 256 точек;
     * их размеры фиксированы, поэтому таблицы ресурсов PE переписывать не нужно.
     * Возвращает число заменённых картинок.
     */
    fun patchExe(exe: ByteArray, pixels: Pixels): Int {
        var patched = 0
        for (n in SIZES) {
            val maskRow = ((n + 31) / 32) * 4
            val imageSize = n * n * 4 + maskRow * n
            val pattern = ByteArray(40)
            writeHeader(pattern, 0, n, imageSize)
            var from = 0
            while (true) {
                val at = indexOf(exe, pattern.copyOfRange(0, 24), from)
                if (at < 0) break
                if (at + 40 + imageSize <= exe.size) {
                    val img = dib(n, pixels.argb(n))
                    System.arraycopy(img, 0, exe, at, img.size)
                    patched++
                }
                from = at + 40
            }
        }
        return patched
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray, from: Int): Int {
        if (needle.isEmpty()) return -1
        val first = needle[0]
        var i = from
        val last = hay.size - needle.size
        outer@ while (i <= last) {
            if (hay[i] != first) { i++; continue }
            for (j in 1 until needle.size) if (hay[i + j] != needle[j]) { i++; continue@outer }
            return i
        }
        return -1
    }

    /** Простейшее масштабирование ARGB-картинки (для тестов и запасного пути без Android Bitmap). */
    fun scale(src: IntArray, w: Int, h: Int, n: Int): IntArray {
        val out = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val sx = (x * w / n).coerceIn(0, w - 1)
            val sy = (y * h / n).coerceIn(0, h - 1)
            out[y * n + x] = src[sy * w + sx]
        }
        return out
    }
}
