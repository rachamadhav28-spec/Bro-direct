package com.bro.assistant.ai

import android.content.Context
import com.bro.assistant.AiException
import com.bro.assistant.ErrorHandler
import com.bro.assistant.memory.PreferencesStore
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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

    private fun dir(context: Context): File =
        context.applicationContext.getExternalFilesDir(null) ?: context.applicationContext.filesDir

    /** Gemma 4 and newer models come as .litertlm (LiteRT-LM); older ones as .task (MediaPipe). */
    fun fileNameFor(link: String): String {
        val l = link.substringBefore('?').lowercase()
        return when {
            l.endsWith(".gguf") -> "offline_model.gguf"          // llama.cpp models such as Qwen2.5-Coder
            l.endsWith(".litertlm") -> "offline_model.litertlm"
            else -> "offline_model.task"
        }
    }

    fun fileFor(context: Context, link: String): File = File(dir(context), fileNameFor(link))

    fun modelFile(context: Context): File {
        val gguf = File(dir(context), "offline_model.gguf")
        if (gguf.exists()) return gguf
        val lm = File(dir(context), "offline_model.litertlm")
        return if (lm.exists()) lm else File(dir(context), "offline_model.task")
    }

    /** Downloads and copies go to a ".part" file first, so a half-finished file is never used as the model. */
    fun partFile(context: Context, link: String): File = File(dir(context), fileNameFor(link) + ".part")

    fun finishPart(context: Context, link: String): Boolean {
        val part = partFile(context, link)
        if (!part.exists() || part.length() < 1_000_000L) return false
        val dest = fileFor(context, link)
        File(dir(context), "offline_model.litertlm").delete(); File(dir(context), "offline_model.task").delete(); File(dir(context), "offline_model.gguf").delete()
        return part.renameTo(dest)
    }

    /** Copy progress / result for the "Choose file" button (kept here so it survives screen changes). */
    var copyStatus by androidx.compose.runtime.mutableStateOf("")
    private val copyScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    fun copyFrom(context: Context, uri: android.net.Uri, prefs: PreferencesStore) {
        val app = context.applicationContext
        copyStatus = "Copying the model file... this can take a few minutes."
        copyScope.launch {
            try {
                val name = app.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && i >= 0) c.getString(i) else null
                } ?: uri.lastPathSegment.orEmpty()
                if (name.lowercase().endsWith(".gguf")) {
                    val size = app.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val i = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (c.moveToFirst() && i >= 0 && !c.isNull(i)) c.getLong(i) else -1L
                    } ?: -1L
                    GgufEngine.fitProblem(app, size)?.let { copyStatus = it; return@launch }
                }
                release()
                val part = partFile(app, name)
                part.delete()
                app.contentResolver.openInputStream(uri)?.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(1 shl 20)
                        var total = 0L
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            out.write(buf, 0, n); total += n
                            if (total % (64L shl 20) < (1 shl 20)) copyStatus = "Copying... " + total / 1_000_000 + " MB"
                        }
                    }
                }
                prefs.localModelName = name
                copyStatus = if (finishPart(app, name)) {
                    "Offline brain installed." + (if (name.lowercase().endsWith(".gguf")) GgufEngine.tightWarning(app, modelFile(app).length())?.let { " $it" }.orEmpty() else "")
                } else "That file looks incomplete or is not a model file."
            } catch (e: Throwable) {
                ErrorHandler.log("LocalLlm", "copy failed", e)
                copyStatus = "Could not copy that file: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    /** Releases the model's memory once no answer is being generated. */
    suspend fun releaseWhenIdle() = withContext(Dispatchers.IO) { lock.withLock { release() } }

    fun deleteAll(context: Context) {
        release()
        File(dir(context), "offline_model.litertlm").delete()
        File(dir(context), "offline_model.task").delete()
        File(dir(context), "offline_model.gguf").delete()
        File(dir(context), "offline_model.litertlm.part").delete()
        File(dir(context), "offline_model.task.part").delete()
        File(dir(context), "offline_model.gguf.part").delete()
    }

    fun isInstalled(context: Context): Boolean = modelFile(context).let { it.exists() && it.length() > 1_000_000L }

    /** True when the next request will be answered by the offline model. */
    fun willUse(prefs: PreferencesStore): Boolean =
        isInstalled(prefs.appContext) && (prefs.brainMode == "offline" || (prefs.apiKey.isBlank() && !prefs.hasProviderKeys()))

    private var lmEngine: Engine? = null
    private var lmPath: String? = null
    private var ggufHandle: GgufHandle? = null

    fun release() {
        ggufHandle?.let { g -> ggufHandle = null; copyScope.launch { try { g.release() } catch (_: Throwable) {} } }
        try { engine?.close() } catch (_: Exception) {}
        try { lmEngine?.close() } catch (_: Exception) {}
        engine = null
        enginePath = null
        lmEngine = null
        lmPath = null
    }

    /** Loads a .gguf model with llama.cpp. Refuses a file that is too big for this phone's memory instead of crashing the app. */
    private suspend fun loadGguf(context: Context): GgufHandle {
        val file = modelFile(context)
        ggufHandle?.let { if (it.path == file.absolutePath) return it }
        release()
        GgufEngine.fitProblem(context, file.length())?.let { throw IllegalStateException(it) }
        try {
            val h = GgufEngine.open(file.absolutePath)
            ggufHandle = h; contextTokens = GgufEngine.CONTEXT_TOKENS
            return h
        } catch (t: Throwable) {
            ErrorHandler.log("LocalLlm", "GGUF load failed", t)
            throw IllegalStateException("The offline model could not start: ${t.message ?: t.javaClass.simpleName}. " +
                "It may need more free memory, or the file may be incomplete.")
        }
    }

    private fun loadLiteRt(context: Context): Engine {
        val file = modelFile(context)
        lmEngine?.let { if (lmPath == file.absolutePath) return it }
        release()
        try {
            val e = Engine(EngineConfig(modelPath = file.absolutePath, backend = Backend.CPU()))
            e.initialize()
            lmEngine = e; lmPath = file.absolutePath; contextTokens = 4096
            return e
        } catch (t: Throwable) {
            ErrorHandler.log("LocalLlm", "LiteRT-LM load failed", t)
            throw IllegalStateException("The offline model could not start: ${t.message ?: t.javaClass.simpleName}. " +
                "It may need more free memory, or the file may be incomplete.")
        }
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

    /** Loads the model in the background so the first answer is not slow. */
    suspend fun warmUp(prefs: PreferencesStore) = withContext(Dispatchers.IO) {
        try {
            lock.withLock {
                val ctx = prefs.appContext
                if (!isInstalled(ctx)) return@withLock
                val n = modelFile(ctx).name
                if (n.endsWith(".gguf")) loadGguf(ctx) else if (n.endsWith(".litertlm")) loadLiteRt(ctx) else load(ctx)
            }
        } catch (e: Exception) { ErrorHandler.log("LocalLlm", "warm-up failed", e) }
    }

    suspend fun generate(prefs: PreferencesStore, system: String, user: String, json: Boolean): String =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val ctx = prefs.appContext
                val modelName = modelFile(ctx).name
                val useGguf = modelName.endsWith(".gguf")
                val useLm = modelName.endsWith(".litertlm")
                if (useGguf) loadGguf(ctx) else if (useLm) loadLiteRt(ctx) else load(ctx)
                // the GGUF engine has room for a whole file of code in one answer
                val reserve = if (useGguf) (if (json) 600 else 3000) else if (json) 400 else 900
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
                val raw = if (useGguf) {
                    // llama.cpp applies the chat template stored inside the GGUF file (ChatML for Qwen)
                    ggufHandle!!.complete(sys, body, reserve)
                } else if (useLm) {
                    // LiteRT-LM applies the model's own chat template
                    lmEngine!!.createConversation().use { c -> c.sendMessage(sys + "\n\n" + body).toString() }
                } else {
                    engine!!.generateResponse(template(prefs.localModelName, sys, body)).orEmpty()
                }
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
