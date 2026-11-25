package org.ivanivorontsov.stroka.ai

import org.ivanivorontsov.stroka.MockHttp
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** OpenAI-совместимый API на мок-сервере: без настоящих ключей и аккаунтов. */
class AiClientTest {
    private val requests = ArrayList<Pair<String, JSONObject>>()
    private var rejectSchema = false
    private val server = MockHttp { ex ->
        val body = JSONObject(ex.body)
        requests += ex.path to body
        val auth = ex.header("Authorization")
        val fmt = body.optJSONObject("response_format")?.optString("type")
        when {
            auth != "Bearer sk-test" -> 401 to """{"error":{"message":"Invalid API key"}}"""
            fmt == "json_schema" && rejectSchema -> 400 to """{"error":{"message":"response_format json_schema is not supported"}}"""
            fmt != null -> 200 to JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content",
                """{"summary":"Добавлен калькулятор","operations":[{"action":"write","path":"калькулятор.pon","content":"функция сложить(а, б)\n    верни а + б\nконец\n"}]}""")))).toString()
            else -> 200 to """{"choices":[{"message":{"role":"assistant","content":"Привет! ```pon\nпиши 1\n```"}}]}"""
        }
    }

    @After fun stop() = server.close()
    private val base get() = server.base

    @Test
    fun endpoints() {
        assertEquals("https://api.deepseek.com/v1/chat/completions", AiClient.endpoint(""))
        assertEquals("https://api.deepseek.com/v1/chat/completions", AiClient.endpoint("https://api.deepseek.com/"))
        assertEquals("https://api.openai.com/v1/chat/completions", AiClient.endpoint("https://api.openai.com/v1"))
        assertEquals("https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions", AiClient.endpoint("https://dashscope-intl.aliyuncs.com/compatible-mode/v1"))
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", AiClient.endpoint("https://generativelanguage.googleapis.com/v1beta/openai/"))
        assertEquals("https://openrouter.ai/api/v1/chat/completions", AiClient.endpoint("https://openrouter.ai/api/v1"))
        assertEquals("http://localhost:11434/v1/chat/completions", AiClient.endpoint("http://localhost:11434"))
        assertEquals("https://x.y/custom/chat/completions", AiClient.endpoint("https://x.y/custom/chat/completions"))
    }

    @Test
    fun chatAndStructuredWithFallback() {
        val answer = AiClient.chat(base, "qwen-plus", "sk-test", listOf(AiMessage("user", "привет")))
        assertTrue(answer.startsWith("Привет"))
        assertEquals("пиши 1", AiClient.extractCode(answer))
        assertEquals("/v1/chat/completions", requests.last().first)
        assertEquals("qwen-plus", requests.last().second.getString("model"))

        rejectSchema = true
        val raw = AiClient.chatStructured(base, "", "sk-test", listOf(AiMessage("user", "сделай калькулятор")))
        assertEquals("json_object", requests.last().second.getJSONObject("response_format").getString("type"))
        assertEquals("deepseek-chat", requests.last().second.getString("model"))
        val plan = AiPatchEngine.parse(raw)
        assertEquals(1, plan.operations.size)
        assertEquals("калькулятор.pon", plan.operations[0].path)

        try { AiClient.chat(base, "m", "sk-wrong", listOf(AiMessage("user", "x"))); fail() } catch (e: AiException) { assertTrue(e.message, e.message!!.contains("401")) }
        try { AiClient.chat(base, "m", "", listOf(AiMessage("user", "x"))); fail() } catch (e: AiException) { assertTrue(e.message!!.contains("ключ")) }
        try { AiClient.chat("http://127.0.0.1:1", "m", "k", listOf(AiMessage("user", "x"))); fail() } catch (e: AiException) { assertTrue(e.message!!.contains("Не удалось")) }
    }

    @Test
    fun plainTextAnswerIsKept() {
        val plan = AiPatchEngine.parseOrText("Здравствуйте! Чем помочь с кодом?")
        assertEquals("Здравствуйте! Чем помочь с кодом?", plan.summary)
        assertTrue(plan.operations.isEmpty())
        val web = AiPatchEngine.parseOrText("json\nCopy\n{\"summary\":\"ok\",\"operations\":[]}")
        assertEquals("ok", web.summary)
        try { AiPatchEngine.parseOrText("""{"summary":"x","operations":[{"action":"write","path":"../etc/passwd","content":"x"}]}"""); fail() } catch (_: AiException) {}
    }
}
