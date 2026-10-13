package com.bro.assistant.ai

import android.content.Context
import com.bro.assistant.BroTaskService

/**
 * Bigger coding jobs are done in three steps, like a careful engineer: plan, write, then review and fix.
 * Small requests skip this and get one fast answer.
 */
object CodeAgent {
    private val complexWords = Regex(
        "\\b(?:complex|complete|full|entire|whole|project|advanced|production|end[- ]to[- ]end|multi[- ]?file|architecture|backend|frontend|" +
            "database|authentication|login system|management system|full[- ]stack|optimi[sz]e|refactor|design pattern|algorithm|data structure|game|compiler|interpreter)\\b",
        RegexOption.IGNORE_CASE
    )

    fun isComplex(text: String): Boolean = text.length >= 220 || complexWords.containsMatchIn(text)

    private const val PLAN =
        "You are a senior software architect. Write a concise implementation plan for the request: language and libraries, the files " +
            "(names), main data structures and functions, edge cases, and how to test. Maximum 200 words. No code."

    private const val WRITE =
        "You are BRO, an expert programmer. Write COMPLETE, runnable code that follows the plan. Rules: every file in its own fenced block " +
            "with the language, the file name on the first comment line; all imports; no placeholders or '...'; handle errors and edge cases. " +
            "After the code give at most 3 short lines on how to run it. The user may write English, Telugu or Tenglish: answer in the same style " +
            "but keep code and identifiers in English."

    private const val REVIEW =
        "You are a strict code reviewer. Check the code for syntax errors, logic bugs, missing imports, wrong API use and unhandled edge cases " +
            "against the request. Reply with the FINAL corrected code in full (every file, fenced blocks, file name on the first line), then at most " +
            "3 short lines saying what you fixed. If it is already correct, return it unchanged and say so."

    suspend fun solve(c: Context, ai: GeminiClient, request: String, notes: String): String {
        BroTaskService.update(c, "Coding 1 of 3: planning")
        val plan = try { ai.generate(PLAN, notes + request, json = false, timeoutMs = 90000, skill = Skill.REASON).trim() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { "" }
        BroTaskService.update(c, "Coding 2 of 3: writing the code")
        val draft = ai.generate(WRITE, notes + "Request:\n$request\n\nPlan:\n$plan", json = false, timeoutMs = 120000, skill = Skill.CODE).trim()
        BroTaskService.update(c, "Coding 3 of 3: reviewing for bugs")
        return try {
            val reviewed = ai.generate(REVIEW, "Request:\n$request\n\nCode:\n$draft", json = false, timeoutMs = 120000, skill = Skill.CODE).trim()
            if (reviewed.contains("```")) reviewed else draft
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { draft }
    }
}
