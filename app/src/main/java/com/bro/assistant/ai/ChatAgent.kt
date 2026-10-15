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
"""

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
        val grounded = fresh.containsMatchIn(question)
        val text = try {
            if (prefs.apiKey.isNotBlank() && !LocalLlm.willUse(prefs)) ai.chatStream(SYSTEM, user, grounded, onPartial)
            else ai.generate(SYSTEM, user, json = false, timeoutMs = 90000, skill = Skill.GENERAL)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ai.generate(SYSTEM, user, json = false, timeoutMs = 90000, skill = Skill.GENERAL)
        }.trim()
        AnswerCache.put(ctx, question, text)
        return text
    }
}
