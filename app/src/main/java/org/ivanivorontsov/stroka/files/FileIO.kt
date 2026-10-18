package org.ivanivorontsov.stroka.files

import android.content.Context
import android.net.Uri
import java.io.File
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object FileIO {
    const val MAX_BYTES = 8 * 1024 * 1024

    class TooLarge(val size: Long) : Exception("Файл слишком большой: ${size / 1024} КБ")

    fun read(ctx: Context, ref: DocRef): String {
        val bytes = when (ref) {
            is DocRef.Local -> {
                val f = File(ref.path)
                if (f.length() > MAX_BYTES) throw TooLarge(f.length())
                f.readBytes()
            }
            is DocRef.Saf -> ctx.contentResolver.openInputStream(Uri.parse(ref.uri))?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) throw TooLarge(total)
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            } ?: ByteArray(0)
        }
        return decode(bytes)
    }

    fun decode(bytes: ByteArray): String {
        var b = bytes
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) b = b.copyOfRange(3, b.size)
        val utf8 = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            utf8.decode(java.nio.ByteBuffer.wrap(b)).toString()
        } catch (e: CharacterCodingException) {
            String(b, Charset.forName("windows-1251"))
        }.replace("\r\n", "\n")
    }

    fun write(ctx: Context, ref: DocRef, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        when (ref) {
            is DocRef.Local -> {
                val f = File(ref.path)
                f.parentFile?.mkdirs()
                val tmp = File(f.parentFile, "." + f.name + ".tmp")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(f)) {
                    f.writeBytes(bytes)
                    tmp.delete()
                }
            }
            is DocRef.Saf -> {
                val out = ctx.contentResolver.openOutputStream(Uri.parse(ref.uri), "wt")
                    ?: throw java.io.IOException("Нет доступа на запись")
                out.use { it.write(bytes) }
            }
        }
    }
}
