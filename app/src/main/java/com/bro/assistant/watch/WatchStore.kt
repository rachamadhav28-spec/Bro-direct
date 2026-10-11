package com.bro.assistant.watch

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A job BRO repeats on its own, such as "check Claude every hour and retry when the limit resets". */
data class Watch(
    val id: String,
    val kind: String,          // "claude" or "generic"
    val goal: String,
    val intervalMin: Int,
    var nextRun: Long,
    var runs: Int = 0,
    val maxRuns: Int = 48,
    var pending: Boolean = false   // a check was due while the phone was locked
)

object WatchStore {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("bro_watch", Context.MODE_PRIVATE)

    @Synchronized fun all(c: Context): List<Watch> {
        val arr = try { JSONArray(sp(c).getString("list", "[]")) } catch (e: Exception) { JSONArray() }
        return (0 until arr.length()).mapNotNull {
            try {
                val o = arr.getJSONObject(it)
                Watch(o.getString("id"), o.getString("kind"), o.getString("goal"), o.getInt("interval"),
                    o.getLong("next"), o.optInt("runs", 0), o.optInt("max", 48), o.optBoolean("pending", false))
            } catch (e: Exception) { null }
        }
    }

    @Synchronized private fun write(c: Context, list: List<Watch>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("kind", it.kind).put("goal", it.goal)
                .put("interval", it.intervalMin).put("next", it.nextRun).put("runs", it.runs)
                .put("max", it.maxRuns).put("pending", it.pending))
        }
        sp(c).edit().putString("list", arr.toString()).apply()
    }

    fun put(c: Context, w: Watch) = write(c, all(c).filter { it.id != w.id } + w)
    fun remove(c: Context, id: String) = write(c, all(c).filter { it.id != id })
    fun clear(c: Context) = write(c, emptyList())
}
