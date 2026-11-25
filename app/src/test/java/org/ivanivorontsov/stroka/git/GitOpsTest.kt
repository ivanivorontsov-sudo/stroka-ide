package org.ivanivorontsov.stroka.git

import org.eclipse.jgit.api.Git
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files

/** Git без GitHub: локальный bare-репозиторий (file://) и локальный HTTP-сервер с проверкой токена. */
class GitOpsTest {
    private lateinit var tmp: File
    private val log = StringBuilder()
    private val ops = GitOps { log.append(it).append('\n') }
    private val me = GitOps.Creds("tester", null, "Тестер", "tester@example.com")
    private var server: Process? = null

    @Before fun setUp() { tmp = Files.createTempDirectory("stroka-git").toFile() }
    @After fun tearDown() { server?.destroy(); tmp.deleteRecursively() }

    private fun bare(name: String): File {
        val d = File(tmp, "$name.git")
        Git.init().setBare(true).setDirectory(d).setInitialBranch("main").call().close()
        return d
    }

    @Test
    fun cloneCommitPushPullBranches() {
        val remote = bare("remote")
        val a = File(tmp, "a"); val b = File(tmp, "b")
        ops.init(a, null)
        Git.open(a).use { it.remoteAdd().setName("origin").setUri(org.eclipse.jgit.transport.URIish(remote.toURI().toString())).call() }
        File(a, "привет.pon").writeText("пиши \"Привет\"\n")
        ops.commit(a, "Первый коммит", me)
        ops.push(a, me)
        ops.clone(remote.toURI().toString(), b, me)
        assertEquals("пиши \"Привет\"\n", File(b, "привет.pon").readText())
        val author = Git.open(b).use { it.log().call().first().authorIdent }
        assertEquals("Тестер", author.name)

        // Пустой коммит не создаётся
        val before = Git.open(a).use { it.log().call().count() }
        ops.commit(a, "ничего", me)
        assertEquals(before, Git.open(a).use { it.log().call().count() })

        // pull: изменения из другой копии
        File(b, "README.md").writeText("# Тест\n")
        ops.commit(b, "README", me); ops.push(b, me)
        ops.pull(a, me)
        assertTrue(File(a, "README.md").isFile)

        // Ветки: создать, отправить, увидеть и переключиться в другой копии
        ops.createBranch(a, "feature/calc")
        File(a, "calc.pon").writeText("функция сложить(а, б)\n    верни а + б\nконец\n")
        ops.commit(a, "Калькулятор", me); ops.push(a, me)
        ops.pull(b, me)
        Git.open(b).use { it.fetch().call() }
        assertTrue(ops.branches(b).toString(), ops.branches(b).contains("feature/calc"))
        ops.checkout(b, "feature/calc")
        assertEquals("feature/calc", ops.currentBranch(b))
        assertTrue(File(b, "calc.pon").isFile)
        ops.pull(b, me)
        ops.checkout(b, "main")
        assertTrue(!File(b, "calc.pon").exists())
        try { ops.createBranch(b, "плохое имя"); fail() } catch (_: IllegalArgumentException) {}

        // Отказ push без pull и понятная ошибка
        ops.checkout(a, "main")
        File(b, "b.txt").writeText("b"); ops.commit(b, "b", me); ops.push(b, me)
        File(a, "a.txt").writeText("a"); ops.commit(a, "a", me)
        try { ops.push(a, me); fail("push должен быть отклонён") } catch (e: IllegalStateException) { assertTrue(e.message, e.message!!.contains("pull")) }
        ops.pull(a, me); ops.push(a, me)

        // Конфликт при pull
        File(a, "README.md").writeText("# A\n"); ops.commit(a, "A", me); ops.push(a, me)
        ops.pull(b, me)
        File(b, "README.md").writeText("# B\n"); ops.commit(b, "B", me)
        File(a, "README.md").writeText("# A2\n"); ops.commit(a, "A2", me); ops.push(a, me)
        try { ops.pull(b, me); fail("ожидался конфликт") } catch (e: IllegalStateException) { assertTrue(e.message, e.message!!.contains("README.md")) }
    }

    @Test
    fun httpRemoteNeedsToken() {
        val py = listOf("python3", "-c", "1").let { try { ProcessBuilder(it).start().waitFor() == 0 } catch (_: Exception) { false } }
        assumeTrue("нужны python3 и git", py)
        val script = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "tools/ci/git-http-server.py") }.firstOrNull { it.isFile }
        assumeTrue(script != null)
        val root = File(tmp, "srv"); root.mkdirs()
        val repo = File(root, "demo.git")
        Git.init().setBare(true).setDirectory(repo).setInitialBranch("main").call().use { it.repository.config.apply { setBoolean("http", null, "receivepack", true); save() } }
        val port = ServerSocket(0).use { it.localPort }
        server = ProcessBuilder("python3", script!!.absolutePath, root.absolutePath, port.toString(), "s3cret-123").redirectErrorStream(true).start()
        Thread.sleep(800)
        val url = "http://127.0.0.1:$port/demo.git"
        val a = File(tmp, "ha")
        ops.init(a, url)
        File(a, "main.py").writeText("print('hi')\n")
        ops.commit(a, "init", me)
        try { ops.push(a, me); fail() } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("токен")) }
        try { ops.push(a, me.copy(token = "wrong")); fail() } catch (e: Exception) { assertTrue(e.toString(), e.toString().contains("not authorized", true) || e.toString().contains("Authentication", true) || e.toString().contains("401")) }
        val good = me.copy(token = "s3cret-123")
        ops.push(a, good)
        val c = File(tmp, "hc")
        ops.clone(url, c, good)
        assertEquals("print('hi')\n", File(c, "main.py").readText())
        File(c, "main.py").writeText("print('hello')\n"); ops.commit(c, "edit", good); ops.push(c, good)
        ops.pull(a, good)
        assertEquals("print('hello')\n", File(a, "main.py").readText())
    }
}
