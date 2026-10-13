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

    /** Every Gemini key the owner entered (separated by commas, spaces or new lines). */
    fun apiKeyList(): List<String> = apiKey.split(Regex("[,;\\s]+")).map { it.trim() }.filter { it.length >= 20 }.distinct()

    /** Keys for the other free AI services (Groq, NVIDIA, ...). Stored encrypted with the phone's keystore. */
    fun providerKey(id: String): String {
        val stored = str("pk_$id", "")
        if (stored.isBlank()) return ""
        return com.bro.assistant.security.SecretBox.decrypt(stored) ?: ""
    }

    fun setProviderKey(id: String, value: String) {
        val t = value.trim()
        sp.edit().putString("pk_$id", if (t.isEmpty()) "" else com.bro.assistant.security.SecretBox.encrypt(t)).apply()
    }

    /** The model name that last worked for this provider. */
    fun providerModel(id: String): String = str("pm_$id", "")
    fun setProviderModel(id: String, value: String) { sp.edit().putString("pm_$id", value).apply() }

    fun hasProviderKeys(): Boolean =
        com.bro.assistant.ai.Provider.entries.any { it != com.bro.assistant.ai.Provider.GEMINI && providerKey(it.id).isNotBlank() }

    var githubToken: String
        get() = str("github_token", "")
        set(value) { sp.edit().putString("github_token", value.trim()).apply() }

    /** "auto" = Gemini when online, offline model as backup; "offline" = only the offline model. */
    var brainMode: String
        get() = str("brain_mode", "auto")
        set(value) { sp.edit().putString("brain_mode", value).apply() }

    /** Set when the chosen Gemini model rejects the "no thinking" option. */
    var thinkingUnsupported: Boolean
        get() = sp.getBoolean("thinking_unsupported", false)
        set(value) { sp.edit().putBoolean("thinking_unsupported", value).apply() }

    var localModelName: String
        get() = str("local_model_name", "")
        set(value) { sp.edit().putString("local_model_name", value).apply() }

    /** True when BRO has some AI to think with: a Gemini key or an installed offline model. */
    fun hasBrain(): Boolean = apiKey.isNotBlank() || hasProviderKeys() || com.bro.assistant.ai.LocalLlm.isInstalled(appContext)

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

    /** Ask for fingerprint / screen lock when BRO opens (only used when the phone has one set up). */
    var appLock: Boolean
        get() = sp.getBoolean("app_lock", true)
        set(value) { sp.edit().putBoolean("app_lock", value).apply() }

    /** Also allow fingerprint on the BRO lock screen (the BRO PIN/password/pattern stays the main key). */
    var lockBiometric: Boolean
        get() = sp.getBoolean("lock_biometric", false)
        set(value) { sp.edit().putBoolean("lock_biometric", value).apply() }

    /** The Gmail the owner signed in with when BRO first opened. */
    var accountEmail: String
        get() = str("account_email", "")
        set(value) { sp.edit().putString("account_email", value.trim()).apply() }

    /** The Google app password, encrypted with the phone's keystore (see SecretBox). */
    var mailSecret: String
        get() = str("mail_secret", "")
        set(value) { sp.edit().putString("mail_secret", value).apply() }

    /** Show the always-on floating circle over other apps. */
    var bubbleEnabled: Boolean
        get() = sp.getBoolean("bubble_enabled", false)
        set(value) { sp.edit().putBoolean("bubble_enabled", value).apply() }

    /** City used for the weather in the morning briefing. */
    var city: String
        get() = str("city", "")
        set(value) { sp.edit().putString("city", value.trim()).apply() }

    /** BRO studies by itself at night (12 to 3). */
    var learnNightly: Boolean
        get() = sp.getBoolean("learn_nightly", true)
        set(value) { sp.edit().putBoolean("learn_nightly", value).apply() }
    /** Theme colours (ARGB as unsigned long) and the moving HUD background. */
    var accentPrimary: Long
        get() = sp.getLong("accent_primary", 0xFF00E5FFL)
        set(value) { sp.edit().putLong("accent_primary", value).apply() }
    var accentSecondary: Long
        get() = sp.getLong("accent_secondary", 0xFF9D6BFFL)
        set(value) { sp.edit().putLong("accent_secondary", value).apply() }
    var hudAnimation: Boolean
        get() = sp.getBoolean("hud_animation", true)
        set(value) { sp.edit().putBoolean("hud_animation", value).apply() }

    /** Study non-stop until stopped (restarts after the phone restarts). */
    var studyNonStop: Boolean
        get() = sp.getBoolean("study_nonstop", false)
        set(value) { sp.edit().putBoolean("study_nonstop", value).apply() }
    var lastLearned: Long
        get() = sp.getLong("last_learned", 0L)
        set(value) { sp.edit().putLong("last_learned", value).apply() }

    /** BRO may speak up by itself (battery, calendar events). */
    var proactiveEnabled: Boolean
        get() = sp.getBoolean("proactive_enabled", false)
        set(value) { sp.edit().putBoolean("proactive_enabled", value).apply() }

    /** Read new messages aloud when the phone is awake (needs notification access). */
    var readMessages: Boolean
        get() = sp.getBoolean("read_messages", false)
        set(value) { sp.edit().putBoolean("read_messages", value).apply() }

    /** Part of your car's Bluetooth name: drive mode starts when it connects. */
    var driveBtName: String
        get() = str("drive_bt", "")
        set(value) { sp.edit().putString("drive_bt", value.trim()).apply() }

    // ---- "BRO calls me" (your own Twilio account) ----
    var twilioSid: String
        get() = str("twilio_sid", "")
        set(value) { sp.edit().putString("twilio_sid", value.trim()).apply() }
    /** Stored encrypted with the phone's keystore (see SecretBox). */
    var twilioToken: String
        get() = str("twilio_token", "")
        set(value) { sp.edit().putString("twilio_token", value).apply() }
    var twilioFrom: String
        get() = str("twilio_from", "")
        set(value) { sp.edit().putString("twilio_from", value.trim()).apply() }
    var myNumber: String
        get() = str("my_number", "")
        set(value) { sp.edit().putString("my_number", value.trim()).apply() }
    var callVoice: String
        get() = str("call_voice", "")
        set(value) { sp.edit().putString("call_voice", value.trim()).apply() }
    /** Reminders ring like an incoming BRO call inside the app (you can answer and talk). */
    var reminderRings: Boolean
        get() = sp.getBoolean("reminder_rings", true)
        set(value) { sp.edit().putBoolean("reminder_rings", value).apply() }

    /** "call me" phones through Twilio (paid, one-way) only when this is on; otherwise it rings in the app (free, two-way). */
    var callViaTwilio: Boolean
        get() = sp.getBoolean("call_via_twilio", false)
        set(value) { sp.edit().putBoolean("call_via_twilio", value).apply() }

    var callForReminders: Boolean
        get() = sp.getBoolean("call_for_reminders", false)
        set(value) { sp.edit().putBoolean("call_for_reminders", value).apply() }

    var wakeEnabled: Boolean
        get() = sp.getBoolean("wake_enabled", false)
        set(value) { sp.edit().putBoolean("wake_enabled", value).apply() }

    fun wakePhraseList(): List<String> =
        wakePhrases.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
}
