package org.ivanivorontsov.stroka.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.Repository
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
            val st = git.status().call()
            if (st.added.isEmpty() && st.changed.isEmpty() && st.removed.isEmpty() && git.repository.resolve(Constants.HEAD) != null) {
                log("Нечего коммитить: изменений нет")
                return
            }
            val name = c.name.ifBlank { c.user.ifBlank { "Строка" } }
            val email = c.email.ifBlank { "${c.user.ifBlank { "user" }}@users.noreply.github.com" }
            val rev = git.commit().setMessage(message).setAuthor(name, email).setCommitter(name, email).call()
            log("Коммит ${rev.name.take(7)}: $message")
        }
    }

    fun pull(dir: File, c: Creds) {
        Git.open(dir).use { git ->
            log("Получаю изменения…")
            val branch = git.repository.branch
            val cfg = git.repository.config
            val cmd = git.pull().setCredentialsProvider(provider(c)).setProgressMonitor(monitor)
            if (cfg.getString("branch", branch, "merge") == null) {
                val remote = git.remoteList().call().firstOrNull()?.name ?: throw IllegalStateException("У репозитория нет удалённого адреса (remote).")
                cmd.setRemote(remote).setRemoteBranchName(branch)
            }
            val r = cmd.call()
            if (r.isSuccessful) log("Pull выполнен: ${r.mergeResult?.mergeStatus ?: "ок"}")
            else {
                val conflicts = r.mergeResult?.conflicts?.keys.orEmpty()
                throw IllegalStateException("Pull не удался: ${r.mergeResult?.mergeStatus}" +
                    if (conflicts.isNotEmpty()) ". Конфликты: ${conflicts.joinToString()}. Исправьте файлы и сделайте коммит." else "")
            }
        }
    }

    fun remoteUrl(dir: File): String? = Git.open(dir).use { it.repository.config.getString("remote", "origin", "url") }

    fun currentBranch(dir: File): String = Git.open(dir).use { it.repository.branch }

    fun push(dir: File, c: Creds) {
        Git.open(dir).use { git ->
            val url = git.repository.config.getString("remote", "origin", "url")
                ?: throw IllegalStateException("У репозитория нет удалённого адреса origin.")
            if (url.startsWith("http", true) && c.token.isNullOrBlank()) throw IllegalStateException("Сначала укажите токен GitHub в настройках")
            val branch = git.repository.branch
            log("Отправляю $branch…")
            val results = git.push().setRemote("origin").setRefSpecs(RefSpec("refs/heads/$branch:refs/heads/$branch"))
                .setCredentialsProvider(provider(c)).setProgressMonitor(monitor).call()
            var failed: String? = null
            for (r in results) for (u in r.remoteUpdates) {
                log("${u.remoteName}: ${u.status}${u.message?.let { " — $it" } ?: ""}")
                if (u.status != RemoteRefUpdate.Status.OK && u.status != RemoteRefUpdate.Status.UP_TO_DATE) failed = u.status.toString()
            }
            if (failed != null) throw IllegalStateException(
                if (failed!!.startsWith("REJECTED_NONFASTFORWARD")) "Push отклонён: на сервере есть новые коммиты. Сначала выполните pull." else "Push не выполнен: $failed")
            val cfg = git.repository.config
            if (cfg.getString("branch", branch, "remote") == null) {
                cfg.setString("branch", branch, "remote", "origin")
                cfg.setString("branch", branch, "merge", "refs/heads/$branch")
                cfg.save()
            }
        }
    }

    /** Локальные ветки и ветки origin, которых ещё нет локально. Текущая — первой. */
    fun branches(dir: File): List<String> = Git.open(dir).use { git ->
        val cur = git.repository.branch
        val local = git.branchList().call().map { Repository.shortenRefName(it.name) }
        val remote = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call()
            .map { Repository.shortenRefName(it.name) }.filter { it.startsWith("origin/") && it != "origin/HEAD" }
            .map { it.removePrefix("origin/") }.filter { it !in local }
        listOf(cur) + (local - cur).sorted() + remote.sorted()
    }

    fun createBranch(dir: File, name: String) {
        if (!Regex("[A-Za-z0-9._/-]{1,100}").matches(name) || name.startsWith("-") || name.contains("..") || name.endsWith("/") || name.endsWith(".lock"))
            throw IllegalArgumentException("Недопустимое имя ветки: $name")
        Git.open(dir).use { git ->
            git.checkout().setCreateBranch(true).setName(name).call()
            log("Создана ветка $name и выполнено переключение на неё")
        }
    }

    fun checkout(dir: File, name: String) {
        Git.open(dir).use { git ->
            val st = git.status().call()
            if (st.hasUncommittedChanges()) throw IllegalStateException("Есть незакоммиченные изменения. Сделайте коммит перед сменой ветки.")
            val local = git.repository.findRef("refs/heads/$name") != null
            if (local) git.checkout().setName(name).call()
            else {
                git.checkout().setCreateBranch(true).setName(name).setStartPoint("origin/$name")
                    .setUpstreamMode(org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode.TRACK).call()
            }
            log("Текущая ветка: $name")
        }
    }

}
