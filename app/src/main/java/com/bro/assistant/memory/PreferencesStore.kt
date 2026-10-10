package com.bro.assistant.memory

import android.content.Context

/** Simple settings storage. The API key lives only on this phone, never in the source code. */
class PreferencesStore(context: Context) {

    val appContext: Context = context.applicationContext

    private val sp = context.applicationContext.getSharedPreferences("bro_prefs", Context.MODE_PRIVATE)

    private fun str(key: String, def: String): String = sp.getString(key, def) ?: def

    var apiKey: String
        get() = str("api_key", "")
        set(value) { sp.edit().putString("api_key", value.trim()).apply() }

    var githubToken: String
        get() = str("github_token", "")
        set(value) { sp.edit().putString("github_token", value.trim()).apply() }

    /** "auto" = Gemini when online, offline model as backup; "offline" = only the offline model. */
    var brainMode: String
        get() = str("brain_mode", "auto")
        set(value) { sp.edit().putString("brain_mode", value).apply() }

    var localModelName: String
        get() = str("local_model_name", "")
        set(value) { sp.edit().putString("local_model_name", value).apply() }

    /** True when BRO has some AI to think with: a Gemini key or an installed offline model. */
    fun hasBrain(): Boolean = apiKey.isNotBlank() || com.bro.assistant.ai.LocalLlm.isInstalled(appContext)

    var model: String
        get() = str("model", "gemini-2.5-flash")
        set(value) { sp.edit().putString("model", value.trim()).apply() }

    var wakePhrases: String
        get() = str("wake_phrases", "hey bro, bro wake up")
        set(value) { sp.edit().putString("wake_phrases", value.trim()).apply() }

    var countryCode: String
        get() = str("country_code", "91")
        set(value) { sp.edit().putString("country_code", value.filter { it.isDigit() }).apply() }

    var language: String
        get() = str("language", "en-IN")
        set(value) { sp.edit().putString("language", value.trim()).apply() }

    var voiceName: String
        get() = str("voice_name", "")
        set(value) { sp.edit().putString("voice_name", value).apply() }

    var confirmBeforeSend: Boolean
        get() = sp.getBoolean("confirm_send", true)
        set(value) { sp.edit().putBoolean("confirm_send", value).apply() }

    var speakReplies: Boolean
        get() = sp.getBoolean("speak_replies", true)
        set(value) { sp.edit().putBoolean("speak_replies", value).apply() }

    var wakeEnabled: Boolean
        get() = sp.getBoolean("wake_enabled", false)
        set(value) { sp.edit().putBoolean("wake_enabled", value).apply() }

    fun wakePhraseList(): List<String> =
        wakePhrases.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
}
