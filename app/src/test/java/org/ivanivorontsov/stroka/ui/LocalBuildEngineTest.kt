package org.ivanivorontsov.stroka.ui

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalBuildEngineTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun file(root: File, path: String): File {
        val result = File(root, path)
        result.parentFile?.mkdirs()
        result.writeText("test")
        return result
    }

    @Test
    fun detectsStaticWebsiteWithoutExternalBuildFiles() {
        val root = temporary.newFolder()
        file(root, "index.html")
        file(root, "assets/style.css")
        assertEquals(LocalBuildEngine.ProjectKind.STATIC_WEBSITE, LocalBuildEngine.classify(root))
    }

    @Test
    fun androidGradleProjectTakesPriorityOverWebsiteFiles() {
        val root = temporary.newFolder()
        file(root, "index.html")
        file(root, "app/build.gradle.kts")
        assertEquals(LocalBuildEngine.ProjectKind.ANDROID_GRADLE, LocalBuildEngine.classify(root))
    }

    @Test
    fun detectsPonyatnyAndPythonProjectsForExistingFallbacks() {
        val pony = temporary.newFolder("pony")
        file(pony, "src/main.pon")
        assertEquals(LocalBuildEngine.ProjectKind.PONYATNY, LocalBuildEngine.classify(pony))

        val python = temporary.newFolder("python")
        file(python, "main.py")
        assertEquals(LocalBuildEngine.ProjectKind.PYTHON, LocalBuildEngine.classify(python))
    }

    @Test
    fun unknownProjectIsNotPretendedToBeBuildable() {
        val root = temporary.newFolder()
        file(root, "notes.txt")
        assertEquals(LocalBuildEngine.ProjectKind.UNKNOWN, LocalBuildEngine.classify(root))
    }
}
