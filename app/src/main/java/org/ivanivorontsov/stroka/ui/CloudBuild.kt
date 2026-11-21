package org.ivanivorontsov.stroka.ui

import android.content.Context
import android.util.Base64
import org.eclipse.jgit.api.Git
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object CloudBuild {
    data class Result(val repo: String, val branch: String, val actionsUrl: String)
    private data class GitState(val remote: String, val branch: String, val clean: Boolean, val pushed: Boolean)

    fun dispatch(context: Context, directory: File, sourceFile: File?, target: String, token: String): Result {
        require(token.isNotBlank()) { "GitHub-токен не задан" }
        val state = Git.open(directory).use { git ->
            val branch = git.repository.branch
            val localHead = git.repository.resolve("HEAD")
            val remoteHead = git.repository.resolve("refs/remotes/origin/$branch")
            GitState(
                git.repository.config.getString("remote", "origin", "url").orEmpty(),
                branch,
                git.status().call().isClean,
                localHead != null && remoteHead != null && localHead == remoteHead
            )
        }
        require(state.clean) { "В Git есть незакоммиченные изменения. Сначала сохраните их коммитом и выполните push: облачная сборка не должна незаметно собирать старый код." }
        require(state.pushed) { "Локальная ветка не совпадает с origin/${state.branch}. Сначала выполните push последнего коммита, затем повторите сборку." }
        val match = Regex("(?:github\\.com[:/])([^/]+)/([^/]+?)(?:\\.git)?/?$").find(state.remote)
            ?: throw IllegalStateException("Удалённый адрес origin не похож на GitHub: ${state.remote}")
        val owner = match.groupValues[1]
        val name = match.groupValues[2]
        val fullName = "$owner/$name"
        ensureWorkflow(context, fullName, token)
        val projectPath = findProjectPath(directory, sourceFile)
        val endpoint = "https://api.github.com/repos/$fullName/actions/workflows/project-build.yml/dispatches"
        val payload = JSONObject()
            .put("ref", state.branch)
            .put("inputs", JSONObject().put("target", target).put("project_path", projectPath))
            .toString()
        val (code, response) = request("POST", endpoint, token, payload)
        if (code != 204 && code != 200) {
            val detail = response.take(1200)
            val reason = when (code) {
                401 -> "GitHub отклонил токен (401). Проверьте срок действия."
                403 -> "Недостаточно прав (403). Нужны Actions: write и Contents: write, чтобы Строка могла установить сборочный workflow."
                404 -> "Workflow не найден в основной ветке или репозиторий недоступен."
                else -> "GitHub API вернул HTTP $code."
            }
            throw IllegalStateException("$reason $detail")
        }
        return Result(fullName, state.branch, "https://github.com/$fullName/actions/workflows/project-build.yml")
    }

    private fun ensureWorkflow(context: Context, repo: String, token: String) {
        val (_, metadata) = request("GET", "https://api.github.com/repos/$repo", token)
        val defaultBranch = JSONObject(metadata).optString("default_branch").ifBlank { "main" }
        val path = ".github/workflows/project-build.yml"
        val encodedPath = path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }
        val contentUrl = "https://api.github.com/repos/$repo/contents/$encodedPath?ref=${java.net.URLEncoder.encode(defaultBranch, "UTF-8")}"
        val (readCode, readBody) = request("GET", contentUrl, token)
        if (readCode == 200) return
        if (readCode != 404) throw IllegalStateException("Не удалось проверить сборочный workflow (HTTP $readCode): ${readBody.take(600)}")
        val yaml = context.assets.open("project-build.yml").bufferedReader().use { it.readText() }
        val encoded = Base64.encodeToString(yaml.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val payload = JSONObject()
            .put("message", "Add project build workflow (installed by Stroka)")
            .put("content", encoded)
            .put("branch", defaultBranch)
            .toString()
        val (writeCode, writeBody) = request("PUT", "https://api.github.com/repos/$repo/contents/$encodedPath", token, payload)
        if (writeCode != 201 && writeCode != 200) {
            val reason = if (writeCode == 403) "Для автоматической установки workflow нужно Contents: write." else "GitHub не смог установить workflow (HTTP $writeCode)."
            throw IllegalStateException("$reason ${writeBody.take(800)}")
        }
    }

    private fun findProjectPath(repo: File, source: File?): String {
        var current = source?.let { if (it.isDirectory) it else it.parentFile }
        var candidate: File? = null
        while (current != null && current.absolutePath.startsWith(repo.absolutePath)) {
            val androidRoot = File(current, "settings.gradle").isFile ||
                File(current, "settings.gradle.kts").isFile || File(current, "gradlew").isFile
            val projectRoot = androidRoot ||
                current.listFiles()?.any { it.extension == "sln" || it.extension == "csproj" } == true ||
                File(current, "package.json").isFile || File(current, "pyproject.toml").isFile ||
                File(current, "requirements.txt").isFile || File(current, "index.html").isFile
            if (projectRoot) {
                candidate = current
                if (androidRoot) break
            }
            if (current == repo) break
            current = current.parentFile
        }
        val chosen = candidate ?: repo
        return repo.toPath().relativize(chosen.toPath()).toString().replace(File.separatorChar, '/').ifBlank { "." }
    }

    private fun request(method: String, endpoint: String, token: String, payload: String? = null): Pair<Int, String> {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to body
        } finally {
            connection.disconnect()
        }
    }
}
