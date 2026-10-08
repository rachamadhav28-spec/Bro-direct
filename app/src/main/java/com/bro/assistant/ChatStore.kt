package com.bro.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class StoredMessage(val role: String, val text: String, val time: Long = System.currentTimeMillis())

data class Conversation(
    val id: Long,
    val title: String,
    val updated: Long,
    val messages: List<StoredMessage>
)

/** Saves every conversation on the phone so chats and history survive closing the app. */
class ChatStore(context: Context) {
    private val prefs = context.getSharedPreferences("bro_chat_v2", Context.MODE_PRIVATE)

    var currentId: Long
        get() = prefs.getLong("current", 0L)
        set(v) { prefs.edit().putLong("current", v).apply() }

    fun all(): MutableList<Conversation> {
        val arr = JSONArray(prefs.getString("conversations", "[]") ?: "[]")
        val out = mutableListOf<Conversation>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ms = o.getJSONArray("messages")
            val list = (0 until ms.length()).map {
                val m = ms.getJSONObject(it)
                StoredMessage(m.getString("role"), m.getString("text"), m.optLong("time"))
            }
            out.add(Conversation(o.getLong("id"), o.getString("title"), o.optLong("updated"), list))
        }
        return out
    }

    fun get(id: Long): Conversation? = all().firstOrNull { it.id == id }

    fun put(c: Conversation) {
        val list = all().filter { it.id != c.id }.toMutableList()
        list.add(c)
        write(list.sortedByDescending { it.updated }.take(100))
    }

    fun delete(id: Long) = write(all().filter { it.id != id })

    fun clearAll() = prefs.edit().remove("conversations").remove("current").apply()

    private fun write(list: List<Conversation>) {
        val arr = JSONArray()
        list.forEach { c ->
            val ms = JSONArray()
            c.messages.takeLast(500).forEach {
                ms.put(JSONObject().put("role", it.role).put("text", it.text).put("time", it.time))
            }
            arr.put(
                JSONObject().put("id", c.id).put("title", c.title)
                    .put("updated", c.updated).put("messages", ms)
            )
        }
        prefs.edit().putString("conversations", arr.toString()).apply()
    }
}
