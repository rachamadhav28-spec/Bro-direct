package com.bro.assistant.learn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** What BRO has studied: short notes kept on the phone and shown to the AI before it answers (not model training). */
object LearnStore {
    data class Note(val id: Long, val kind: String, val topic: String, val text: String, val time: Long)

    private const val MAX_NOTES = 120
    private fun file(c: Context) = File(c.applicationContext.filesDir, "learned_notes.json")

    @Synchronized
    fun all(c: Context): List<Note> = try {
        val f = file(c)
        if (!f.exists()) emptyList() else {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Note(o.getLong("id"), o.getString("kind"), o.getString("topic"), o.getString("text"), o.getLong("time"))
            }
        }
    } catch (e: Exception) { emptyList() }

    @Synchronized
    private fun save(c: Context, list: List<Note>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("kind", it.kind).put("topic", it.topic).put("text", it.text).put("time", it.time)) }
        file(c).writeText(arr.toString())
    }

    /** Adds a note, replacing an older note on the same topic. */
    fun put(c: Context, kind: String, topic: String, text: String) {
        val clean = text.trim().take(1400)
        if (clean.length < 40) return
        val kept = all(c).filter { !(it.kind == kind && it.topic.equals(topic, true)) }
        save(c, (kept + Note(System.currentTimeMillis(), kind, topic.take(80), clean, System.currentTimeMillis())).takeLast(MAX_NOTES))
    }

    fun clear(c: Context) { file(c).delete() }
    fun count(c: Context) = all(c).size
    fun hasTopic(c: Context, topic: String, withinDays: Int): Boolean {
        val limit = System.currentTimeMillis() - withinDays * 86_400_000L
        return all(c).any { it.topic.equals(topic, true) && it.time >= limit }
    }

    private val stop = setOf("the", "a", "an", "of", "to", "in", "and", "or", "is", "it", "for", "on", "with", "how", "what", "write", "make", "create", "me", "my", "i", "you", "please", "code", "program")
    private fun words(s: String) = s.lowercase().split(Regex("[^a-z0-9+#]+")).filter { it.length >= 2 && it !in stop }.toSet()

    /** Notes worth showing for this question, as text for the AI's prompt (empty when nothing fits). */
    fun promptText(c: Context, query: String, maxChars: Int): String {
        val notes = all(c)
        if (notes.isEmpty() || maxChars < 200) return ""
        val q = words(query)
        val sb = StringBuilder()
        // how to talk with the owner (language notes) always come first, but short
        notes.filter { it.kind == "STYLE" || it.kind == "LANG" }.takeLast(2).forEach { n ->
            if (sb.length + n.text.length < maxChars / 2) sb.append("- ").append(n.text.replace('\n', ' ').take(500)).append('\n')
        }
        val scored = notes.filter { it.kind == "CODE" }
            .map { n -> n to (words(n.topic + " " + n.text).count { it in q }) }
            .filter { it.second > 0 }.sortedByDescending { it.second }
        for ((n, _) in scored) {
            if (sb.length + n.text.length > maxChars) continue
            sb.append("- ").append(n.topic).append(": ").append(n.text).append('\n')
        }
        return if (sb.isEmpty()) "" else "Study notes BRO wrote earlier (may be imperfect: prefer correct knowledge if they disagree):\n$sb\n"
    }
}
