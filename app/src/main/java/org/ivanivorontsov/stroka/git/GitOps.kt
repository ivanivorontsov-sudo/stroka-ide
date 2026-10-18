package org.ivanivorontsov.stroka.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

class GitOps(private val log: (String) -> Unit) {

    data class Creds(val user: String, val token: String?, val name: String, val email: String)

    private fun provider(c: Creds): CredentialsProvider? =
        c.token?.takeIf { it.isNotBlank() }?.let { UsernamePasswordCredentialsProvider(c.user.ifBlank { "x-access-token" }, it) }

    private val monitor = object : ProgressMonitor {
        private var task = ""
        private var total = 0
        private var done = 0
        private var lastPct = -1
        override fun start(totalTasks: Int) {}
        override fun beginTask(title: String?, totalWork: Int) {
            task = title ?: ""; total = totalWork; done = 0; lastPct = -1
        }
        override fun update(completed: Int) {
            done += completed
            if (total > 0) {
                val pct = done * 100 / total
                if (pct / 25 != lastPct / 25) { lastPct = pct; log("$task: $pct%") }
            }
        }
        override fun endTask() {}
        override fun isCancelled(): Boolean = false
    }

    fun isRepo(dir: File): Boolean = File(dir, ".git").isDirectory

    fun findRepoRoot(start: File?): File? {
        var d = start
        while (d != null) {
            if (isRepo(d)) return d
            d = d.parentFile
        }
        return null
    }

    fun clone(url: String, target: File, c: Creds) {
        log("Клонирую $url …")
        Git.cloneRepository().setURI(url).setDirectory(target).setCredentialsProvider(provider(c)).setProgressMonitor(monitor).call().use {
            log("Готово: ${target.name}, ветка ${it.repository.branch}")
        }
    }

    fun init(dir: File, remote: String?) {
        Git.init().setDirectory(dir).setInitialBranch("main").call().use { git ->
            if (!remote.isNullOrBlank()) {
                git.remoteAdd().setName("origin").setUri(URIish(remote)).call()
                log("Удалённый репозиторий origin → $remote")
            }
            log("Создан репозиторий в ${dir.name}")
        }
    }

    fun status(dir: File) {
        Git.open(dir).use { git ->
            val s = git.status().call()
            log("Ветка: ${git.repository.branch}")
            val lines = ArrayList<String>()
            s.added.forEach { lines.add("  добавлен:  $it") }
            s.changed.forEach { lines.add("  изменён:   $it") }
            s.modified.forEach { lines.add("  изменён*:  $it") }
            s.untracked.forEach { lines.add("  новый:     $it") }
            s.missing.forEach { lines.add("  удалён*:   $it") }
            s.removed.forEach { lines.add("  удалён:    $it") }
            s.conflicting.forEach { lines.add("  конфликт:  $it") }
            if (lines.isEmpty()) log("Изменений нет") else lines.take(200).forEach(log)
        }
    }

    fun commit(dir: File, message: String, c: Creds) {
        Git.open(dir).use { git ->
            git.add().addFilepattern(".").call()
            git.add().addFilepattern(".").setUpdate(true).call()
            val name = c.name.ifBlank { c.user.ifBlank { "Строка" } }
            val email = c.email.ifBlank { "${c.user.ifBlank { "user" }}@users.noreply.github.com" }
            val rev = git.commit().setMessage(message).setAuthor(name, email).setCommitter(name, email).call()
            log("Коммит ${rev.name.take(7)}: $message")
        }
    }

    fun pull(dir: File, c: Creds) {
        Git.open(dir).use { git ->
            log("Получаю изменения…")
            val r = git.pull().setCredentialsProvider(provider(c)).setProgressMonitor(monitor).call()
            log(if (r.isSuccessful) "Pull выполнен: ${r.mergeResult?.mergeStatus ?: "ок"}" else "Pull не удался: ${r.mergeResult?.mergeStatus}")
        }
    }

    fun push(dir: File, c: Creds) {
        if (c.token.isNullOrBlank()) throw IllegalStateException("Сначала укажите токен GitHub в настройках")
        Git.open(dir).use { git ->
            log("Отправляю…")
            val results = git.push().setCredentialsProvider(provider(c)).setProgressMonitor(monitor).call()
            for (r in results) for (u in r.remoteUpdates) log("${u.remoteName}: ${u.status}${u.message?.let { " — $it" } ?: ""}")
        }
    }
}
