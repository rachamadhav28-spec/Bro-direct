package com.bro.assistant.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.bro.assistant.memory.PreferencesStore
import kotlinx.coroutines.delay

/** "Install WhatsApp from Play Store": opens Google Play on the search results and lets the phone agent pick the right app and tap Install. */
object PlayInstall {
    private val rx = Regex(
        "^(?:please\\s+|can\\s+you\\s+)?(?:install|download|get\\s+me|add)\\s+(?:the\\s+|an?\\s+)?(?:app\\s+)?(.{2,60}?)" +
            "(?:\\s+(?:app|application))?(?:\\s+(?:from|on|in|using|via)\\s+(?:the\\s+)?(?:google\\s+)?play\\s*store)?(?:\\s+(?:for\\s+me|please))?$",
        RegexOption.IGNORE_CASE
    )
    private val notApp = Regex("\\b(?:apk|repo|repository|github|pdf|file|update|updates|model|brain|offline)\\b", RegexOption.IGNORE_CASE)

    fun nameOf(text: String): String? {
        val m = rx.matchEntire(text.trim().trimEnd('.', '!', '?')) ?: return null
        val name = m.groupValues[1].trim()
        if (name.length < 2 || notApp.containsMatchIn(name)) return null
        // "download the apk of x" / "install from github" are handled elsewhere
        if (Regex("\\b(?:github|apk)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
        return name
    }

    suspend fun handle(c: Context, prefs: PreferencesStore, text: String, confirm: suspend (String) -> Boolean): String? {
        val name = nameOf(text) ?: return null
        if (!prefs.hasBrain()) return "To install apps for you I need an AI: add a Gemini API key or the offline brain in Settings."
        if (!confirm("Install \"$name\" from Google Play? I'll pick the official app, and I will not buy anything.")) return "Okay, I didn't install it."
        com.bro.assistant.Activity.step("Opening Google Play")
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(name) + "&c=apps"))
                .setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(i)
        } catch (e: Exception) {
            return "I couldn't open Google Play. Is it installed and signed in?"
        }
        delay(2500)
        val goal = "Google Play Store is open on the search results for \"$name\". Find the right OFFICIAL app (matching name, correct developer, most downloads). " +
            "Open it and tap Install. If it only shows a price or Buy, or needs a purchase, stop with fail and say it is paid. " +
            "If it shows Open instead of Install the app is already installed: finish with done and say so. " +
            "If Google asks to verify it's you (fingerprint, password) finish with ask and tell the user to do that step. " +
            "Wait until the button changes to Open (use wait and look again), then finish with done: 'Installed $name (developer name)'. " +
            "Never sign in, never enter card or payment details."
        val r = AppAgent(c, prefs).run(goal, confirm) { }
        return r.message
    }
}
