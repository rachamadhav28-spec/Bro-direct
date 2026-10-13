package com.bro.assistant.learn

import android.content.Context
import com.bro.assistant.BroTaskService
import com.bro.assistant.memory.PreferencesStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "learn coding", "learn <topic>", "what did you learn", "forget your lessons". */
object LearnCommands {
    private val I = RegexOption.IGNORE_CASE
    private val coding = Regex("^(?:please\\s+)?(?:go\\s+)?(?:learn|study|practi[cs]e)(?:\\s+(?:about|more))?\\s*(?:coding|code|programming|by\\s*yourself)?[.!\\s]*$", I)
    private val topic = Regex("^(?:please\\s+)?(?:learn|study)\\s+(?:about\\s+|how\\s+to\\s+)?(.{3,60}?)[.!\\s]*$", I)
    private val showRx = Regex("^(?:what\\s+(?:have\\s+you|did\\s+you)\\s+learn(?:ed|t)|show\\s+(?:your\\s+)?(?:lessons|notes|learning)|what\\s+do\\s+you\\s+know\\s+about\\s+coding)[?.!\\s]*$", I)
    private val forgetRx = Regex("^(?:forget|delete|clear)\\s+(?:all\\s+)?(?:your\\s+)?(?:lessons|study\\s+notes|what\\s+you\\s+learn(?:ed|t))[.!\\s]*$", I)

    private val startNon = Regex("^(?:start|begin|keep)\\s+(?:studying|learning)(?:\\s+(?:non[- ]?stop|24/7|all\\s+the\\s+time|always))?[.!\\s]*$|^study\\s+(?:non[- ]?stop|24/7)[.!\\s]*$", I)
    private val stopNon = Regex("^(?:stop|end|quit|cancel)\\s+(?:studying|learning|study)[.!\\s]*$", I)

    suspend fun handle(c: Context, prefs: PreferencesStore, raw: String): String? {
        val t = raw.trim()
        if (stopNon.matches(t)) {
            return if (StudyBus.running) { StudyService.stop(c); "Okay, I will stop studying. I'll send you a short report of what I learned." } else "I'm not studying right now."
        }
        if (startNon.matches(t)) {
            if (!prefs.hasBrain()) return "I need an AI key or the offline brain to study. Add one in Settings first."
            if (StudyBus.running) return "I'm already studying. Say \"stop studying\" to stop."
            StudyService.start(c)
            return "Okay, I'm studying non-stop in the background. A notification has a Stop button, or say \"stop studying\". It uses internet and battery."
        }
        if (forgetRx.matches(t)) { LearnStore.clear(c); return "Done. I forgot all my study notes." }
        if (showRx.matches(t)) {
            val notes = LearnStore.all(c)
            if (notes.isEmpty()) return "I haven't studied anything yet. Say \"learn coding\" and I'll start. I also study by myself at night when the phone is idle."
            val f = SimpleDateFormat("d MMM", Locale.US)
            val runs = StudyLog.recent(c, 5)
            val ran = if (runs.isEmpty()) "" else "Recent study runs:\n" + runs.joinToString("\n") { "• " + SimpleDateFormat("d MMM HH:mm", Locale.US).format(Date(it.time)) + ": " + it.topics.joinToString(", ") } + "\n\n"
            return ran + "I have ${notes.size} study notes:\n" + notes.takeLast(25).reversed().joinToString("\n") { "• ${it.topic} (${f.format(Date(it.time))})" }
        }
        val isCoding = coding.matches(t)
        val m = if (isCoding) null else topic.find(t)
        if (!isCoding && m == null) return null
        if (!prefs.hasBrain()) return "I need an AI key or the offline brain to study. Add one in Settings first."
        BroTaskService.update(c, "BRO is studying...")
        val learned = Learner.session(c, prefs, maxLessons = if (isCoding) 3 else 1, budgetMs = 4 * 60_000L, topic = m?.groupValues?.get(1)) { BroTaskService.update(c, it) }
        return if (learned.isEmpty()) "I couldn't study right now (no internet, or the AI said wait). I'll try again tonight."
        else "Studied and saved ${learned.size}: " + learned.joinToString("; ") + ". I'll use these notes in my answers. Total notes: ${LearnStore.count(c)}."
    }
}
