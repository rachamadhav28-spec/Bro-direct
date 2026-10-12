package com.bro.assistant.memory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A short list of tasks BRO finished, so the morning briefing can say what happened while the owner was away. */
object TaskLog {
    data class Entry(val time: Long, val command: String, val result: String, val away: Boolean)

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("bro_tasklog", Context.MODE_PRIVATE)

    fun all(ctx: Context): List<Entry> = try {
        val arr = JSONArray(sp(ctx).getString("log", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Entry(o.getLong("t"), o.getString("c"), o.getString("r"), o.optBoolean("a"))
        }
    } catch (e: Exception) { emptyList() }

    fun add(ctx: Context, command: String, result: String, away: Boolean) {
        val list = (all(ctx) + Entry(System.currentTimeMillis(), command.take(80), result.take(140), away)).takeLast(40)
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.time).put("c", it.command).put("r", it.result).put("a", it.away)) }
        sp(ctx).edit().putString("log", arr.toString()).apply()
    }

    fun lastBriefing(ctx: Context): Long = sp(ctx).getLong("last_briefing", 0L)
    fun markBriefing(ctx: Context) = sp(ctx).edit().putLong("last_briefing", System.currentTimeMillis()).apply()
}
