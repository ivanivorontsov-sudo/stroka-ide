package org.ivanivorontsov.stroka.core

import java.io.File

/**
 * Lightweight, local-only project inspection. Reads only common manifest files
 * from the current project root; never uploads source code or project metadata.
 */
data class VoiceProjectContext(
    val framework: String = "",
    val dependencies: Set<String> = emptySet(),
    val projectRoot: String = ""
) {
    companion object {
        private val markers = listOf(
            "package.json", "pyproject.toml", "requirements.txt", "Pipfile",
            "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
            "Cargo.toml", "go.mod", "composer.json", "pubspec.yaml", "pom.xml"
        )

        fun inspect(file: File?, stopAt: File? = null): VoiceProjectContext {
            if (file == null) return VoiceProjectContext()
            var dir = if (file.isDirectory) file else file.parentFile
            var selected: File? = null
            repeat(6) {
                val current = dir ?: return@repeat
                if (markers.any { File(current, it).isFile } || File(current, ".git").exists()) {
                    selected = current
                    return@repeat
                }
                if (stopAt != null && current.absoluteFile == stopAt.absoluteFile) return@repeat
                dir = current.parentFile
            }
            val root = selected ?: (if (file.isDirectory) file else file.parentFile) ?: return VoiceProjectContext()
            val manifests = markers.map { File(root, it) }.filter { it.isFile }.mapNotNull {
                try { it.readText().take(256_000) } catch (_: Exception) { null }
            }
            val source = manifests.joinToString("\n").lowercase()
            val deps = linkedSetOf<String>()
            val known = listOf(
                "react", "next", "vue", "nuxt", "svelte", "angular", "express", "fastify",
                "nestjs", "vite", "tailwindcss", "bootstrap", "axios", "zod", "prisma",
                "django", "flask", "fastapi", "sqlalchemy", "pydantic", "pygame", "pyqt",
                "pyside6", "pytest", "numpy", "pandas", "opencv-python", "torch", "tensorflow",
                "spring-boot", "ktor", "compose", "room", "retrofit", "okhttp", "junit",
                "serde", "tokio", "axum", "tauri", "godot"
            )
            known.forEach { if (source.contains(it)) deps.add(it) }
            val framework = when {
                "next" in deps -> "next"
                "react" in deps -> "react"
                "vue" in deps || "nuxt" in deps -> "vue"
                "svelte" in deps -> "svelte"
                "angular" in deps -> "angular"
                "nestjs" in deps -> "nestjs"
                "express" in deps -> "express"
                "fastify" in deps -> "fastify"
                "fastapi" in deps -> "fastapi"
                "django" in deps -> "django"
                "flask" in deps -> "flask"
                "spring-boot" in deps -> "spring"
                "ktor" in deps -> "ktor"
                "compose" in deps -> "compose"
                "pygame" in deps -> "pygame"
                "pyqt" in deps || "pyside6" in deps -> "qt"
                "godot" in deps -> "godot"
                else -> ""
            }
            return VoiceProjectContext(framework, deps, root.absolutePath)
        }
    }
}
