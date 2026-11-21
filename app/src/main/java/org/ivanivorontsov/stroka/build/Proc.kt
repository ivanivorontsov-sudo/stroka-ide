
package org.ivanivorontsov.stroka.build

import java.io.File
import java.util.concurrent.TimeUnit

class ProcResult(val code: Int, val out: String) {
    val ok: Boolean get() = code == 0
}

object Proc {
    fun run(cmd: List<String>, cwd: File, env: Map<String, String> = emptyMap(), timeoutSec: Long = 180): ProcResult {
        val pb = ProcessBuilder(cmd).directory(cwd).redirectErrorStream(true)
        val e = pb.environment()
        env.forEach { (k, v) -> e[k] = v }
        val p = try {
            pb.start()
        } catch (ex: Exception) {
            return ProcResult(127, "Не удалось запустить «${cmd.first()}»: ${ex.message}")
        }
        val out = p.inputStream.bufferedReader(Charsets.UTF_8).readText()
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            return ProcResult(124, "Команда превысила $timeoutSec с: ${cmd.joinToString(" ")}\n$out")
        }
        return ProcResult(p.exitValue(), out)
    }

    fun require(cmd: List<String>, cwd: File, env: Map<String, String> = emptyMap(), timeoutSec: Long = 180, err: String): String {
        val r = run(cmd, cwd, env, timeoutSec)
        if (!r.ok) {
            val tail = r.out.trim().ifBlank { "(нет вывода)" }
            throw BuildException("$err\nКоманда: ${cmd.joinToString(" ")}\nКод ${r.code}\n$tail")
        }
        return r.out
    }
}

class BuildException(message: String) : Exception(message)
