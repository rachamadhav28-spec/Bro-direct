package com.bro.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.bro.assistant.memory.PreferencesStore

/** Speech-to-text using Android's built-in SpeechRecognizer. Must be used from the main thread. */
class SpeechInput(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onProblem: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private val prefs = PreferencesStore(context)

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onProblem("Speech recognition isn't available on this phone.")
            return
        }
        destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) = safely {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) onPartial(text)
            }

            override fun onResults(results: Bundle?) = safely {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) onProblem("I didn't catch that. Tap the mic and try again.")
                else onFinal(text)
            }

            override fun onError(error: Int) = safely {
                when (error) {
                    SpeechRecognizer.ERROR_CLIENT -> {} // we cancelled it ourselves
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        onProblem("I didn't hear anything. Tap the mic and try again.")
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        onProblem("Speech recognition needs an internet connection on this phone.")
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        onProblem("The microphone permission is missing.")
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        onProblem("The recognizer is busy. Try again in a moment.")
                    SpeechRecognizer.ERROR_AUDIO ->
                        onProblem("There was a problem with the microphone.")
                    else -> onProblem("Speech recognition failed (code $error).")
                }
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, prefs.language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        r.startListening(intent)
    }

    private fun safely(block: () -> Unit) {
        try { block() } catch (e: Exception) {
            com.bro.assistant.ErrorHandler.log("SpeechInput", "callback failed", e)
        }
    }

    fun stop() {
        recognizer?.cancel()
        destroy()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}
