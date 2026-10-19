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
            "with the language; all imports; no placeholders or '...'; handle errors and edge cases. " +
            "After the code give at most 3 short lines on how to run it. The user may write English, Telugu or Tenglish: answer in the same style " +
            "but keep code and identifiers in English. " + CodeFormat.RULES

    private const val REVIEW =
        "You are a strict code reviewer. Check the code for syntax errors, logic bugs, missing imports, wrong API use and unhandled edge cases " +
            "against the request. Reply with the FINAL corrected code in full (every file in its own fenced block, with its file name in bold on the line above), then at most " +
            "3 short lines saying what you fixed. If it is already correct, return it unchanged and say so. " + CodeFormat.RULES

    // ---- Chunked mode: a small local model (Termux) has a 2048-token window, so one file at a time ----
    private const val FILES_PROMPT =
        "You are a senior software architect. List the source files needed for the request, in the order they should be written " +
            "(shared code first). Reply with ONLY lines in this exact form, at most 8 lines, nothing else:\n" +
            "FILE: <real file name> | <what it contains, one short sentence>"

    private const val FILE_WRITE =
        "You are BRO, an expert programmer. Write ONLY the one file you are asked for, complete and runnable: all imports, " +
            "no placeholders or '...', handle errors. Use exactly the names of functions and classes shown in the other files. " +
            "Reply with the file name in bold on its own line, then the file in one fenced block with its language, then nothing else. " +
            CodeFormat.RULES

    private fun localCoderOn(c: Context): Boolean =
        try {
            val p = com.bro.assistant.memory.PreferencesStore(c)
            p.providerKey("colab").isNotBlank() || (p.codeLocalFirst && LocalLlm.ggufReady(c))
        } catch (e: Exception) { false }

    private val signatureRx = Regex("^\\s*(?:import |from |class |def |fun |function |export |const |public |private |interface |data class |object )")

    private fun summary(name: String, code: String): String {
        val lines = code.lines().filter { signatureRx.containsMatchIn(it) }.take(12).joinToString("\n") { it.trim().take(110) }
        return "$name:\n${lines.take(600)}"
    }

    private fun langOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "py" -> "python"; "kt" -> "kotlin"; "java" -> "java"; "js" -> "javascript"; "ts" -> "typescript"
        "html" -> "html"; "css" -> "css"; "xml" -> "xml"; "json" -> "json"; "sh" -> "bash"; "c" -> "c"; "cpp" -> "cpp"; else -> ""
    }

    private suspend fun solveChunked(c: Context, ai: GeminiClient, request: String, notes: String): String? {
        val plan = ai.generate(FILES_PROMPT, notes + "Request:\n" + request, json = false, timeoutMs = 120000, skill = Skill.REASON)
        val files = plan.lines().mapNotNull { l ->
            val t = l.trim().removePrefix("-").trim()
            if (!t.startsWith("FILE:", ignoreCase = true)) return@mapNotNull null
            val body = t.substring(5).trim()
            val name = body.substringBefore('|').trim().trim('`', '*')
            if (name.isBlank() || name.length > 80 || name.contains(' ')) null else name to body.substringAfter('|', "").trim()
        }.distinctBy { it.first }.take(8)
        if (files.size < 2) return null   // a single file fits the normal flow
        val outline = files.joinToString("\n") { "${it.first}: ${it.second}" }
        val done = ArrayList<Pair<String, String>>()
        val out = StringBuilder()
        files.forEachIndexed { i, (name, purpose) ->
            BroTaskService.update(c, "Coding file ${i + 1} of ${files.size}: $name"); com.bro.assistant.Activity.step("Writing $name (${i + 1} of ${files.size})")
            val others = done.joinToString("\n\n") { summary(it.first, it.second) }.take(1400)
            val user = notes.take(300) + "Request:\n" + request.take(700) + "\n\nAll files:\n" + outline +
                (if (others.isNotBlank()) "\n\nAlready written (signatures only):\n$others" else "") +
                "\n\nNow write ONLY $name: $purpose"
            val reply = ai.generate(FILE_WRITE, user, json = false, timeoutMs = 240000, skill = Skill.CODE).trim()
            val code = Regex("```[A-Za-z0-9+#_-]*\\n([\\s\\S]*?)(?:```|$)").find(reply)?.groupValues?.get(1)?.trimEnd() ?: reply
            done.add(name to code)
            out.append("**").append(name).append("**\n```").append(langOf(name)).append('\n').append(code).append("\n```\n\n")
        }
        out.append("Written file by file so it fits the on-phone model. Run the first file listed, or use the usual command for this language.")
        return out.toString()
    }

    suspend fun solve(c: Context, ai: GeminiClient, request: String, notes: String): String {
        if (localCoderOn(c)) {
            try { solveChunked(c, ai, request, notes)?.let { return it } }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { /* fall back to the normal three-step flow */ }
        }
        BroTaskService.update(c, "Coding 1 of 3: planning"); com.bro.assistant.Activity.step("Planning the code")
        val plan = try { ai.generate(PLAN, notes + request, json = false, timeoutMs = 90000, skill = Skill.REASON).trim() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { "" }
        BroTaskService.update(c, "Coding 2 of 3: writing the code"); com.bro.assistant.Activity.step("Writing the code")
        val ui = DesignGuide.isUi(request)
        val draft = ai.generate(if (ui) WRITE + "\n" + DesignGuide.GUIDE else WRITE, notes + "Request:\n$request\n\nPlan:\n$plan", json = false, timeoutMs = 120000, skill = Skill.CODE).trim()
        BroTaskService.update(c, "Coding 3 of 3: reviewing for bugs"); com.bro.assistant.Activity.step("Reviewing the code for bugs")
        return try {
            val reviewed = ai.generate(if (ui) REVIEW + " " + DesignGuide.CRITIQUE else REVIEW, "Request:\n$request\n\nCode:\n$draft", json = false, timeoutMs = 120000, skill = Skill.CODE).trim()
            if (reviewed.contains("```")) reviewed else draft
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { draft }
    }
}
