package com.bro.assistant.task

import android.content.Context
import com.bro.assistant.ErrorHandler
import com.bro.assistant.LocalCommandParser
import com.bro.assistant.actions.ExtraCommands
import com.bro.assistant.ai.IntentParser
import com.bro.assistant.memory.PreferencesStore
import com.bro.assistant.memory.SessionMemory
import kotlinx.coroutines.CancellationException

data class PlanResult(
    val reply: String? = null,
    val actions: List<BroAction> = emptyList(),
    val error: String? = null
)

/**
 * Decides how to understand a command: simple offline answers first, then the local
 * parser, and only then the AI. Every plan is validated before anything runs.
 */
class ActionPlanner(
    private val context: Context,
    private val memory: SessionMemory,
    private val prefs: PreferencesStore
) {
    private val extra = ExtraCommands(context)
    private val local = LocalCommandParser(memory)
    /** Set by the screen: receives the answer as it streams in. */
    @Volatile var onPartial: ((String) -> Unit)? = null

    private val ai = IntentParser(memory, prefs)
    private val gemini = com.bro.assistant.ai.GeminiClient(prefs)

    suspend fun plan(text: String): PlanResult {
        // instant built-in answers (boss, greetings): no AI, no internet
        com.bro.assistant.ai.Persona.answer(text)?.let { return PlanResult(reply = it) }

        extra.tryHandle(text)?.let { return PlanResult(reply = it) }

        // "what can you do in whatsapp" -> read the built-in playbook
        Regex("^(?:what\\s+can\\s+you\\s+do\\s+(?:in|with|on)|how\\s+does|tell\\s+me\\s+about)\\s+(.{2,30}?)[?.!]*$", RegexOption.IGNORE_CASE)
            .find(text.trim())?.let { m ->
                com.bro.assistant.actions.AppKnowledge.summaryFor(m.groupValues[1].removeSuffix(" work").trim())?.let {
                    return PlanResult(reply = "Here is what I know about it, and I can operate it for you with: agent <what you want>\n\n$it")
                }
            }

        // explicit: "agent <goal>" / "inside <goal>" runs the screen-operating agent directly
        Regex("^(?:agent|operate|inside)\\b[:,]?\\s+(.{3,})$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(text.trim())?.let { m ->
                return validated("On it. I'll work inside the app.", listOf(BroAction(ActionType.AGENT_TASK, mapOf("goal" to m.groupValues[1].trim()))))
            }

        // which free AI keys are set and who does what
        if (Regex("^(?:ai\\s+status|my\\s+(?:ai\\s+)?keys|which\\s+ai)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text.trim())) {
            return PlanResult(reply = com.bro.assistant.ai.Router.statusText(prefs))
        }

        local.parse(text)?.let { return validated(it.reply, it.actions) }

        if (!prefs.hasBrain()) {
            return PlanResult(
                error = "I couldn't understand that as a simple command, and no AI is set up. " +
                    "Add a free AI key (Gemini, Groq, ...) or install the offline brain in Settings, or try a simpler command."
            )
        }
        // "divide <task>" (or a long multi-part message): split it and give each part to the best free AI
        val divideCmd = Regex("^(?:divide|split|team)\\b(?:\\s+task)?[:,]?\\s+(.{8,})$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(text.trim())
        val codeJob = looksLikeCode(text) && com.bro.assistant.ai.CodeAgent.isComplex(text) && !com.bro.assistant.ai.LocalLlm.willUse(prefs)
        val auto = divideCmd == null && !codeJob && prefs.hasProviderKeys() &&
            com.bro.assistant.ai.Router.keyedProviders(prefs).size >= 2 &&
            com.bro.assistant.ai.TaskDivider.looksBig(text)
        if (divideCmd != null || auto) {
            val task = divideCmd?.groupValues?.get(1)?.trim() ?: text.trim()
            return try {
                val answer = com.bro.assistant.ai.TaskDivider.run(prefs, gemini, task, memory.historyText().takeLast(1500))
                SessionMemory.extractLastCode(answer)?.let { memory.lastCode = it }
                PlanResult(reply = withPdf(text, answer))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ErrorHandler.log("ActionPlanner", "divide failed", e)
                PlanResult(error = ErrorHandler.friendly(e))
            }
        }
        if (looksLikeCode(text) && !com.bro.assistant.learn.AnswerCache.online(context.applicationContext) && !com.bro.assistant.ai.LocalLlm.willUse(prefs)) {
            com.bro.assistant.learn.AnswerCache.find(context.applicationContext, text, 0.6)?.let { return PlanResult(reply = it + "\n\n(Offline: from my memory)") }
            com.bro.assistant.learn.AnswerCache.fromNotes(context.applicationContext, text)?.let { return PlanResult(reply = it) }
        }
        if (looksLikeCode(text)) {
            return try {
                val answer = if (codeJob) {
                    val notes = com.bro.assistant.learn.LearnStore.promptText(context.applicationContext, text, 1500)
                    com.bro.assistant.ai.CodeAgent.solve(context.applicationContext, gemini, text, notes)
                } else ai.answerFreely(text)
                SessionMemory.extractLastCode(answer)?.let { memory.lastCode = it }
                if (!codeJob) com.bro.assistant.learn.AnswerCache.put(context.applicationContext, text, answer)
                PlanResult(reply = withPdf(text, answer))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ErrorHandler.log("ActionPlanner", "code answer failed", e)
                PlanResult(error = ErrorHandler.friendly(e))
            }
        }
        // instant replies: a very close match is answered from memory; with no internet any decent match is
        run {
            val app = context.applicationContext
            val offline = !com.bro.assistant.learn.AnswerCache.online(app)
            val cached = com.bro.assistant.learn.AnswerCache.find(app, text, if (offline) 0.6 else 0.92)
            if (cached != null && (offline || !looksLikeCode(text))) return PlanResult(reply = cached + if (offline) "\n\n(Offline: from my memory)" else "")
            if (offline && !com.bro.assistant.ai.LocalLlm.willUse(prefs)) {
                com.bro.assistant.learn.AnswerCache.fromNotes(app, text)?.let { return PlanResult(reply = it) }
            }
        }
        if (com.bro.assistant.ai.ChatAgent.wants(text, prefs)) {
            return try {
                val answer = com.bro.assistant.ai.ChatAgent.answer(context.applicationContext, prefs, text, memory.historyText()) { onPartial?.invoke(it) }
                SessionMemory.extractLastCode(answer)?.let { memory.lastCode = it }
                PlanResult(reply = answer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ErrorHandler.log("ActionPlanner", "chat answer failed", e)
                PlanResult(error = ErrorHandler.friendly(e))
            }
        }
        return try {
            val result = ai.understand(text)
            if (result.actions.isEmpty() && result.reply.isNotBlank()) com.bro.assistant.learn.AnswerCache.put(context.applicationContext, text, result.reply)
            validated(result.reply.ifBlank { null }, result.actions)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ErrorHandler.log("ActionPlanner", "AI planning failed", e)
            PlanResult(error = ErrorHandler.friendly(e))
        }
    }

    private val codeWord = Regex(
        "\\b(?:code|program|script|function|algorithm|java|python|kotlin|javascript|typescript|html|css|sql|" +
            "c\\+\\+|c#|php|swift|rust|bash|regex|class|api|calculator|website|webpage|web page|page|game|app|ui|layout|button|buttons|responsive|css|index\\.html)\\b|కోడ్", RegexOption.IGNORE_CASE
    )
    private val codeVerb = Regex(
        "\\b(?:write|create|generate|make|build|fix|debug|explain|convert|rayi|raayi|rayandi|rasi|ivvu|cheppu)\\b|రాయి|రాయండి",
        RegexOption.IGNORE_CASE
    )
    private val followUp = Regex(
        "^(?:fix|change|add|remove|optimi[sz]e|rewrite|modify|explain|convert|make|now|also|update|improve|edit|use|resize|center)\\b",
        RegexOption.IGNORE_CASE
    )

    // talk about code that is already in the chat ("in the code there were lines like ___, I don't want them")
    private val codeTalk = Regex(
        "\\b(?:code|line|lines|comment|comments|separator|separators|function|class|variable|import|imports|syntax|compile|compiler|error|bug|mainactivity|xml|layout|snippet)\\b",
        RegexOption.IGNORE_CASE
    )

    private fun looksLikeCode(text: String): Boolean =
        (codeWord.containsMatchIn(text) && codeVerb.containsMatchIn(text)) ||
            (memory.lastCode != null && (followUp.containsMatchIn(text.trim()) || codeTalk.containsMatchIn(text)))

    private fun validated(reply: String?, actions: List<BroAction>): PlanResult {
        for (action in actions) {
            ActionValidator.validate(action)?.let { return PlanResult(error = it) }
        }
        return PlanResult(reply = reply, actions = actions)
    }

    /** Adds a note that a PDF of the code was saved (Downloads/BRO). */
    private fun withPdf(request: String, answer: String): String {
        val name = try { com.bro.assistant.features.CodePdf.make(context.applicationContext, request, answer) } catch (e: Exception) { null }
        return if (name == null) answer else answer + "\n\nPDF saved: Downloads/BRO/" + name + " (say \"open the pdf\" or \"share the pdf\")"
    }
}
