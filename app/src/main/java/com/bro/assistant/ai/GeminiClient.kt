package com.bro.assistant.ai

import com.bro.assistant.AiException
import com.bro.assistant.memory.PreferencesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    /**
     * Picks the brain: the offline model when it is installed and chosen (or there is no API key),
     * otherwise Gemini, with the offline model as backup when there is no internet or Gemini fails.
     */
    suspend fun generate(
        system: String,
        user: String,
        json: Boolean = true,
        timeoutMs: Int = if (json) 30000 else 90000
    ): String {
        if (LocalLlm.willUse(prefs)) return LocalLlm.generate(prefs, system, user, json)
        try {
            return generateRemote(system, user, json, timeoutMs)
        } catch (e: java.io.IOException) {
            if (LocalLlm.isInstalled(prefs.appContext)) return LocalLlm.generate(prefs, system, user, json)
            throw e
        } catch (e: AiException) {
            if (LocalLlm.isInstalled(prefs.appContext)) return LocalLlm.generate(prefs, system, user, json)
            throw e
        }
    }

    /** Retries when Google says it is overloaded (429, 500, 502, 503, 504). */
    private suspend fun generateRemote(
        system: String,
        user: String,
        json: Boolean,
        timeoutMs: Int
    ): String {
        val waits = longArrayOf(2000, 6000, 15000)
        var attempt = 0
        var modelSwitched = false
        while (true) {
            try {
                return generateOnce(system, user, json, timeoutMs)
            } catch (e: AiException) {
                // Google retired this model for this key: pick one that works and try again.
                if (e.code == 404 && !modelSwitched && switchModel()) { modelSwitched = true; continue }
                if (e.code == 400 && !prefs.thinkingUnsupported && e.detail.contains("think", ignoreCase = true)) {
                    prefs.thinkingUnsupported = true; continue
                }
                if (e.code !in listOf(429, 500, 502, 503, 504) || attempt >= waits.size) throw e
                delay(waits[attempt++])
            }
        }
    }

    /** Asks Google which models this key can use and saves the best fast one. */
    private suspend fun switchModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("x-goog-api-key", prefs.apiKey)
            val text = try {
                if (conn.responseCode !in 200..299) return@withContext false
                conn.inputStream.bufferedReader().readText()
            } finally { conn.disconnect() }
            val arr = JSONObject(text).optJSONArray("models") ?: return@withContext false
            val names = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val methods = m.optJSONArray("supportedGenerationMethods") ?: continue
                if ((0 until methods.length()).none { methods.getString(it) == "generateContent" }) continue
                val n = m.getString("name").removePrefix("models/")
                if (n.startsWith("gemini") && !n.contains("embed") && !n.contains("image") &&
                    !n.contains("tts") && !n.contains("live") && !n.contains("audio") &&
                    !n.contains("robotics") && !n.contains("computer-use")
                ) names.add(n)
            }
            fun ver(n: String) = Regex("gemini-(\\d+(?:\\.\\d+)?)").find(n)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            // newest version first; among those prefer plain "flash", then other flash, then the rest
            val best = names.sortedWith(
                compareByDescending<String> { ver(it) }
                    .thenBy { if (it.endsWith("-flash")) 0 else if (it.contains("flash") && !it.contains("lite")) 1 else if (it.contains("flash")) 2 else 3 }
                    .thenBy { if (it.contains("preview") || it.contains("exp")) 1 else 0 }
            ).firstOrNull() ?: return@withContext false
            if (best == prefs.model) return@withContext false
            prefs.model = best
            true
        } catch (e: Exception) { false }
    }

    private suspend fun generateOnce(
        system: String,
        user: String,
        json: Boolean,
        timeoutMs: Int
    ): String = withContext(Dispatchers.IO) {
        // Big answers stream in pieces so the connection never sits silent until the timeout.
        val streaming = timeoutMs > 60000
        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/${prefs.model}:" +
                if (streaming) "streamGenerateContent?alt=sse" else "generateContent"
        )
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
                        put("maxOutputTokens", if (json) 1024 else 32768)
                        // quick commands do not need long "thinking": skip it for speed (retried without if unsupported)
                        if (json && !prefs.thinkingUnsupported) {
                            put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                        }
                    }
                )

            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (streaming && code in 200..299) {
                val sb = StringBuilder()
                conn.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data.isEmpty()) continue
                        val parts = JSONObject(data).optJSONArray("candidates")?.optJSONObject(0)
                            ?.optJSONObject("content")?.optJSONArray("parts") ?: continue
                        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
                    }
                }
                if (sb.isBlank()) throw AiException(502, "model=${prefs.model}; empty answer")
                return@withContext sb.toString()
            }
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
