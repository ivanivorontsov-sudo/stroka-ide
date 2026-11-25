
package org.ivanivorontsov.stroka.build

import java.io.File

object RealApkBuilder {
    fun build(
        work: File,
        label: String,
        pkg: String,
        versionName: String,
        versionCode: Int,
        www: Map<String, ByteArray>,
        mainJava: String,
        toolchain: Toolchain,
        key: UserKey,
        outApk: File,
        log: (String) -> Unit = {},
        extraJars: List<File> = emptyList(),
        extras: Map<String, ByteArray> = emptyMap(),
        extractNativeLibs: Boolean = false,
        kotlinSources: List<File> = emptyList(),
        kotlinStdlib: File? = null,
        activityClass: String = "Main",
        iconPng: ByteArray? = null
    ): File {
        val err = toolchain.check()
        if (err != null) throw BuildException(err)
        work.mkdirs()
        val src = File(work, "src")
        val res = File(work, "res")
        val assets = File(work, "assets/www")
        val gen = File(work, "gen")
        val classes = File(work, "classes")
        val compiled = File(work, "compiled")
        val out = File(work, "out")
        listOf(src, res, assets, gen, classes, compiled, out).forEach { it.deleteRecursively(); it.mkdirs() }

        log("Готовлю проект $pkg")
        val pkgDir = File(src, pkg.replace('.', '/'))
        pkgDir.mkdirs()
        if (mainJava.isNotBlank()) {
            File(pkgDir, "Main.java").writeText(mainJava.replace("__PACKAGE__", pkg), Charsets.UTF_8)
        }
        val values = File(res, "values")
        values.mkdirs()
        File(values, "strings.xml").writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">${escXml(label)}</string>
</resources>
""",
            Charsets.UTF_8
        )
        val icon = iconPng ?: PngIcon.launcher(192)
        val iconSizes = linkedMapOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192)
        for ((density, size) in iconSizes) {
            val mip = File(res, "mipmap-$density")
            mip.mkdirs()
            File(mip, "ic_launcher.png").writeBytes(IconComposer.resizePng(icon, size))
        }
        val manifest = File(work, "AndroidManifest.xml")
        manifest.writeText(manifestXml(pkg, versionName, versionCode, extractNativeLibs, activityClass), Charsets.UTF_8)
        for ((name, data) in www) {
            val f = File(assets, name.replace('\\', '/'))
            f.parentFile?.mkdirs()
            f.writeBytes(data)
        }

        log("aapt2: ресурсы")
        val resZip = File(compiled, "res.zip")
        Proc.require(
            listOf(toolchain.aapt2.absolutePath, "compile", "--dir", res.absolutePath, "-o", resZip.absolutePath),
            work,
            err = "Не удалось собрать ресурсы aapt2."
        )
        val unsigned = File(out, "unsigned.apk")
        log("aapt2: упаковка")
        Proc.require(
            listOf(
                toolchain.aapt2.absolutePath, "link",
                "-o", unsigned.absolutePath,
                "-I", toolchain.androidJar.absolutePath,
                "--manifest", manifest.absolutePath,
                resZip.absolutePath,
                "--java", gen.absolutePath,
                "--auto-add-overlay",
                "-A", File(work, "assets").absolutePath,
                "--min-sdk-version", "26",
                "--target-sdk-version", "35",
                "--version-code", versionCode.toString(),
                "--version-name", versionName
            ),
            work,
            err = "Не удалось собрать пакет aapt2 link."
        )

        log("Компилирую Java")
        val sources = (gen.walkTopDown() + src.walkTopDown()).filter { it.isFile && it.name.endsWith(".java") }.toList()
        if (sources.isNotEmpty()) JavaCompile.compile(sources, toolchain.androidJar, classes, extraJars, toolchain.coreLambdaStubs)
        if (kotlinSources.isNotEmpty()) {
            val stdlib = kotlinStdlib ?: throw BuildException("Для Kotlin не найден kotlin-stdlib.")
            log("Компилирую Kotlin/JVM")
            KotlinCompile.compile(kotlinSources, toolchain.androidJar, stdlib, classes, listOf(classes) + extraJars)
        }

        log("d8: DEX")
        val classFiles = classes.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }.toList()
        val dexJars = extraJars + listOfNotNull(kotlinStdlib)
        val dex = Dexer.toDex(classFiles, toolchain.androidJar, out, dexJars)

        log("Добавляю classes.dex")
        val withDex = File(out, "with-dex.apk")
        unsigned.copyTo(withDex, overwrite = true)
        val z = ZipArchive.read(withDex.readBytes())
        var entries = z.entries.filter { it.name != "classes.dex" } + ZipEntryData.of("classes.dex", dex.readBytes(), false)
        for ((name, data) in extras) {
            val n = name.replace('\\', '/').trimStart('/')
            entries = entries.filter { it.name != n } + ZipEntryData.of(n, data, n.endsWith(".so"))
        }
        withDex.writeBytes(ZipArchive(entries).write())

        log("Выравниваю APK")
        val aligned = File(out, "aligned.apk")
        Proc.require(
            listOf(toolchain.zipalign.absolutePath, "-f", "-p", "4", withDex.absolutePath, aligned.absolutePath),
            work,
            err = "Не удалось выровнять APK (zipalign)."
        )

        log("Подписываю APK")
        outApk.parentFile?.mkdirs()
        val cfg = com.android.apksig.ApkSigner.SignerConfig.Builder("stroka", key.key, listOf(key.cert)).build()
        com.android.apksig.ApkSigner.Builder(listOf(cfg))
            .setInputApk(aligned)
            .setOutputApk(outApk)
            .setMinSdkVersion(26)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
        if (!outApk.isFile || outApk.length() < 100) throw BuildException("Подписанный APK не получился.")
        log("Готово: ${outApk.absolutePath} (${outApk.length()} байт)")
        return outApk
    }

    fun manifestXml(pkg: String, versionName: String, versionCode: Int, extractNativeLibs: Boolean = false, activityClass: String = "Main"): String = """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="${escXml(pkg)}"
    android:versionCode="$versionCode"
    android:versionName="${escXml(versionName)}">
    <uses-permission android:name="android.permission.VIBRATE"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
    <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/>
    <application android:label="@string/app_name" android:icon="@mipmap/ic_launcher" android:allowBackup="false" android:usesCleartextTraffic="false" android:extractNativeLibs="${if (extractNativeLibs) "true" else "false"}">
        <activity android:name="${escXml(if (activityClass.contains('.')) activityClass else ".$activityClass")}" android:exported="true" android:theme="@android:style/Theme.DeviceDefault.NoActionBar" android:configChanges="orientation|screenSize|keyboard|keyboardHidden|screenLayout|smallestScreenSize|density" android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
    </application>
</manifest>
""".trimIndent() + "\n"

    fun escXml(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
