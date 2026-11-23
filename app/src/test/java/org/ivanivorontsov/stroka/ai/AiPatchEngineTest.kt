package org.ivanivorontsov.stroka.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AiPatchEngineTest {
    @Test
    fun parsesJsonOperationsAndWindowsPaths() {
        val plan = AiPatchEngine.parse("""{"summary":"update","operations":[{"action":"write","path":"src\\Main.kt","content":"class Main"},{"action":"delete","path":"old.txt","content":""}]}""")
        assertEquals(2, plan.operations.size)
        assertEquals("src/Main.kt", plan.operations[0].path)
        assertEquals("delete", plan.operations[1].action)
    }

    @Test(expected = AiException::class)
    fun rejectsPathTraversal() {
        AiPatchEngine.parse("""{"summary":"bad","operations":[{"action":"write","path":"../outside.txt","content":"x"}]}""")
    }

    @Test(expected = AiException::class)
    fun rejectsSecretFiles() {
        AiPatchEngine.parse("""{"summary":"bad","operations":[{"action":"write","path":"local.properties","content":"secret"}]}""")
    }

    @Test
    fun appliesChangesAndCreatesBackup() {
        val root = Files.createTempDirectory("stroka-ai-project-").toFile()
        val backups = Files.createTempDirectory("stroka-ai-backup-").toFile()
        try {
            File(root, "Main.kt").writeText("old")
            val plan = AiPatchPlan("update", listOf(
                AiFileOperation("write", "Main.kt", "new"),
                AiFileOperation("write", "src/Added.kt", "class Added")
            ))
            val changed = AiPatchEngine.apply(root, plan, backups)
            assertEquals(2, changed.size)
            assertEquals("new", File(root, "Main.kt").readText())
            assertEquals("class Added", File(root, "src/Added.kt").readText())
            assertTrue(backups.walkTopDown().any { it.isFile && it.readText() == "old" })
        } finally {
            root.deleteRecursively()
            backups.deleteRecursively()
        }
    }

    @Test
    fun failedDeleteRollsBackEarlierWrites() {
        val root = Files.createTempDirectory("stroka-ai-project-").toFile()
        val backups = Files.createTempDirectory("stroka-ai-backup-").toFile()
        try {
            File(root, "Main.kt").writeText("old")
            val plan = AiPatchPlan("rollback", listOf(
                AiFileOperation("write", "Main.kt", "new"),
                AiFileOperation("delete", "missing.txt")
            ))
            try {
                AiPatchEngine.apply(root, plan, backups)
                throw AssertionError("Expected failure")
            } catch (_: AiException) { }
            assertEquals("old", File(root, "Main.kt").readText())
        } finally {
            root.deleteRecursively()
            backups.deleteRecursively()
        }
    }
}
