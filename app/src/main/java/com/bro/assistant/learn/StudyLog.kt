package com.bro.assistant.learn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** What BRO did after each study run, so you can see it in Settings and by asking "what did you learn". */
object StudyLog {
    data class Entry(val time: Long, val topics: List<String>, val why: String)

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("bro_learnlog", Context.MODE_PRIVATE)

    fun add(c: Context, topics: List<String>, why: String) {
        if (topics.isEmpty()) return
        val arr = try { JSONArray(sp(c).getString("log", "[]")) } catch (e: Exception) { JSONArray() }
        arr.put(JSONObject().put("t", System.currentTimeMillis()).put("w", why).put("x", JSONArray(topics)))
        val keep = JSONArray()
        for (i in maxOf(0, arr.length() - 40) until arr.length()) keep.put(arr.get(i))
        sp(c).edit().putString("log", keep.toString()).apply()
    }

    fun recent(c: Context, n: Int = 10): List<Entry> = try {
        val arr = JSONArray(sp(c).getString("log", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val x = o.getJSONArray("x")
            Entry(o.getLong("t"), (0 until x.length()).map { i -> x.getString(i) }, o.optString("w"))
        }.takeLast(n).reversed()
    } catch (e: Exception) { emptyList() }
}
