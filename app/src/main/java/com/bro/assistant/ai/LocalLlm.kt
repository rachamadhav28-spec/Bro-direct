package com.bro.assistant.ai

import android.content.Context
import com.bro.assistant.AiException
import com.bro.assistant.ErrorHandler
import com.bro.assistant.memory.PreferencesStore
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * BRO's own brain: a small language model that runs on the phone (MediaPipe LLM Inference), with no
 * internet and no API key. The model file (.task) is installed once from Settings.
 */
object LocalLlm {

    private var engine: LlmInference? = null
    private var enginePath: String? = null
    private var contextTokens = 0
    private val lock = Mutex()

    fun modelFile(context: Context): File =
        File(context.applicationContext.getExternalFilesDir(null) ?: context.applicationContext.filesDir, "offline_model.task")

    fun isInstalled(context: Context): Boolean = modelFile(context).let { it.exists() && it.length() > 1_000_000L }

    /** True when the next request will be answered by the offline model. */
    fun willUse(prefs: PreferencesStore): Boolean =
        isInstalled(prefs.appContext) && (prefs.brainMode == "offline" || prefs.apiKey.isBlank())

    fun release() {
        try { engine?.close() } catch (_: Exception) {}
        engine = null
        enginePath = null
    }

    private fun load(context: Context): LlmInference {
        val file = modelFile(context)
        engine?.let { if (enginePath == file.absolutePath) return it }
        release()
        var last: Exception? = null
        for (tokens in listOf(4096, 2048, 1280)) {
            try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(file.absolutePath)
                    .setMaxTokens(tokens)
                    .build()
                val e = LlmInference.createFromOptions(context.applicationContext, options)
                engine = e; enginePath = file.absolutePath; contextTokens = tokens
                return e
            } catch (e: Exception) {
                last = e
                ErrorHandler.log("LocalLlm", "load with $tokens tokens failed", e)
            }
        }
        throw IllegalStateException("The offline model could not start: ${last?.message ?: "unknown error"}. " +
            "It may be too big for this phone or not a MediaPipe .task file.")
    }

    suspend fun generate(prefs: PreferencesStore, system: String, user: String, json: Boolean): String =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val ctx = prefs.appContext
                val e = load(ctx)
                val reserve = if (json) 400 else 900
                val budgetChars = ((contextTokens - reserve).coerceAtLeast(300)) * 3
                val sys = if (json) system + "\nReply with ONE valid JSON object and nothing else." else system
                var body = user
                val room = budgetChars - sys.length
                if (room < 200) throw IllegalStateException("The offline model's memory is too small for this request.")
                if (body.length > room) {
                    // keep the beginning (goal/context) and the end (latest user text / screen)
                    val head = room * 2 / 5
                    body = body.take(head) + "\n...\n" + body.takeLast(room - head - 5)
                }
                val raw = e.generateResponse(template(prefs.localModelName, sys, body)).orEmpty()
                if (raw.isBlank()) throw AiException(502, "offline model gave an empty answer")
                if (!json) raw else asJson(raw)
            }
        }

    private fun template(name: String, system: String, user: String): String {
        val n = name.lowercase()
        return when {
            n.contains("gemma") -> "<start_of_turn>user\n$system\n\n$user<end_of_turn>\n<start_of_turn>model\n"
            n.contains("llama") ->
                "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n$system<|eot_id|>" +
                    "<|start_header_id|>user<|end_header_id|>\n\n$user<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"
            else -> "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n$user<|im_end|>\n<|im_start|>assistant\n"
        }
    }

    /** Pulls the JSON object out of the model's answer; plain text becomes a chat reply. */
    private fun asJson(raw: String): String {
        val a = raw.indexOf('{')
        val b = raw.lastIndexOf('}')
        if (a >= 0 && b > a) {
            val candidate = raw.substring(a, b + 1)
            if (runCatching { JSONObject(candidate) }.isSuccess) return candidate
        }
        return JSONObject().put("reply", raw.trim().take(800)).put("actions", org.json.JSONArray()).toString()
    }
}
