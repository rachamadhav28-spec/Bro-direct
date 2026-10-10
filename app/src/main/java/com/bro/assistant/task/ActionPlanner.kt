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
    context: Context,
    private val memory: SessionMemory,
    private val prefs: PreferencesStore
) {
    private val extra = ExtraCommands(context)
    private val local = LocalCommandParser(memory)
    private val ai = IntentParser(memory, prefs)

    suspend fun plan(text: String): PlanResult {
        extra.tryHandle(text)?.let { return PlanResult(reply = it) }

        // explicit: "agent <goal>" / "inside <goal>" runs the screen-operating agent directly
        Regex("^(?:agent|operate|inside)\\b[:,]?\\s+(.{3,})$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(text.trim())?.let { m ->
                return validated("On it. I'll work inside the app.", listOf(BroAction(ActionType.AGENT_TASK, mapOf("goal" to m.groupValues[1].trim()))))
            }

        local.parse(text)?.let { return validated(it.reply, it.actions) }

        if (prefs.apiKey.isBlank()) {
            return PlanResult(
                error = "I couldn't understand that as a simple command, and no AI key is set. " +
                    "Add a Gemini API key in Settings, or try a simpler command."
            )
        }
        if (looksLikeCode(text)) {
            return try {
                val answer = ai.answerFreely(text)
                SessionMemory.extractLastCode(answer)?.let { memory.lastCode = it }
                PlanResult(reply = answer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ErrorHandler.log("ActionPlanner", "code answer failed", e)
                PlanResult(error = ErrorHandler.friendly(e))
            }
        }
        return try {
            val result = ai.understand(text)
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

    private fun looksLikeCode(text: String): Boolean =
        (codeWord.containsMatchIn(text) && codeVerb.containsMatchIn(text)) ||
            (memory.lastCode != null && followUp.containsMatchIn(text.trim()))

    private fun validated(reply: String?, actions: List<BroAction>): PlanResult {
        for (action in actions) {
            ActionValidator.validate(action)?.let { return PlanResult(error = it) }
        }
        return PlanResult(reply = reply, actions = actions)
    }
}
