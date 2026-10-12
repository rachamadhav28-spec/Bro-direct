package com.bro.assistant.ai

import com.bro.assistant.ErrorHandler
import com.bro.assistant.memory.PreferencesStore
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/**
 * Splits a big task into parts, gives each part to the free AI that is best at that kind of work
 * (coding -> Mistral/Codestral, quick -> Groq, long text -> Gemini, thinking -> NVIDIA ...),
 * and joins the answers. Parts that depend on earlier parts get those results as context.
 */
object TaskDivider {

    private data class Part(val id: Int, val title: String, val skill: Skill, val prompt: String, val deps: List<Int>)

    private const val PLAN_PROMPT =
        "You split a big task into smaller parts for different AI helpers. Reply with JSON only, no other text:\n" +
            "{\"parts\":[{\"id\":1,\"title\":\"short title\",\"skill\":\"code|fast|long|reason|general\"," +
            "\"prompt\":\"complete, self-contained instruction for this part\",\"deps\":[]}]}\n" +
            "Rules: 1 to 6 parts. If the task is simple, return exactly 1 part. " +
            "skill: code = writing or fixing code; fast = short quick answers; long = long documents, summaries, big text; " +
            "reason = maths, logic, planning, comparing; general = everything else. " +
            "deps = ids of EARLIER parts whose result this part needs (only smaller ids). " +
            "Never add a part that only combines or repeats other parts. Keep the user's language."

    private const val PART_SYSTEM =
        "You are BRO, a helpful assistant. Do exactly the task you are given, nothing extra. Be concise. " +
            "If it needs code, give complete working code in fenced code blocks with short notes."

    private val codeWords = Regex(
        "\\b(?:code|program|script|function|algorithm|java|python|kotlin|javascript|html|css|sql|bug|debug|app|website|api|class)\\b",
        RegexOption.IGNORE_CASE
    )
    private val longWords = Regex("\\b(?:summari[sz]e|translate|document|article|essay|report|pdf|transcript)\\b", RegexOption.IGNORE_CASE)
    private val thinkWords = Regex("\\b(?:solve|prove|calculate|compare|plan|why|logic|math|maths|step by step|strategy)\\b", RegexOption.IGNORE_CASE)

    fun classify(text: String): Skill = when {
        codeWords.containsMatchIn(text) -> Skill.CODE
        longWords.containsMatchIn(text) -> Skill.LONG
        thinkWords.containsMatchIn(text) -> Skill.REASON
        text.length < 80 -> Skill.FAST
        else -> Skill.GENERAL
    }

    /** True when a message looks like several jobs in one, so BRO should divide it by itself. */
    fun looksBig(text: String): Boolean {
        if (text.length < 160) return false
        var signs = 0
        if (Regex("\\b(?:and then|then|also|after that|next)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) signs++
        if (Regex("\\band\\b", RegexOption.IGNORE_CASE).findAll(text).count() >= 2) signs++
        if (text.count { it == '\n' } >= 2) signs++
        if (Regex("(?m)^\\s*(?:\\d+[.)]|[-*•])\\s").containsMatchIn(text)) signs++
        return signs >= 1
    }

    private fun parseSkill(s: String): Skill = when (s.trim().lowercase()) {
        "code" -> Skill.CODE
        "fast" -> Skill.FAST
        "long" -> Skill.LONG
        "reason" -> Skill.REASON
        else -> Skill.GENERAL
    }

    private suspend fun plan(prefs: PreferencesStore, gemini: GeminiClient, task: String): List<Part> {
        return try {
            val raw = Router.run(prefs, Skill.FAST, PLAN_PROMPT, task, true, 30000, gemini).second
            val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val arr = JSONObject(cleaned).getJSONArray("parts")
            val parts = ArrayList<Part>()
            for (i in 0 until minOf(arr.length(), 6)) {
                val o = arr.getJSONObject(i)
                val deps = ArrayList<Int>()
                o.optJSONArray("deps")?.let { d -> for (j in 0 until d.length()) deps.add(d.optInt(j)) }
                val id = i + 1
                parts.add(
                    Part(
                        id, o.optString("title", "Part $id").ifBlank { "Part $id" },
                        parseSkill(o.optString("skill", "general")),
                        o.getString("prompt"), deps.filter { it in 1 until id }
                    )
                )
            }
            parts.ifEmpty { listOf(Part(1, "Task", classify(task), task, emptyList())) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ErrorHandler.log("TaskDivider", "planning failed, doing it as one part", e)
            listOf(Part(1, "Task", classify(task), task, emptyList()))
        }
    }

    suspend fun run(prefs: PreferencesStore, gemini: GeminiClient, task: String, context: String = ""): String {
        if (Router.keyedProviders(prefs).isEmpty()) {
            return "No AI keys are set yet. Open Settings and add your free keys (each one has a Get key button)."
        }
        val parts = plan(prefs, gemini, task)
        val withContext = if (context.isBlank()) "" else "$context\n\n"

        // one part: just send it to the best AI
        if (parts.size == 1) {
            val p = parts[0]
            val (who, text) = Router.run(prefs, p.skill, systemFor(p.skill), withContext + task, false, 90000, gemini)
            return text.trim() + "\n\n(${p.skill.label} · ${who.label})"
        }

        val results = HashMap<Int, String>()
        val out = StringBuilder()
        out.append("I split this into ").append(parts.size).append(" parts and gave each to the best free AI:\n")
        for (p in parts) out.append("${p.id}. ${p.title} (${p.skill.label})\n")
        out.append("\n")

        for (p in parts) {
            val input = buildString {
                append(withContext)
                append(p.prompt)
                if (p.deps.isNotEmpty()) {
                    append("\n\nResults of earlier parts you can use:")
                    for (d in p.deps) results[d]?.let { append("\n--- part ").append(d).append(" ---\n").append(it.take(3000)) }
                }
            }
            try {
                val (who, text) = Router.run(prefs, p.skill, systemFor(p.skill), input, false, 90000, gemini)
                results[p.id] = text.trim()
                out.append("### ${p.id}. ${p.title} — ${p.skill.label} · ${who.label}\n").append(text.trim()).append("\n\n")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ErrorHandler.log("TaskDivider", "part ${p.id} failed", e)
                out.append("### ${p.id}. ${p.title}\nThis part failed: ${ErrorHandler.friendly(e)}\n\n")
            }
        }
        return out.toString().trim()
    }

    private fun systemFor(skill: Skill): String = when (skill) {
        Skill.CODE -> PART_SYSTEM + " Write complete code, not fragments."
        Skill.REASON -> PART_SYSTEM + " Think carefully and show the key steps briefly."
        else -> PART_SYSTEM
    }
}
