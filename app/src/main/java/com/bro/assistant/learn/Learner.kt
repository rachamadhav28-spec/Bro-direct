package com.bro.assistant.learn

import android.content.Context
import com.bro.assistant.ChatStore
import com.bro.assistant.ErrorHandler
import com.bro.assistant.ai.GeminiClient
import com.bro.assistant.ai.Skill
import com.bro.assistant.memory.PreferencesStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * BRO's "learning". It cannot retrain a model on a phone, so it studies: it asks the AI (your API keys, or the
 * offline brain) for short expert notes on coding topics and on how to talk with you in Telugu and Tenglish,
 * keeps them, and shows the relevant ones to the AI before every answer.
 */
object Learner {
    val codeTopics = listOf(
        "Kotlin coroutines and Flow", "Jetpack Compose state and recomposition", "Android services, permissions and lifecycle",
        "Python idioms, data structures and common libraries", "JavaScript async/await, promises and the DOM",
        "HTML and responsive CSS layouts", "SQL joins, indexes and query design", "Git branching and GitHub Actions",
        "REST API design, HTTP errors and retries", "Sorting, searching and Big-O", "Dynamic programming patterns",
        "Graph algorithms: BFS, DFS, Dijkstra", "Object-oriented design patterns", "Reading stack traces and debugging method",
        "Unit testing and test design", "Clean code and refactoring", "Java collections and streams", "C++ STL containers and pointers",
        "Security basics: input validation, secrets, injection", "Performance profiling and optimisation", "Regular expressions", "Recursion, trees and linked lists",
        "UI design: hierarchy, spacing, typography and colour contrast", "UX: user flows, empty/error states, accessibility and microcopy", "Mobile UI patterns for Android Material 3"
    )

    /** True while some topic has not been studied in the last 45 days. */
    fun hasFresh(c: Context): Boolean = codeTopics.any { !LearnStore.hasTopic(c, it, 45) }

    private const val LESSON_SYSTEM =
        "You write a private study note that a coding assistant reads before answering. Be exact and compact: at most 170 words. " +
            "Include the key ideas, ONE short correct code snippet in a fenced block, and 2 common mistakes. No greeting, no intro."

    private const val LANG_SYSTEM =
        "You help a voice assistant talk with a young Telugu-speaking programmer from Telangana. Write a compact guide (max 170 words): " +
            "1) how to answer in natural Tenglish (Telugu in English letters mixed with English tech words) with 10 short example replies; " +
            "2) 15 common Telugu words/phrases a user may say to a phone assistant (in English letters) with meanings. No intro."

    /** A style note built on the phone from the owner's own messages (nothing is sent anywhere for this). */
    private fun styleNote(c: Context): String? {
        val msgs = try { ChatStore(c).all().flatMap { it.messages }.filter { it.role == "user" }.map { it.text } } catch (e: Exception) { emptyList() }
        if (msgs.size < 8) return null
        val counts = HashMap<String, Int>()
        for (m in msgs.takeLast(300)) for (w in m.lowercase().split(Regex("[^a-z\\u0C00-\\u0C7F']+"))) if (w.length in 3..12) counts[w] = (counts[w] ?: 0) + 1
        val top = counts.entries.filter { it.value >= 3 }.sortedByDescending { it.value }.take(25).joinToString(", ") { it.key }
        val tenglish = msgs.takeLast(100).count { Regex("\\b(?:ela|enti|cheyyi|chey|ledu|undi|ra|bro|anna|ippudu|kadha|nuvvu|nenu|cheppu|ivvu)\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) || it.any { ch -> ch in '\u0C00'..'\u0C7F' } }
        return "The owner (R. Madhav) usually writes short messages. Words he uses often: $top. " +
            (if (tenglish >= 5) "He mixes Telugu and English, so answer in friendly Tenglish when he does. " else "He mostly writes English. ") +
            "Keep answers short and direct."
    }

    /**
     * One study session. Returns the titles learned. [maxLessons] lessons, stopping after [budgetMs].
     * [topic] = a specific topic (any text); null = the next topics of the curriculum.
     */
    suspend fun session(
        c: Context, prefs: PreferencesStore, maxLessons: Int, budgetMs: Long, topic: String? = null,
        onStep: (String) -> Unit = {}
    ): List<String> {
        val app = c.applicationContext
        val ai = GeminiClient(prefs)
        val started = System.currentTimeMillis()
        val learned = ArrayList<String>()

        // 1) the owner's way of talking (local only, instant)
        styleNote(app)?.let { LearnStore.put(app, "STYLE", "owner style", it); if (topic == null) learned.add("how you talk (from your own messages)") }

        // 2) the topics to study
        val wanted: List<Pair<String, String>> = when {
            topic != null && Regex("tenglish|telugu|తెలుగు|language|talk", RegexOption.IGNORE_CASE).containsMatchIn(topic) -> listOf("LANG" to "Tenglish and Telugu")
            topic != null -> listOf("CODE" to topic.trim().take(60))
            else -> {
                val list = ArrayList<Pair<String, String>>()
                if (!LearnStore.hasTopic(app, "Tenglish and Telugu", 14)) list.add("LANG" to "Tenglish and Telugu")
                val fresh = codeTopics.filter { !LearnStore.hasTopic(app, it, 45) }.ifEmpty { codeTopics.shuffled() }
                fresh.forEach { list.add("CODE" to it) }
                list
            }
        }

        for ((kind, name) in wanted) {
            if (learned.size >= maxLessons || System.currentTimeMillis() - started > budgetMs) break
            currentCoroutineContext().ensureActive()
            onStep("Studying: $name")
            try {
                val text = if (kind == "LANG") ai.generate(LANG_SYSTEM, "Write the guide.", json = false, timeoutMs = 90000, skill = Skill.GENERAL)
                else ai.generate(LESSON_SYSTEM, "Topic: $name", json = false, timeoutMs = 90000, skill = Skill.CODE)
                LearnStore.put(app, kind, name, text)
                learned.add(name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ErrorHandler.log("Learner", "could not study $name", e)
                break   // no key / no internet / quota: stop the session quietly
            }
        }
        prefs.lastLearned = System.currentTimeMillis()
        return learned
    }
}
