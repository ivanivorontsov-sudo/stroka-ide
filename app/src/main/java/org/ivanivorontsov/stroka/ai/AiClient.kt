package org.ivanivorontsov.stroka.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AiException(message: String) : Exception(message)

data class AiMessage(val role: String, val content: String)

object AiClient {
    fun systemPrompt(langName: String, fileName: String): String {
        return """
Ты помогаешь писать и исправлять программы в мобильном редакторе «Строка».
Отвечай по-русски, кратко и по делу. Когда нужно изменить код, верни полный текст файла в одном блоке ``` с языком.
Не добавляй комментарии в код, если пользователь не просил. Не выводи ключи, токены и пароли.

Язык «Понятный»: русские ключевые слова (пусть, константа, если/то/иначе, пока, повторить, для, функция, верни, конец, и, или, не, истина, ложь, ничего, попробуй, брось, выбор, класс, новый).
Строки можно брать в «ёлочки» «». Функции в скомпилированном JS получают префикс $. Стандартные имена: напиши, спроси.
Пиши идиоматичный «Понятный», а не перевод с JavaScript дословно.

Текущий файл: $fileName (язык: $langName).
Если пользователь просит калькулятор, сделай четыре действия и обработай деление на ноль.
""".trimIndent()
    }

    fun chat(baseUrl: String, model: String, apiKey: String, messages: List<AiMessage>): String {
        if (apiKey.isBlank()) throw AiException("Не задан ключ API ИИ. Откройте Настройки и сохраните ключ.")
        val base = baseUrl.trim().ifBlank { "https://api.deepseek.com" }.trimEnd('/')
        val url = if (base.endsWith("/v1") || base.endsWith("/chat/completions")) {
            if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        } else "$base/v1/chat/completions"
        val arr = JSONArray()
        for (m in messages) {
            arr.put(JSONObject().put("role", m.role).put("content", m.content))
        }
        val body = JSONObject()
            .put("model", model.ifBlank { "deepseek-chat" })
            .put("messages", arr)
            .put("temperature", 0.2)
            .toString()
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 30000
            conn.readTimeout = 120000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val snippet = text.take(500)
                throw AiException("Сервер ИИ вернул HTTP $code. $snippet")
            }
            val json = JSONObject(text)
            val content = json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            if (content.isBlank()) throw AiException("Пустой ответ модели.")
            return content
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException("Не удалось обратиться к ИИ: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    fun chatStructured(baseUrl: String, model: String, apiKey: String, messages: List<AiMessage>): String {
        if (apiKey.isBlank()) throw AiException("Не задан ключ API ИИ. Откройте Настройки и сохраните ключ.")
        val schema = JSONObject()
            .put("type", "object")
            .put("additionalProperties", false)
            .put("properties", JSONObject()
                .put("summary", JSONObject().put("type", "string"))
                .put("operations", JSONObject().put("type", "array").put("items",
                    JSONObject().put("type", "object").put("additionalProperties", false)
                        .put("properties", JSONObject()
                            .put("action", JSONObject().put("type", "string").put("enum", JSONArray().put("write").put("delete")))
                            .put("path", JSONObject().put("type", "string"))
                            .put("content", JSONObject().put("type", "string")))
                        .put("required", JSONArray().put("action").put("path").put("content")))))
            .put("required", JSONArray().put("summary").put("operations"))
        return try {
            requestStructured(baseUrl, model, apiKey, messages,
                JSONObject().put("type", "json_schema").put("json_schema",
                    JSONObject().put("name", "stroka_project_patch").put("strict", true).put("schema", schema)))
        } catch (e: AiException) {
            if (!e.message.orEmpty().contains("HTTP 400") && !e.message.orEmpty().contains("response_format")) throw e
            requestStructured(baseUrl, model, apiKey, messages, JSONObject().put("type", "json_object"))
        }
    }

    private fun requestStructured(
        baseUrl: String,
        model: String,
        apiKey: String,
        messages: List<AiMessage>,
        responseFormat: JSONObject
    ): String {
        val base = baseUrl.trim().ifBlank { "https://api.deepseek.com" }.trimEnd('/')
        val url = if (base.endsWith("/v1") || base.endsWith("/chat/completions")) {
            if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        } else "$base/v1/chat/completions"
        val arr = JSONArray()
        messages.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val body = JSONObject().put("model", model.ifBlank { "deepseek-chat" })
            .put("messages", arr).put("temperature", 0.1).put("response_format", responseFormat).toString()
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 30000
            conn.readTimeout = 180000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw AiException("Сервер ИИ вернул HTTP $code. ${text.take(500)}")
            val json = JSONObject(text)
            val choice = json.optJSONArray("choices")?.optJSONObject(0)
            val refusal = choice?.optJSONObject("message")?.optString("refusal").orEmpty()
            if (refusal.isNotBlank()) throw AiException("ИИ отказался выполнять запрос: $refusal")
            val content = choice?.optJSONObject("message")?.optString("content").orEmpty()
            if (content.isBlank()) throw AiException("Пустой JSON-ответ модели.")
            return content
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException("Не удалось обратиться к ИИ: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    fun extractCode(answer: String): String? {
        val fence = Regex("```(?:[A-Za-z0-9_+-]+)?\\s*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
        val m = fence.find(answer)
        return m?.groupValues?.get(1)?.trim('\n')
    }
}
