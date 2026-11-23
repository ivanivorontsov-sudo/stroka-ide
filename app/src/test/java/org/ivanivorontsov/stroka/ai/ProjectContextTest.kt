package org.ivanivorontsov.stroka.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProjectContextTest {
    @Test
    fun includesSourceAndSkipsBuildOutput() {
        val root = createTempDir(prefix = "stroka-project-")
        try {
            File(root, "src").mkdirs()
            File(root, "src/Main.kt").writeText("class MainActivity")
            File(root, "build").mkdirs()
            File(root, "build/generated.kt").writeText("secretGeneratedSource")
            val snapshot = ProjectContext.collect(root)
            assertTrue(snapshot.contains("src/Main.kt"))
            assertTrue(snapshot.contains("class MainActivity"))
            assertFalse(snapshot.contains("secretGeneratedSource"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun excludesBinaryFiles() {
        val root = createTempDir(prefix = "stroka-project-")
        try {
            File(root, "image.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0))
            File(root, "readme.md").writeText("hello")
            val snapshot = ProjectContext.collect(root)
            assertTrue(snapshot.contains("readme.md"))
            assertFalse(snapshot.contains("image.png"))
        } finally {
            root.deleteRecursively()
        }
    }
}
