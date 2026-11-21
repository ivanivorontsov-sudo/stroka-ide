
package org.ivanivorontsov.stroka.build

import com.android.apksig.ApkSigner
import java.io.File
import java.util.Collections

object ApkPacker {
    const val PLACEHOLDER_PKG = "ru.ivancode.w"
    const val PLACEHOLDER_LABEL = "IVANCODE_APP_LABEL_PLACEHOLDER"

    fun packageName(label: String): String {
        val raw = transliterate(label).lowercase().replace(Regex("[^a-z0-9_]"), "_").trim('_')
        val slug = (if (raw.isEmpty() || !raw[0].isLetter()) "app$raw" else raw).take(40).ifBlank { "app" }
        return "org.stroka.app.$slug"
    }

    fun pack(
        template: ByteArray,
        label: String,
        pkg: String,
        version: String,
        files: Map<String, ByteArray>,
        key: UserKey,
        unsignedOut: File,
        signedOut: File
    ): File {
        val zip = ZipArchive.read(template)
        val out = ArrayList<ZipEntryData>()
        for (e in zip.entries) {
            if (e.name.startsWith("assets/www/")) continue
            if (e.name.startsWith("META-INF/")) continue
            if (e.name == "AndroidManifest.xml") {
                val patched = Axml.replace(
                    e.data(),
                    mapOf(
                        PLACEHOLDER_PKG to pkg,
                        PLACEHOLDER_LABEL to label,
                        "$PLACEHOLDER_PKG.Main" to "$pkg.Main",
                        "1.0" to version
                    )
                )
                out.add(ZipEntryData.of(e.name, patched, false))
                continue
            }
            out.add(e)
        }
        for ((name, data) in files) {
            val path = "assets/www/" + name.replace('\\', '/').trimStart('/')
            val store = name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".webp")
            out.add(ZipEntryData.of(path, data, store))
        }
        unsignedOut.parentFile?.mkdirs()
        unsignedOut.writeBytes(ZipArchive(out).write())
        val cfg = ApkSigner.SignerConfig.Builder("stroka", key.key, Collections.singletonList(key.cert)).build()
        ApkSigner.Builder(listOf(cfg))
            .setInputApk(unsignedOut)
            .setOutputApk(signedOut)
            .setMinSdkVersion(26)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
        unsignedOut.delete()
        return signedOut
    }

    private val RU = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e",
        'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "i", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "",
        'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya"
    )

    fun transliterate(s: String): String {
        val b = StringBuilder()
        for (ch in s) {
            val low = ch.lowercaseChar()
            val t = RU[low]
            if (t != null) b.append(t) else if (ch.isLetterOrDigit()) b.append(low) else b.append('_')
        }
        return b.toString()
    }
}
