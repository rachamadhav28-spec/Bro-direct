package com.bro.assistant.watch

import android.content.Context

/** "Every hour check ... and retry" style commands: create, list and stop watches. No AI needed. */
object WatchCommands {

    private val stopRx = Regex("^(?:stop|cancel|remove|delete|clear|end)\\s+(?:the\\s+|all\\s+|my\\s+)*(?:watch(?:es|ing)?|auto(?:matic)?\\s+check(?:s|ing)?|hourly\\s+check(?:s)?|claude\\s+(?:watch|check)(?:ing)?)[.!]*$", RegexOption.IGNORE_CASE)
    private val listRx = Regex("^(?:watch(?:es)?\\s+status|list\\s+(?:my\\s+)?watch(?:es)?|what\\s+are\\s+you\\s+watching|show\\s+watch(?:es)?)[?.!]*$", RegexOption.IGNORE_CASE)
    private val everyRx = Regex("^(?:every|each)\\s+(\\d+)?\\s*(hours?|hrs?|minutes?|mins?)\\s*,?\\s*(.{6,})$", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL)

    /** Used by the command splitter so these sentences are not cut at "then". */
    fun isWatchRequest(text: String): Boolean = looksLikeClaude(text) || everyRx.containsMatchIn(text.trim())

    private fun looksLikeClaude(t: String) =
        Regex("claude", RegexOption.IGNORE_CASE).containsMatchIn(t) &&
            Regex("limit|reset|quota", RegexOption.IGNORE_CASE).containsMatchIn(t) &&
            Regex("every|hour|automatic|keep\\s+check|retry|watch|check", RegexOption.IGNORE_CASE).containsMatchIn(t)

    fun handle(c: Context, text: String): String? {
        val t = text.trim()
        if (stopRx.matches(t)) {
            val n = WatchStore.all(c).size
            WatchStore.all(c).forEach { WatchScheduler.cancel(c, it.id) }
            WatchStore.clear(c)
            return if (n == 0) "I wasn't watching anything." else "Stopped $n watch${if (n == 1) "" else "es"}."
        }
        if (listRx.matches(t)) {
            val l = WatchStore.all(c)
            if (l.isEmpty()) return "I'm not watching anything right now."
            val fmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            return "Watching:\n" + l.joinToString("\n") {
                "- " + (if (it.kind == "claude") "Claude limit, then retry" else it.goal.take(50)) +
                    " (every ${it.intervalMin} min, next ${fmt.format(java.util.Date(it.nextRun))}, ${it.runs} checks so far)"
            }
        }
        val interval: Int
        val kind: String
        val goal: String
        if (looksLikeClaude(t)) {
            val m = Regex("every\\s+(\\d+)\\s*(hour|hr|minute|min)", RegexOption.IGNORE_CASE).find(t)
            interval = if (m == null) 60 else m.groupValues[1].toInt() * (if (m.groupValues[2].startsWith("h", true)) 60 else 1)
            kind = "claude"; goal = WatchRunner.CLAUDE_GOAL
        } else {
            val m = everyRx.find(t) ?: return null
            val n = m.groupValues[1].ifEmpty { "1" }.toInt()
            interval = if (m.groupValues[2].startsWith("h", true)) n * 60 else n
            kind = "generic"; goal = m.groupValues[3].trim()
        }
        if (interval < 5) return "That's too often. The shortest I'll watch is every 5 minutes."
        val w = Watch(id = System.currentTimeMillis().toString(), kind = kind, goal = goal, intervalMin = interval.coerceAtLeast(5), nextRun = System.currentTimeMillis())
        WatchStore.put(c, w)
        WatchRunner.startNow(c, w.id) // first check now
        val every = if (interval % 60 == 0) "${interval / 60} hour${if (interval == 60) "" else "s"}" else "$interval minutes"
        return if (kind == "claude")
            "Okay. I'll check your Claude chat in Chrome every $every. If the limit is still on I read the reset time and check again right after it. " +
                "When it's over I'll retry in your most recent chat and tell you. Starting the first check now. " +
                "Keep the phone unlocked or unlock it later: I run missed checks when you unlock. Say \"stop watching\" to cancel."
        else
            "Okay. Every $every I'll do this in the background: ${goal.take(80)}. Starting the first check now. Say \"stop watching\" to cancel."
    }
}
