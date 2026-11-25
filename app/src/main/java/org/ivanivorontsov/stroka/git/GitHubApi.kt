package org.ivanivorontsov.stroka.git

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GitHubApiException(val code: Int, message: String) : Exception(message)

/** Небольшой клиент GitHub REST API: pull request'ы и Actions. Адрес API можно подменить (GitHub Enterprise, тесты). */
class GitHubApi(private val token: String, private val base: String = "https://api.github.com") {

    fun request(method: String, path: String, body: JSONObject? = null): String {
        if (token.isBlank()) throw IllegalStateException("Сначала сохраните GitHub-токен в Настройки → Git и GitHub.")
        val connection = URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val json = try { JSONObject(response) } catch (_: Exception) { null }
                var detail = json?.optString("message").orEmpty().ifBlank { response.take(300) }
                val errors = json?.optJSONArray("errors")
                if (errors != null && errors.length() > 0) {
                    val e = errors.optJSONObject(0)
                    detail += " (" + (e?.optString("message").orEmpty().ifBlank { e?.toString().orEmpty() }) + ")"
                }
                throw GitHubApiException(code, "GitHub API HTTP $code: $detail")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    fun login(): String = JSONObject(request("GET", "/user")).optString("login")

    fun defaultBranch(repo: String): String =
        JSONObject(request("GET", "/repos/$repo")).optString("default_branch").ifBlank { "main" }

    /** Создаёт pull request и возвращает его адрес на github.com. */
    fun createPullRequest(repo: String, head: String, baseBranch: String, title: String, body: String = ""): String {
        val json = JSONObject().put("title", title).put("head", head).put("base", baseBranch).put("body", body)
        return JSONObject(request("POST", "/repos/$repo/pulls", json)).optString("html_url")
    }

    companion object {
        /** owner/repo из адреса удалённого репозитория GitHub (https или ssh), иначе null. */
        fun repoFromRemote(url: String?): String? {
            val u = url?.trim() ?: return null
            val m = Regex("""^(?:https?://(?:[^@/]+@)?github\.com/|git@github\.com:|ssh://git@github\.com/)([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\.git)?/?$""").find(u)
                ?: return null
            return m.groupValues[1] + "/" + m.groupValues[2]
        }
    }
}
