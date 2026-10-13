package com.bro.assistant.learn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * BRO's memory of answers. Every good answer is kept on the phone, so the same or a very similar question gets an
 * instant reply, and still gets one when there is no internet. Without internet and without an offline brain,
 * BRO falls back to its own study notes.
 */
object AnswerCache {
    private const val MAX = 300
    private val timeSensitive = Regex("\\b(?:now|today|tonight|tomorrow|yesterday|current|latest|news|weather|score|price|rate|time|date|battery|remind|alarm|call|message)\\b", RegexOption.IGNORE_CASE)
    private val stop = setOf("the", "a", "an", "of", "to", "in", "and", "or", "is", "it", "for", "on", "with", "me", "my", "i", "you", "please", "bro", "can", "do", "what", "how", "tell", "give")

    private class Item(val q: String, val a: String, val t: Long)
    private fun file(c: Context) = File(c.applicationContext.filesDir, "answer_cache.json")

    @Synchronized
    private fun load(c: Context): MutableList<Item> = try {
        val f = file(c)
        if (!f.exists()) mutableListOf() else {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { val o = arr.getJSONObject(it); Item(o.getString("q"), o.getString("a"), o.getLong("t")) }.toMutableList()
        }
    } catch (e: Exception) { mutableListOf() }

    private fun words(s: String) = s.lowercase().split(Regex("[^a-z0-9+#\\u0C00-\\u0C7F]+")).filter { it.length >= 2 && it !in stop }.toSet()

    fun online(c: Context): Boolean = try {
        val cm = c.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (e: Exception) { true }

    @Synchronized
    fun put(c: Context, question: String, answer: String) {
        val q = question.trim(); val a = answer.trim()
        if (q.length < 6 || a.length < 15 || a.length > 6000 || timeSensitive.containsMatchIn(q)) return
        val list = load(c)
        val key = words(q)
        list.removeAll { words(it.q) == key }
        list.add(Item(q, a, System.currentTimeMillis()))
        val arr = JSONArray()
        list.takeLast(MAX).forEach { arr.put(JSONObject().put("q", it.q).put("a", it.a).put("t", it.t)) }
        file(c).writeText(arr.toString())
    }

    /** Best earlier answer when the question is similar enough (0..1 word overlap), else null. */
    fun find(c: Context, question: String, minScore: Double): String? {
        if (timeSensitive.containsMatchIn(question)) return null
        val q = words(question)
        if (q.isEmpty()) return null
        var best: Item? = null; var bestScore = 0.0
        for (it in load(c)) {
            val w = words(it.q)
            val inter = q.intersect(w).size.toDouble()
            val score = inter / (q.size + w.size - inter)
            if (score > bestScore) { bestScore = score; best = it }
        }
        return if (best != null && bestScore >= minScore) best.a else null
    }

    /** Last resort with no internet and no offline brain: the study note that fits best. */
    fun fromNotes(c: Context, question: String): String? {
        val q = words(question)
        val best = LearnStore.all(c).filter { it.kind == "CODE" }
            .map { n -> n to words(n.topic + " " + n.text).count { it in q } }
            .filter { it.second >= 2 }.maxByOrNull { it.second } ?: return null
        return "(No internet. From my study notes on ${best.first.topic})\n" + best.first.text
    }

    fun size(c: Context) = load(c).size
    fun clear(c: Context) { file(c).delete() }
}
