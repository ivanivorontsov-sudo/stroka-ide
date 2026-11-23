package org.ivanivorontsov.stroka.build

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.min

/** Creates a square, padded launcher icon from a photo or a system emoji glyph. */
object IconComposer {
    private const val SIZE = 512
    private val emojis = listOf("🚀", "🎮", "🧩", "🌟", "🔥", "🐱", "🦊", "🌈", "⚡", "🎨", "🤖", "🍀", "🎵", "📚", "🛠️", "💎")

    fun randomEmoji(): String = emojis.random()

    fun fromUri(context: Context, uri: Uri): ByteArray {
        val source = context.contentResolver.openInputStream(uri)?.use { input ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw BuildException("Не удалось прочитать изображение.")
            var sample = 1
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            BitmapFactory.decodeStream(input, null, opts)
        } ?: throw BuildException("Не удалось открыть выбранное изображение.")
        return try { render(source, null) } finally { source.recycle() }
    }

    fun emojiPng(emoji: String): ByteArray = render(null, emoji.ifBlank { randomEmoji() })

    fun resizePng(data: ByteArray, size: Int): ByteArray {
        val source = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return PngIcon.launcher(size)
        return try {
            val scaled = Bitmap.createScaledBitmap(source, size, size, true)
            try { encode(scaled) } finally { if (scaled !== source) scaled.recycle() }
        } finally { source.recycle() }
    }

    private fun render(source: Bitmap?, emoji: String?): ByteArray {
        val output = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val anti = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val background = if (source != null) {
            try { source.getPixel(source.width / 2, source.height / 2) or (0xff shl 24) }
            catch (_: Exception) { Color.rgb(36, 74, 120) }
        } else Color.rgb(36, 74, 120)
        canvas.drawColor(Color.TRANSPARENT)
        canvas.drawRoundRect(RectF(12f, 12f, 500f, 500f), 112f, 112f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background })
        if (source != null) {
            val box = RectF(64f, 64f, 448f, 448f)
            val scale = min(box.width() / source.width, box.height() / source.height)
            val w = source.width * scale
            val h = source.height * scale
            val dst = RectF((SIZE - w) / 2f, (SIZE - h) / 2f, (SIZE + w) / 2f, (SIZE + h) / 2f)
            val clip = Path().apply { addRoundRect(RectF(28f, 28f, 484f, 484f), 96f, 96f, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clip)
            canvas.drawBitmap(source, null, dst, anti)
            canvas.restore()
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = 260f
                typeface = Typeface.DEFAULT
            }
            val glyph = emoji ?: "🚀"
            val baseline = SIZE / 2f - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(glyph, SIZE / 2f, baseline, paint)
        }
        return try { encode(output) } finally { output.recycle() }
    }

    private fun encode(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw BuildException("Не удалось создать PNG иконку.")
        return out.toByteArray()
    }
}
