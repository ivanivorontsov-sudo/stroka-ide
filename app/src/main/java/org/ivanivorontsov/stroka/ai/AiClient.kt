package org.ivanivorontsov.stroka.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AiException(message: String) : Exception(message)

data class AiMessage(val role: String, val content: String)

object AiClient {
    /** Короткая шпаргалка по «Понятному» 2.7 для моделей, которые его не знают. */
    const val PONYATNY_HINT = """Язык «Понятный» 2.7 (файлы .pon): русские ключевые слова, блоки закрываются словом «конец».
пусть x = 5; константа N = 10; пиши "текст", x; пусть имя = спроси("Вопрос?")
если x > 0 то … иначе если x < 0 то … иначе … конец; пока x > 0 … конец; для i от 1 до 10 … конец; для каждого элемент в список … конец
функция имя(а, б) … верни а + б конец; лямбда: функция(а) => а * 2 или а => а * 2; класс Имя … конец; попробуй … если ошибка как е … конец
Строки: "…" или «…», подстановка "Счёт: {x}". Логика: и, или, не, истина, ложь, ничего. Списки [1, 2], словари {"ключ": 1}.
Встроенные: длина, сумма, округли, число, строка, случайное. Графика: окно(заголовок, ш, в), залей(цвет), прямоугольник(x, y, ш, в, цвет), круг(x, y, r, цвет), рисуй_текст(x, y, текст, цвет, размер), каждый_кадр(функция), клавиша("влево"), мышь().
Если есть функции сложить/вычесть/умножить/разделить — Строка сама строит окно калькулятора."""

    fun systemPrompt(langName: String, fileName: String): String =
        "Ты помогаешь писать и исправлять программы в мобильном редакторе «Строка». Отвечай по-русски, кратко и по делу. " +
            "Когда нужно изменить код, верни полный текст файла в одном блоке ``` с языком. Не выводи ключи, токены и пароли.\n" +
            "Текущий файл: $fileName (язык: $langName).\n" + PONYATNY_HINT

    /** Адрес Chat Completions для OpenAI-совместимого API: https://api.deepseek.com, …/v1, …/v1beta/openai, …/compatible-mode/v1. */
    fun endpoint(baseUrl: String): String {
        val base = baseUrl.trim().ifBlank { "https://api.deepseek.com" }.trimEnd('/')
        if (base.endsWith("/chat/completions")) return base
        val path = Regex("^https?://[^/]+(/.*)?$").find(base)?.groupValues?.get(1).orEmpty()
        return if (Regex("/v\\d+[a-z0-9]*(/|$)|/openai$").containsMatchIn(path)) "$base/chat/completions" else "$base/v1/chat/completions"
    }

    fun chat(baseUrl: String, model: String, apiKey: String, messages: List<AiMessage>): String {
        if (apiKey.isBlank()) throw AiException("Не задан ключ API ИИ. Откройте Настройки и сохраните ключ.")
        val url = endpoint(baseUrl)
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
        val url = endpoint(baseUrl)
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
