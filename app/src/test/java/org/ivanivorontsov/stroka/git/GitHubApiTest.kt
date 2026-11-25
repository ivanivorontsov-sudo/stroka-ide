package org.ivanivorontsov.stroka.git

import org.ivanivorontsov.stroka.MockHttp
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** GitHub REST API на мок-сервере (настоящий GitHub не трогается). */
class GitHubApiTest {
    private var prBody: JSONObject? = null
    private val server = MockHttp { ex ->
        val ok = ex.header("Authorization") == "Bearer test-token"
        when {
            !ok -> 401 to """{"message":"Bad credentials"}"""
            ex.path == "/user" -> 200 to """{"login":"stroka-tester"}"""
            ex.path == "/repos/demo/app" -> 200 to """{"default_branch":"main"}"""
            ex.path == "/repos/demo/app/pulls" && JSONObject(ex.body).optString("head") == "main" ->
                422 to """{"message":"Validation Failed","errors":[{"message":"No commits between main and main"}]}"""
            ex.path == "/repos/demo/app/pulls" && ex.method == "POST" -> { prBody = JSONObject(ex.body); 201 to """{"html_url":"https://github.com/demo/app/pull/7","number":7}""" }
            else -> 404 to """{"message":"Not Found"}"""
        }
    }

    @After fun stop() = server.close()

    private val base get() = server.base

    @Test
    fun pullRequestFlow() {
        val api = GitHubApi("test-token", base)
        assertEquals("stroka-tester", api.login())
        assertEquals("main", api.defaultBranch("demo/app"))
        assertEquals("https://github.com/demo/app/pull/7", api.createPullRequest("demo/app", "feature/calc", "main", "Калькулятор", "Описание"))
        assertEquals("feature/calc", prBody!!.getString("head"))
        assertEquals("Калькулятор", prBody!!.getString("title"))
        try { api.createPullRequest("demo/app", "main", "main", "x"); fail() } catch (e: GitHubApiException) {
            assertEquals(422, e.code); assertTrue(e.message, e.message!!.contains("No commits between"))
        }
        try { GitHubApi("bad", base).login(); fail() } catch (e: GitHubApiException) { assertEquals(401, e.code); assertTrue(e.message!!.contains("Bad credentials")) }
        try { GitHubApi("", base).login(); fail() } catch (_: IllegalStateException) {}
    }

    @Test
    fun repoFromRemote() {
        assertEquals("ivan/stroka", GitHubApi.repoFromRemote("https://github.com/ivan/stroka.git"))
        assertEquals("ivan/stroka", GitHubApi.repoFromRemote("https://x-access-token@github.com/ivan/stroka"))
        assertEquals("ivan/stroka-ide", GitHubApi.repoFromRemote("git@github.com:ivan/stroka-ide.git"))
        assertEquals("a/b.c", GitHubApi.repoFromRemote("https://github.com/a/b.c.git"))
        assertNull(GitHubApi.repoFromRemote("https://gitlab.com/a/b.git"))
        assertNull(GitHubApi.repoFromRemote(null))
    }
}
