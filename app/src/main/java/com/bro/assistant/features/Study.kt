package com.bro.assistant.features

import android.content.Context
import com.bro.assistant.ai.GeminiClient
import com.bro.assistant.memory.PreferencesStore
import org.json.JSONArray
import org.json.JSONObject

/** "Quiz me on DBMS": one question at a time, graded by the AI. Wrong answers are saved and asked again later. */
object Study {
    private val I = RegexOption.IGNORE_CASE
    private val startRx = Regex("""^(?:quiz\s+me|test\s+me|start\s+(?:a\s+)?quiz|ask\s+me\s+(?:some\s+)?questions)(?:\s+(?:on|about|in))?\s*(.{0,60}?)[.!\s]*$""", I)
    private val stopRx = Regex("""^(?:stop|end|exit|quit|finish)(?:\s+the)?(?:\s+quiz)?[.!\s]*$|^(?:that'?s\s+all|enough|no\s+more)[.!\s]*$""", I)
    private val skipRx = Regex("""^(?:skip|next|pass|i\s+don'?t\s+know|no\s+idea|don'?t\s+know)[.!\s]*$""", I)
    private val weakRx = Regex("""weak|mistake|wrong|revise|revision""", I)

    private class Session(val topic: String, val weak: Boolean) {
        var q = ""; var a = ""
        var asked = 0; var right = 0
        val seen = ArrayList<String>()
        val newWrong = ArrayList<Pair<String, String>>()
        val queue = ArrayDeque<Triple<String, String, String>>()
    }
    @Volatile private var session: Session? = null

    val active: Boolean get() = session != null

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("bro_study", Context.MODE_PRIVATE)
    private fun weakList(c: Context): List<Triple<String, String, String>> = try {
        val a = JSONArray(sp(c).getString("weak", "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it); Triple(o.getString("topic"), o.getString("q"), o.getString("a")) }
    } catch (e: Exception) { emptyList() }
    private fun saveWeak(c: Context, l: List<Triple<String, String, String>>) {
        val a = JSONArray(); l.takeLast(60).forEach { a.put(JSONObject().put("topic", it.first).put("q", it.second).put("a", it.third)) }
        sp(c).edit().putString("weak", a.toString()).apply()
    }

    private fun json(raw: String): JSONObject? = try {
        JSONObject(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1))
    } catch (e: Exception) { null }

    private suspend fun nextQuestion(c: Context, prefs: PreferencesStore, s: Session): String? {
        s.queue.removeFirstOrNull()?.let { s.q = it.second; s.a = it.third; s.asked++; s.seen.add(it.second); return s.q }
        if (s.weak) return null
        val ai = GeminiClient(prefs)
        val raw = ai.generate(
            "You are a friendly exam tutor for an Indian B.Tech student. Reply ONLY with JSON: {\"q\":\"one short question\",\"a\":\"the correct answer in one or two sentences\"}. " +
                "Questions must be answerable by voice in a sentence or two, and not repeat earlier questions.",
            "Topic: ${s.topic}\nEarlier questions: ${s.seen.takeLast(8).joinToString(" | ").ifBlank { "none" }}"
        )
        val o = json(raw) ?: return null
        s.q = o.optString("q"); s.a = o.optString("a")
        if (s.q.isBlank()) return null
        s.asked++; s.seen.add(s.q)
        return s.q
    }

    suspend fun handle(c: Context, prefs: PreferencesStore, raw: String): String? {
        val t = raw.trim()
        val s0 = session
        if (s0 == null) {
            val m = startRx.find(t) ?: return null
            val topic = m.groupValues[1].trim()
            val weak = topic.isNotEmpty() && weakRx.containsMatchIn(topic)
            val s = Session(if (weak) "my weak points" else topic.ifBlank { "general engineering basics" }, weak)
            if (weak) {
                val l = weakList(c)
                if (l.isEmpty()) return "You have no saved mistakes yet. Say \"quiz me on <topic>\" first."
                l.shuffled().take(10).forEach { s.queue.addLast(it) }
            }
            session = s
            val q = try { nextQuestion(c, prefs, s) } catch (e: Exception) { null }
            if (q == null) { session = null; return "I couldn't make a question just now. Check your internet or the offline brain and try again." }
            return "Quiz on ${s.topic}. Say \"stop quiz\" any time.\n\nQuestion 1: $q"
        }
        val s = s0
        if (stopRx.matches(t)) { session = null; return summary(c, s) }

        // grade the answer
        var correct = false
        var feedback = "The answer is: ${s.a}"
        if (!skipRx.matches(t)) {
            try {
                val g = json(GeminiClient(prefs).generate(
                    "You grade a student's spoken answer. Be fair: accept correct meaning even if wording differs. Reply ONLY with JSON: {\"correct\":true or false,\"feedback\":\"one short sentence\"}.",
                    "Question: ${s.q}\nCorrect answer: ${s.a}\nStudent answer: $t"
                ))
                if (g != null) { correct = g.optBoolean("correct"); feedback = g.optString("feedback").ifBlank { feedback } }
            } catch (e: Exception) { /* fall through: treated as wrong, answer shown */ }
        }
        if (correct) s.right++ else {
            s.newWrong.add(s.q to s.a)
            saveWeak(c, weakList(c).filter { it.second != s.q } + Triple(s.topic, s.q, s.a))
        } 
        if (correct && s.weak) saveWeak(c, weakList(c).filter { it.second != s.q })
        val head = (if (correct) "Correct! " else "Not quite. ") + feedback

        val next = try { nextQuestion(c, prefs, s) } catch (e: Exception) { null }
        if (next == null) { session = null; return head + "\n\n" + summary(c, s) }
        return head + "\n\nQuestion ${s.asked}: $next"
    }

    private fun summary(c: Context, s: Session): String {
        val base = "Quiz finished. You got ${s.right} right out of ${s.right + s.newWrong.size}."
        return if (s.newWrong.isEmpty()) "$base Great work, boss!"
        else "$base I saved ${s.newWrong.size} to revise. Say \"quiz me on my weak points\" later."
    }
}
