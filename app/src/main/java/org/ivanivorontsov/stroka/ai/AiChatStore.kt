package org.ivanivorontsov.stroka.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class AiChatThread(
    val id: String,
    var providerId: String,
    var title: String,
    var url: String,
    val messages: MutableList<AiMessage> = mutableListOf(),
    var updatedAt: Long = System.currentTimeMillis()
)

class AiChatStore(context: Context) {
    private val file = File(context.filesDir, "ai-chat-threads.json")

    @Synchronized fun load(): MutableList<AiChatThread> {
        if (!file.isFile) return mutableListOf()
        return try {
            val arr = JSONArray(file.readText())
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val msgs = o.optJSONArray("messages") ?: JSONArray()
                val history = MutableList(msgs.length()) { n ->
                    val m = msgs.getJSONObject(n)
                    AiMessage(m.optString("role"), m.optString("content"))
                }
                AiChatThread(o.optString("id"), o.optString("provider"), o.optString("title"),
                    o.optString("url"), history, o.optLong("updatedAt", 0L))
            }
        } catch (_: Exception) { mutableListOf() }
    }

    @Synchronized fun save(threads: List<AiChatThread>) {
        val arr = JSONArray()
        threads.sortedByDescending { it.updatedAt }.take(100).forEach { thread ->
            val msgs = JSONArray()
            thread.messages.takeLast(80).forEach { m ->
                msgs.put(JSONObject().put("role", m.role).put("content", m.content))
            }
            arr.put(JSONObject().put("id", thread.id).put("provider", thread.providerId)
                .put("title", thread.title.take(100)).put("url", thread.url.take(2000))
                .put("updatedAt", thread.updatedAt).put("messages", msgs))
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(arr.toString(), Charsets.UTF_8)
        if (file.exists()) file.delete()
        if (!tmp.renameTo(file)) tmp.copyTo(file, overwrite = true).also { tmp.delete() }
    }

    fun create(providerId: String, url: String): AiChatThread =
        AiChatThread(UUID.randomUUID().toString(), providerId, "Новый чат", url)
}
