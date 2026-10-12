package com.bro.assistant.memory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Things the owner asked BRO to remember. Kept only on this phone; the owner can list and delete them. */
object MemoryStore {
    data class Fact(val id: Long, val text: String)

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("bro_memory", Context.MODE_PRIVATE)

    fun all(ctx: Context): List<Fact> = try {
        val arr = JSONArray(sp(ctx).getString("facts", "[]"))
        (0 until arr.length()).map { val o = arr.getJSONObject(it); Fact(o.getLong("id"), o.getString("text")) }
    } catch (e: Exception) { emptyList() }

    private fun save(ctx: Context, list: List<Fact>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("text", it.text)) }
        sp(ctx).edit().putString("facts", arr.toString()).apply()
    }

    fun add(ctx: Context, text: String): Fact {
        val clean = text.trim().trimEnd('.', '!').take(300)
        val list = all(ctx).toMutableList()
        list.firstOrNull { it.text.equals(clean, true) }?.let { return it }
        val f = Fact(System.currentTimeMillis(), clean)
        list.add(f)
        save(ctx, list.takeLast(200))
        return f
    }

    fun remove(ctx: Context, id: Long) = save(ctx, all(ctx).filter { it.id != id })
    fun clear(ctx: Context) = save(ctx, emptyList())

    /** Text added to the AI's prompt so it can use what it was told. */
    fun promptText(ctx: Context, maxChars: Int): String {
        val facts = all(ctx)
        if (facts.isEmpty()) return ""
        val sb = StringBuilder("Facts the owner asked you to remember (use them when relevant):\n")
        for (f in facts.asReversed()) {
            if (sb.length + f.text.length > maxChars) break
            sb.append("- ").append(f.text).append('\n')
        }
        return sb.append('\n').toString()
    }
}
