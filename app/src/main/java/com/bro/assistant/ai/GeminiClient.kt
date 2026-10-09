package com.bro.assistant.ai

import com.bro.assistant.AiException
import com.bro.assistant.memory.PreferencesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Calls the Google Gemini REST API (generateContent). The key comes from Settings and is sent
 * in a header; it is never written in the source code. For a public release, put the key on
 * a backend server instead of the phone.
 */
class GeminiClient(private val prefs: PreferencesStore) {

    suspend fun generate(
        system: String,
        user: String,
        json: Boolean = true,
        timeoutMs: Int = if (json) 30000 else 90000
    ): String = withContext(Dispatchers.IO) {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${prefs.model}:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = timeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", prefs.apiKey)

            val body = JSONObject()
                .put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))
                )
                .put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put("role", "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", user)))
                    )
                )
                .put(
                    "generationConfig",
                    JSONObject().apply {
                        if (json) put("responseMimeType", "application/json")
                        put("temperature", if (json) 0.2 else 0.3)
                        put("maxOutputTokens", 32768)
                    }
                )

            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw AiException(
                code,
                "model=${prefs.model}; " + (runCatching {
                    JSONObject(text).getJSONObject("error").getString("message")
                }.getOrDefault(text)).take(120)
            )

            JSONObject(text)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts").getJSONObject(0)
                .getString("text")
        } finally {
            conn.disconnect()
        }
    }
}
