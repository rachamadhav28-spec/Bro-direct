package com.bro.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Repeatedly listens with SpeechRecognizer and fires [onWake] when a wake phrase is heard.
 * This is not a true always-on wake-word engine: it restarts the recognizer in a loop,
 * which uses battery and may make a small sound on some phones. Main thread only.
 */
class WakeWordListener(
    private val context: Context,
    private val phrases: () -> List<String>,
    private val onWake: () -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var active = false

    fun start() {
        if (active) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        active = true
        listen()
    }

    fun stop() {
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
    }

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9\\u0900-\\u097F ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun matches(text: String): Boolean {
        val heard = normalize(text)
        return phrases().any { p ->
            val n = normalize(p)
            n.isNotEmpty() && heard.contains(n)
        }
    }

    private fun handle(text: String?): Boolean {
        if (text.isNullOrBlank() || !matches(text)) return false
        stop()
        onWake()
        return true
    }

    private fun restart(delayMs: Long) {
        if (!active) return
        handler.postDelayed({ listen() }, delayMs)
    }

    private fun listen() {
        if (!active) return
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                handle(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!handle(text)) restart(300)
            }

            override fun onError(error: Int) {
                val wait = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 2000L else 700L
                restart(wait)
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        r.startListening(intent)
    }
}
