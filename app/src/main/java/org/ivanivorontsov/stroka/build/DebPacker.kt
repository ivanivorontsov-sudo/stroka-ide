package org.ivanivorontsov.stroka.build

import java.io.File

object DebPacker {
    fun debName(label: String): String {
        val slug = ApkPacker.packageName(label).removePrefix("org.stroka.app.").replace('_', '-')
        return "stroka-" + slug.ifBlank { "app" }
    }

    fun build(
        label: String,
        version: String,
        qjs: ByteArray,
        files: Map<String, ByteArray>,
        icon: ByteArray,
        outDeb: File,
        log: (String) -> Unit = {}
    ): File {
        if (qjs.size < 64) throw BuildException("Нет встроенного QuickJS для Linux x86_64. Пакет .deb для ARM пока не собирается.")
        log("Собираю пакет .deb")
        val pkg = debName(label)
        val bin = pkg
        val lib = "usr/lib/stroka-apps/$pkg"
        val wrapper = """
            |#!/bin/sh
            |DIR="/usr/lib/stroka-apps/$pkg"
            |exec "${'$'}DIR/qjs" --std "${'$'}DIR/main.js" "${'$'}@"
            |""".trimMargin() + "\n"
        val desktop = """
            |[Desktop Entry]
            |Type=Application
            |Name=$label
            |Comment=Программа, собранная в редакторе Строка
            |Exec=/usr/bin/$bin
            |Icon=$pkg
            |Terminal=true
            |Categories=Utility;
            |""".trimMargin() + "\n"
        val dataFiles = LinkedHashMap<String, ByteArray>()
        dataFiles["$lib/qjs"] = qjs
        dataFiles["usr/bin/$bin"] = wrapper.toByteArray(Charsets.UTF_8)
        dataFiles["usr/share/applications/$pkg.desktop"] = desktop.toByteArray(Charsets.UTF_8)
        dataFiles["usr/share/icons/hicolor/48x48/apps/$pkg.png"] = icon
        for ((n, d) in files) {
            val name = n.replace('\\', '/').trimStart('/')
            dataFiles["$lib/$name"] = d
        }
        val dataTar = ArTar.tar(dataFiles, setOf("$lib/qjs", "usr/bin/$bin"))
        val dataGz = ArTar.gzip(dataTar)
        val installed = (dataFiles.values.sumOf { it.size.toLong() } + 1023) / 1024
        val control = """
            |Package: $pkg
            |Version: $version
            |Section: utils
            |Priority: optional
            |Architecture: amd64
            |Maintainer: Stroka <stroka@local>
            |Installed-Size: $installed
            |Description: $label (собрано в Строке)
            | Программа на «Понятном» или JavaScript, собранная в редакторе «Строка».
            | В комплект входит интерпретатор QuickJS (x86_64). Пакет не подписан.
            |""".trimMargin() + "\n"
        val controlTar = ArTar.tar(mapOf("control" to control.toByteArray(Charsets.UTF_8)))
        val controlGz = ArTar.gzip(controlTar)
        val deb = ArTar.ar(
            listOf(
                "debian-binary" to "2.0\n".toByteArray(Charsets.US_ASCII),
                "control.tar.gz" to controlGz,
                "data.tar.gz" to dataGz
            )
        )
        outDeb.parentFile?.mkdirs()
        outDeb.writeBytes(deb)
        if (!outDeb.isFile || outDeb.length() < 64) throw BuildException("Не удалось записать пакет .deb")
        return outDeb
    }
}
