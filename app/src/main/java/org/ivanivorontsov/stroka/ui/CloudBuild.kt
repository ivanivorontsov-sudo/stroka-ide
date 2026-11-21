package org.ivanivorontsov.stroka.ui

import org.eclipse.jgit.api.Git
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object CloudBuild {
    data class Result(val repo: String, val branch: String, val actionsUrl: String)
    private data class GitState(val remote: String, val branch: String, val clean: Boolean, val pushed: Boolean)

    fun dispatch(directory: File, target: String, token: String): Result {
        require(token.isNotBlank()) { "GitHub-токен не задан" }
        val (remote, branch, clean, pushed) = Git.open(directory).use { git ->
            val currentBranch = git.repository.branch
            val localHead = git.repository.resolve("HEAD")
            val remoteHead = git.repository.resolve("refs/remotes/origin/$currentBranch")
            GitState(
                git.repository.config.getString("remote", "origin", "url").orEmpty(),
                currentBranch,
                git.status().call().isClean,
                localHead != null && remoteHead != null && localHead == remoteHead
            )
        }
        require(clean) { "В Git есть незакоммиченные изменения. Сначала сохраните их коммитом и выполните push: облачная сборка не должна незаметно собирать старый код." }
        require(pushed) { "Локальная ветка не совпадает с origin/$branch. Сначала выполните push последнего коммита, затем повторите сборку." }
        val match = Regex("(?:github\\.com[:/])([^/]+)/([^/]+?)(?:\\.git)?/?$").find(remote)
            ?: throw IllegalStateException("Удалённый адрес origin не похож на GitHub: $remote")
        val owner = match.groupValues[1]
        val name = match.groupValues[2]
        val fullName = "$owner/$name"
        val endpoint = URL("https://api.github.com/repos/$fullName/actions/workflows/project-build.yml/dispatches")
        val body = JSONObject()
            .put("ref", branch)
            .put("inputs", JSONObject().put("target", target).put("project_path", "."))
            .toString()
        val connection = endpoint.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code != 204 && code != 200) {
                val stream = if (code >= 400) connection.errorStream else connection.inputStream
                val detail = stream?.bufferedReader()?.use { it.readText() }.orEmpty().take(1200)
                val reason = when (code) {
                    401 -> "GitHub отклонил токен (401). Проверьте его срок действия."
                    403 -> "Недостаточно прав (403). Нужен доступ Actions: write; для приватного репозитория также Contents."
                    404 -> "Workflow project-build.yml не найден в основной ветке или репозиторий недоступен."
                    else -> "GitHub API вернул HTTP $code."
                }
                throw IllegalStateException("$reason $detail")
            }
        } finally {
            connection.disconnect()
        }
        return Result(fullName, branch, "https://github.com/$fullName/actions/workflows/project-build.yml")
    }
}
