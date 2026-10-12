package com.bro.assistant.ai

import android.content.Context
import com.bro.assistant.memory.MemoryStore
import com.bro.assistant.memory.PreferencesStore

/** "remember ...", "what do you remember", "forget ...", "what is my ..." — answered on the phone, no AI needed. */
object MemoryCommands {
    private val I = RegexOption.IGNORE_CASE
    private val remember = Regex("^(?:please\\s+)?(?:remember|note|save|keep in mind)\\s+(?:that\\s+)?(.{3,300})$", I)
    private val listRx = Regex("^(?:what\\s+do\\s+you\\s+(?:remember|know)(?:\\s+about\\s+me)?|show\\s+(?:my\\s+)?memory|what\\s+(?:have\\s+i|did\\s+i)\\s+(?:told|tell)\\s+you|list\\s+(?:my\\s+)?memor(?:y|ies))[?.!\\s]*$", I)
    private val forgetAll = Regex("^(?:please\\s+)?forget\\s+(?:everything|all(?:\\s+of\\s+it)?|all\\s+memor(?:y|ies))[.!\\s]*$", I)
    private val forget = Regex("^(?:please\\s+)?forget\\s+(?:about\\s+)?(?:that\\s+)?(.{2,100})$", I)
    private val city = Regex("^(?:my\\s+city\\s+is|i\\s+live\\s+in|my\\s+location\\s+is)\\s+(.{2,40}?)[.!\\s]*$", I)
    private val ask = Regex("^(?:what(?:'s|\\s+is|\\s+are)|who(?:'s|\\s+is)|when(?:'s|\\s+is)|where(?:'s|\\s+is)|tell\\s+me)\\s+(?:my|the)\\s+(.{2,60}?)[?.!\\s]*$", I)
    private val stop = setOf("the", "a", "an", "of", "is", "are", "number", "name", "my", "me", "for", "to", "and")

    private fun tokens(s: String): List<String> =
        s.lowercase().replace("'s", "").split(Regex("[^a-z0-9\\u0900-\\u097F\\u0C00-\\u0C7F]+")).filter { it.length >= 2 && it !in stop }

    fun handle(ctx: Context, raw: String): String? {
        val t = raw.trim()
        if (t.length > 320) return null

        city.find(t)?.let { m ->
            val c = m.groupValues[1].trim()
            PreferencesStore(ctx).city = c
            MemoryStore.add(ctx, "The owner's city is $c")
            return "Got it, boss. Your city is $c. I'll use it for the weather in your morning briefing."
        }
        if (listRx.matches(t)) {
            val facts = MemoryStore.all(ctx)
            return if (facts.isEmpty()) "I don't have anything saved yet. Say \"remember that ...\" and I'll keep it."
            else "Here is what I remember:\n" + facts.mapIndexed { i, f -> "${i + 1}. ${f.text}" }.joinToString("\n") +
                "\n\nYou can also delete any of them in Settings, BRO's memory."
        }
        if (forgetAll.matches(t)) {
            MemoryStore.clear(ctx)
            return "Done. I forgot everything you asked me to remember."
        }
        // "forget ..." (but not "forget it")
        forget.find(t)?.let { m ->
            val words = tokens(m.groupValues[1])
            if (words.isNotEmpty()) {
                val hit = MemoryStore.all(ctx).filter { f -> val ft = f.text.lowercase(); words.all { it in ft } }
                if (hit.isEmpty()) return "I don't have anything saved about that."
                hit.forEach { MemoryStore.remove(ctx, it.id) }
                return "Okay, I forgot " + (if (hit.size == 1) "\"${hit[0].text}\"." else "${hit.size} things.")
            }
        }
        remember.find(t)?.let { m ->
            val fact = m.groupValues[1].trim()
            // never store passwords, PINs or card numbers
            if (Regex("password|passcode|\\bpin\\b|otp|cvv|card\\s*number|\\bupi\\b", I).containsMatchIn(fact))
                return "I won't store passwords, PINs, OTPs or card details. Please keep those to yourself, boss."
            MemoryStore.add(ctx, fact)
            return "Okay, I'll remember that: $fact"
        }
        // recall: "what is my mom's number"
        ask.find(t)?.let { m ->
            val words = tokens(m.groupValues[1])
            if (words.isEmpty()) return null
            val best = MemoryStore.all(ctx)
                .map { f -> f to words.count { it in f.text.lowercase() } }
                .filter { it.second > 0 && it.second * 2 >= words.size }
                .maxByOrNull { it.second }
            if (best != null) return best.first.text + "."
        }
        return null
    }
}
