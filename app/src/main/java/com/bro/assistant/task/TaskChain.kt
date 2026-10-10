package com.bro.assistant.task

/** Splits "do A then B, after that C" (or a numbered list) into ordered steps. */
object TaskChain {

    private val seq = Regex(
        "\\s*(?:[,;]\\s*)?\\b(?:and\\s+then|then|after\\s+that|afterwards|followed\\s+by|tarvata|taruvata|tarvatha|aa\\s+tarvata)\\b\\s*" +
            "|\\s*(?:ఆ\\s*తర్వాత|తర్వాత)\\s*",
        RegexOption.IGNORE_CASE
    )
    private val building = Regex(
        "\\b(?:write|create|build|make|generate|fix|debug)\\b.*\\b(?:code|program|script|app|website|page|function)\\b",
        RegexOption.IGNORE_CASE
    )

    fun split(text: String): List<String> {
        val t = text.trim()
        if (t.length > 500 || t.contains('"') || t.contains('“')) return listOf(t)
        if (Regex("^(?:agent|inside|operate)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) return listOf(t)
        if (building.containsMatchIn(t)) return listOf(t)
        val lines = t.lines().map { it.trim().replace(Regex("^(?:\\d+[.)]|[-*•])\\s*"), "") }.filter { it.isNotEmpty() }
        val src = if (lines.size > 1) lines else listOf(t)
        val out = src.flatMap { seq.split(it) }.map { it.trim().trim(',', ';', '.') }.filter { it.length >= 2 }
        return if (out.size > 1) out.take(8) else listOf(t)
    }
}
