package com.bro.assistant.ai

import com.bro.assistant.actions.ScreenContext
import com.bro.assistant.learn.AnswerCache
import com.bro.assistant.learn.LearnStore
import com.bro.assistant.memory.MemoryStore
import com.bro.assistant.memory.PreferencesStore
import kotlinx.coroutines.CancellationException

/**
 * BRO as a chat assistant, like ChatGPT: questions, explanations, advice and writing get a full, clear answer that streams in
 * word by word. Questions about current things use Google Search. Phone commands never come here.
 */
object ChatAgent {
    private val action = Regex(
        "^(?:please\\s+)?(?:open|launch|start|call|dial|send|play|turn|switch|set|enable|disable|toggle|increase|decrease|take|go|close|read|remind|message|text|whatsapp|" +
            "navigate|share|record|install|uninstall|delete|cancel|stop|pause|resume|skip|next|back|home|lock|unlock|mute|unmute|search\\s+(?:for\\s+)?on|click|tap|scroll|type)\\b|" +
            "\\b(?:chey|cheyyi|cheyandi|pampu|pampandi|tiyyi|teeyi|penchu|taggu|ravu|vestunna)\\b",
        RegexOption.IGNORE_CASE
    )
    private val question = Regex(
        "\\?\\s*$|^(?:please\\s+)?(?:what|why|how|who|whom|whose|when|where|which|explain|tell\\s+me|describe|define|compare|difference|meaning|" +
            "is|are|was|were|can|could|should|would|will|do|does|did|give\\s+me|suggest|recommend|summari[sz]e|translate|teach|help\\s+me|write\\s+(?:me\\s+)?(?:an?\\s+)?(?:essay|poem|story|letter|email|speech|paragraph|summary|resume|cv)|" +
            "list|plan|solve|calculate|convert|rewrite|improve|correct|check\\s+my|i\\s+(?:want|need)\\s+to\\s+(?:know|learn|understand))\\b|" +
            "\\b(?:enti|ela|evaru|enduku|eppudu|ekkada|ante\\s+enti|gurinchi|meaning\\s+enti|artham)\\b|(?:ఏమిటి|ఎలా|ఎవరు|ఎందుకు|ఎప్పుడు|ఎక్కడ|గురించి|అంటే)",
        RegexOption.IGNORE_CASE
    )
    private val fresh = Regex(
        "\\b(?:latest|current(?:ly)?|today|tonight|now|news|recent|this\\s+(?:week|month|year)|20(?:2[5-9]|3\\d)|price\\s+of|score|weather|stock|" +
            "who\\s+is\\s+the\\s+(?:current|new)|release\\s+date|trending|live)\\b",
        RegexOption.IGNORE_CASE
    )

    /** Chat questions only: not phone commands, not code, not about the screen. */
    fun wants(text: String, prefs: PreferencesStore): Boolean {
        val t = text.trim()
        if (t.length < 6 || !prefs.hasBrain()) return false
        if (action.containsMatchIn(t) || ScreenContext.isAboutScreen(t)) return false
        return question.containsMatchIn(t)
    }

    private const val SYSTEM = """
You are BRO, a smart, friendly AI assistant (like ChatGPT or Claude) talking with R. Madhav, a B.Tech student from Telangana, on his phone.
How to answer:
- Give a real, complete answer. Be clear and well organised: short paragraphs, and bullet or numbered steps when they help.
- Teach: explain the idea simply first, then the detail. Use a small example. For maths or logic, show the steps.
- Be honest. If you are not sure, say so. Never invent facts, names, links or numbers.
- If search results are available, use them for current facts and mention the sources briefly.
- Reply in the user's language style: English, Telugu (తెలుగు), or Tenglish (Telugu in English letters). Keep technical terms in English.
- Put code in fenced blocks with the language name.
- Keep it as short as the question allows. Simple questions get short answers; do not pad.
- Answer the real question first, then add what helps. No flattery, no filler openers like "Great question".
- Say what you are unsure about and how sure you are. If the question has two reasonable readings, answer the likelier one and name your assumption; ask one short question only when guessing could waste his time.
- Check your own work: recompute numbers, re-read code for errors, and make sure each claim is something you actually know. Correct yourself if you notice a mistake.
- Give a clear recommendation when asked to choose, with the main reason and the main risk.
""" + "\n" + CodeFormat.RULES

    private val hard = Regex(
        "\\b(?:why|how\\s+(?:do|does|can|to|would|should)|compare|difference|explain|plan|design|debug|prove|solve|analy[sz]e|step\\s*by\\s*step|pros\\s+and\\s+cons|" +
            "trade-?offs?|best\\s+way|strategy|architecture|optimi[sz]e|algorithm|derive|evaluate|decide|which\\s+is\\s+better)\\b",
        RegexOption.IGNORE_CASE
    )

    private const val THINK = """
You are the private reasoning step of an AI assistant. Do NOT answer the user. Write working notes for yourself, under 220 words:
1. What exactly is being asked, and what a great answer must contain.
2. The key facts, formulas or steps (work any numbers out carefully).
3. Likely mistakes or traps, and anything uncertain.
4. The best order for the answer.
Plain text, terse.
"""

    /** Hard questions get a short private planning pass first, then the streamed answer uses those notes. */
    private suspend fun thinkFirst(ai: GeminiClient, prefs: PreferencesStore, question: String, context: String): String {
        if (!prefs.deepThink || LocalLlm.willUse(prefs)) return ""
        if (!(question.length > 140 || hard.containsMatchIn(question))) return ""
        com.bro.assistant.Activity.step("Thinking it through")
        return try {
            ai.generate(THINK, context.takeLast(2500) + "\nQuestion: " + question, json = false, timeoutMs = 40000, engine = "cloud").trim().take(1600)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { "" }
    }

    suspend fun answer(
        ctx: android.content.Context, prefs: PreferencesStore, question: String, history: String, onPartial: (String) -> Unit
    ): String {
        val notes = LearnStore.promptText(ctx, question, 1200)
        val mem = MemoryStore.promptText(ctx, 800)
        val user = buildString {
            append(mem).append(notes)
            if (history.isNotBlank()) append("Conversation so far:\n").append(history.takeLast(3000)).append("\n\n")
            append("User: ").append(question)
        }
        val ai = GeminiClient(prefs)
        val plan = thinkFirst(ai, prefs, question, user)
        com.bro.assistant.Activity.step("Writing the answer")
        val prompt = if (plan.isBlank()) user else user + "\n\n[Your private working notes. Use them, but never show or mention them:\n" + plan + "\n]"
        val grounded = fresh.containsMatchIn(question)
        val text = try {
            if (prefs.apiKey.isNotBlank() && !LocalLlm.willUse(prefs) && prefs.providerKey("claude").isBlank()) ai.chatStream(SYSTEM, prompt, grounded, onPartial)
            else ai.generate(SYSTEM, prompt, json = false, timeoutMs = 90000, skill = Skill.GENERAL)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ai.generate(SYSTEM, prompt, json = false, timeoutMs = 90000, skill = Skill.GENERAL)
        }.trim()
        AnswerCache.put(ctx, question, text)
        return text
    }
}
