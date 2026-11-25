package org.ivanivorontsov.stroka.build

import java.io.File
import java.security.MessageDigest

class Toolchain(
    val aapt2: File,
    val zipalign: File,
    val androidJar: File,
    val coreLambdaStubs: File? = null
) {
    fun check(): String? {
        if (!aapt2.isFile) return "Не найден aapt2: ${aapt2.absolutePath}"
        if (!aapt2.canExecute() && !aapt2.setExecutable(true, false)) {
            return "Нет права на запуск aapt2: ${aapt2.absolutePath}"
        }
        if (!zipalign.isFile) return "Не найден zipalign: ${zipalign.absolutePath}"
        if (!zipalign.canExecute() && !zipalign.setExecutable(true, false)) {
            return "Нет права на запуск zipalign: ${zipalign.absolutePath}"
        }
        if (!androidJar.isFile) return "Не найден android.jar: ${androidJar.absolutePath}"
        if (coreLambdaStubs == null || !coreLambdaStubs.isFile) {
            return "Не найден core-lambda-stubs.jar из Android SDK. Переустановите Строку или обновите набор сборки."
        }
        return null
    }

    companion object {
        fun fromFiles(aapt2: File, zipalign: File, androidJar: File, coreLambdaStubs: File? = null): Toolchain {
            aapt2.setExecutable(true, false)
            zipalign.setExecutable(true, false)
            return Toolchain(aapt2, zipalign, androidJar, coreLambdaStubs)
        }

        fun fromProject(root: File): Toolchain? {
            val abi = hostAbi()
            val aapt2 = File(root, "app/src/main/jniLibs/$abi/libaapt2.so")
            val zipalign = File(root, "app/src/main/jniLibs/$abi/libzipalign.so")
            val platformDir = File(root, "app/src/main/assets/toolchain")
            val jar = File(platformDir, "android.jar")
            val lambda = File(platformDir, "core-lambda-stubs.jar")
            if (aapt2.isFile && zipalign.isFile && jar.isFile) return fromFiles(aapt2, zipalign, jar, lambda)
            return fromSdk(defaultSdk() ?: return null)
        }

        fun fromNativeAndJar(nativeLibDir: File, androidJar: File): Toolchain? {
            val aapt2 = File(nativeLibDir, "libaapt2.so")
            val zipalign = File(nativeLibDir, "libzipalign.so")
            val lambda = File(androidJar.parentFile, "core-lambda-stubs.jar")
            if (!aapt2.isFile || !zipalign.isFile || !androidJar.isFile) return null
            return fromFiles(aapt2, zipalign, androidJar, lambda)
        }

        fun fromSdk(sdk: File): Toolchain? {
            val bt = newestBuildTools(sdk) ?: return null
            val platform = newestAndroidJar(sdk) ?: return null
            val lambda = newestCoreLambdaStubs(sdk) ?: return null
            val aapt2 = File(bt, "aapt2")
            val zipalign = File(bt, "zipalign")
            if (!aapt2.isFile || !zipalign.isFile) return null
            return fromFiles(aapt2, zipalign, platform, lambda)
        }

        fun defaultSdk(): File? {
            for (k in listOf("ANDROID_HOME", "ANDROID_SDK_ROOT")) {
                val v = System.getenv(k)
                if (!v.isNullOrBlank()) {
                    val f = File(v)
                    if (f.isDirectory) return f
                }
            }
            val local = File("/home/box/android")
            return if (local.isDirectory) local else null
        }

        fun hostAbi(): String {
            val a = System.getProperty("os.arch")?.lowercase() ?: ""
            return if (a == "aarch64" || a == "arm64") "arm64-v8a" else "x86_64"
        }

        fun sha256(f: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { inp ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = inp.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { b -> "%02x".format(b) }
        }

        private fun newestBuildTools(sdk: File): File? {
            val dir = File(sdk, "build-tools")
            if (!dir.isDirectory) return null
            return dir.listFiles()?.filter { File(it, "aapt2").isFile }?.maxByOrNull { it.name }
        }

        private fun newestCoreLambdaStubs(sdk: File): File? {
            val dir = File(sdk, "build-tools")
            if (!dir.isDirectory) return null
            return dir.listFiles()
                ?.map { File(it, "core-lambda-stubs.jar") }
                ?.filter { it.isFile }
                ?.maxByOrNull { it.parentFile?.name ?: "" }
        }

        private fun newestAndroidJar(sdk: File): File? {
            val dir = File(sdk, "platforms")
            if (!dir.isDirectory) return null
            return dir.listFiles()
                ?.map { File(it, "android.jar") }
                ?.filter { it.isFile }
                ?.maxByOrNull { it.parentFile?.name ?: "" }
        }
    }
}
