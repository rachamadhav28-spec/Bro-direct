package com.bro.assistant

import android.util.Log
import org.json.JSONException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AiException(val code: Int, val detail: String = "") : Exception("AI HTTP $code")

object ErrorHandler {

    fun log(tag: String, message: String, t: Throwable? = null) {
        Log.e("BRO/$tag", message, t)
    }

    fun friendly(t: Throwable): String = when (t) {
        is UnknownHostException, is SocketTimeoutException ->
            "I couldn't reach the AI. Check your internet connection."
        is AiException -> when (t.code) {
            400 -> "The AI rejected the request. Check the model name and key in Settings."
            401, 403 -> "The AI key was rejected. Check your API key in Settings."
            404 -> "The AI model name was not found. Check the model name in Settings.${if (t.detail.isNotBlank()) " (${t.detail})" else ""}"
            429 -> "The AI is rate limited right now. Try again in a minute."
            else -> "The AI service returned error ${t.code}."
        }
        is JSONException -> "I couldn't understand the AI's answer. Try saying it differently."
        is SecurityException -> "Android blocked that action: ${t.message ?: "missing permission"}."
        else -> "Something went wrong: ${t.message ?: t.javaClass.simpleName}."
    }
}
