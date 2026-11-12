package com.alic.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Chat history stored privately on this device only. */
class HistoryRepository(private val ctx: Context) {
    private fun file(owner: String) = File(ctx.filesDir, "history_${owner.filter { it.isLetterOrDigit() }}.json")

    fun loadLocal(owner: String): List<Conversation> = try {
        val f = file(owner)
        if (!f.exists()) emptyList() else {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { toConv(arr.getJSONObject(it)) }
        }
    } catch (e: Exception) { emptyList() }

    fun saveLocal(owner: String, list: List<Conversation>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        file(owner).writeText(arr.toString())
    }

    private fun toJson(c: Conversation) = JSONObject().put("id", c.id).put("title", c.title).put("createdAt", c.createdAt)
        .put("messages", JSONArray().also { a ->
            c.messages.forEach { a.put(JSONObject().put("id", it.id).put("role", it.role).put("text", it.text).put("ts", it.ts)) }
        })

    private fun toConv(o: JSONObject): Conversation {
        val ms = o.optJSONArray("messages") ?: JSONArray()
        return Conversation(
            o.getString("id"), o.optString("title", "Chat"), o.optLong("createdAt"),
            (0 until ms.length()).map {
                val m = ms.getJSONObject(it)
                ChatMessage(m.getString("id"), m.getString("role"), m.getString("text"), m.optLong("ts"))
            }
        )
    }
}
