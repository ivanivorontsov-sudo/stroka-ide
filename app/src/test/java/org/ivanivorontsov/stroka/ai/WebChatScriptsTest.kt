package org.ivanivorontsov.stroka.ai

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Скрипты веб-чатов выгружаются в app/build/webchat-scripts; tools/ci/webchat-check.js
 * гоняет их в настоящем Chrome на мок-страницах в стиле DeepSeek, Qwen и ChatGPT.
 */
class WebChatScriptsTest {
    @Test
    fun dumpScripts() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
        val out = File(root, "app/build/webchat-scripts").apply { mkdirs() }
        val prompt = "Сделай калькулятор. Верни JSON {\"summary\":…}"
        File(out, "prompt.txt").writeText(prompt)
        for (p in listOf("deepseek", "qwen", "chatgpt", "duck")) {
            File(out, "inject-$p.js").writeText(AiWebChat.injectScript(prompt, p))
            File(out, "poll-$p.js").writeText(AiWebChat.pollScript(p))
        }
        File(out, "send.js").writeText(AiWebChat.sendScript())
        File(out, "inspect.js").writeText(AiWebChat.inspectScript())
        File(out, "candidates.js").writeText(AiWebChat.composerCandidatesScript())
        assertTrue(AiWebChat.providers.map { it.id }.containsAll(listOf("deepseek", "qwen", "duck")))
        assertTrue(AiWebChat.isLoginState("\"NO_COMPOSER\""))
    }
}
