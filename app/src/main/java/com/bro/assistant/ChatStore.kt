package com.bro.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class StoredMessage(val role: String, val text: String, val time: Long = System.currentTimeMillis())

/** Saves the chat to the phone so it survives closing the app. No new Gradle dependency needed. */
class ChatStore(context: Context) {
    private val prefs = context.getSharedPreferences("bro_chat", Context.MODE_PRIVATE)

    fun load(): MutableList<StoredMessage> {
        val raw = prefs.getString("messages", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableListOf<StoredMessage>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(StoredMessage(o.getString("role"), o.getString("text"), o.optLong("time")))
        }
        return out
    }

    fun save(messages: List<StoredMessage>) {
        val arr = JSONArray()
        messages.takeLast(500).forEach {
            arr.put(JSONObject().put("role", it.role).put("text", it.text).put("time", it.time))
        }
        prefs.edit().putString("messages", arr.toString()).apply()
    }

    fun clear() = prefs.edit().remove("messages").apply()
}
