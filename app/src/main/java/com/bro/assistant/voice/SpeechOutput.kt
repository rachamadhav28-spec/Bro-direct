package com.bro.assistant.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.bro.assistant.ErrorHandler
import com.bro.assistant.memory.PreferencesStore
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Text-to-speech. Tries a male voice, otherwise lowers the pitch of the default voice. */
class SpeechOutput(context: Context, private val prefs: PreferencesStore) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val main = Handler(Looper.getMainLooper())
    private val callbacks = ConcurrentHashMap<String, Pair<(() -> Unit)?, (() -> Unit)?>>()
    private var counter = 0

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                main.post {
                    ready = true
                    configure()
                }
            }
        }
    }

    private fun configure() {
        val t = tts ?: return
        try {
            val wanted = Locale.forLanguageTag(prefs.language)
            val result = t.setLanguage(wanted)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                t.setLanguage(Locale.US)
            }
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    val id = utteranceId ?: return
                    main.post { callbacks[id]?.first?.invoke() }
                }

                override fun onDone(utteranceId: String?) = finish(utteranceId)

                override fun onError(utteranceId: String?) = finish(utteranceId)

                override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    if (utteranceId != null) callbacks.remove(utteranceId)
                }
            })
            applyVoice()
        } catch (e: Exception) {
            ErrorHandler.log("SpeechOutput", "setup failed", e)
        }
    }

    private fun finish(utteranceId: String?) {
        val id = utteranceId ?: return
        main.post { callbacks.remove(id)?.second?.invoke() }
    }

    private fun applyVoice() {
        val t = tts ?: return
        try {
            val wanted = prefs.voiceName
            val voice: Voice? =
                if (wanted.isNotBlank()) t.voices?.firstOrNull { it.name == wanted } else guessMale(t)
            if (voice != null) t.setVoice(voice)
            t.setPitch(if (voice != null && wanted.isNotBlank()) 1.0f else 0.8f)
            t.setSpeechRate(1.0f)
        } catch (e: Exception) {
            ErrorHandler.log("SpeechOutput", "voice setup failed", e)
        }
    }

    private fun guessMale(t: TextToSpeech): Voice? {
        val lang = Locale.forLanguageTag(prefs.language).language
        return t.voices?.firstOrNull {
            it.locale.language == lang &&
                it.name.contains("male", ignoreCase = true) &&
                !it.name.contains("female", ignoreCase = true)
        }
    }

    fun voiceNames(): List<String> {
        val t = tts ?: return emptyList()
        return try {
            val lang = Locale.forLanguageTag(prefs.language).language
            t.voices?.filter { it.locale.language == lang }?.map { it.name }?.sorted() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Cycles to the next installed voice, saves it, and returns its name ("" if none). */
    fun nextVoice(): String {
        val names = voiceNames()
        if (names.isEmpty()) return ""
        val index = names.indexOf(prefs.voiceName)
        val next = names[(index + 1) % names.size]
        prefs.voiceName = next
        applyVoice()
        return next
    }

    fun speak(
        text: String,
        flush: Boolean = true,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null
    ) {
        val t = tts
        if (!ready || t == null) {
            if (onDone != null) main.post { onDone() }
            return
        }
        val id = "bro_${counter++}"
        callbacks[id] = Pair(onStart, onDone)
        t.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
