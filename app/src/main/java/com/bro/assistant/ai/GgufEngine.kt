package com.bro.assistant.ai

import android.app.ActivityManager
import android.content.Context
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig

/** A loaded .gguf model. The library's own model type stays hidden inside these two functions. */
class GgufHandle internal constructor(
    val path: String,
    private val completeFn: suspend (String, String, Int) -> String,
    private val releaseFn: suspend () -> Unit
) {
    suspend fun complete(system: String, user: String, maxTokens: Int): String = completeFn(system, user, maxTokens)
    suspend fun release() = releaseFn()
}

/**
 * Runs a GGUF model (for example Qwen2.5-Coder) inside the app with llama.cpp: no server, no internet.
 * It uses the phone's CPU, so it is slow; the model has to fit in memory, which is checked before anything is loaded.
 */
object GgufEngine {

    /** Prompt + answer must fit in this many tokens. */
    const val CONTEXT_TOKENS = 8192

    private fun gb(bytes: Long) = String.format(java.util.Locale.US, "%.1f GB", bytes / 1e9)

    fun totalRamBytes(ctx: Context): Long {
        val am = ctx.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem
    }

    /** Null when the file can run on this phone, otherwise why it cannot (a model larger than about 80% of the memory gets the app killed). */
    fun fitProblem(ctx: Context, fileBytes: Long): String? {
        if (fileBytes <= 0L) return null
        val ram = totalRamBytes(ctx)
        // the file is memory-mapped, so a file a little bigger than the memory still loads (slowly); far bigger would get the app killed
        if (fileBytes <= ram * 13 / 10) return null
        return "This model file is ${gb(fileBytes)} but this phone has only ${gb(ram)} of memory, so it cannot run it. " +
            "Choose a smaller GGUF, for example Qwen2.5-Coder 3B q4_k_m (2.1 GB), or the 7B q4_k_m (4.7 GB) on an 8 GB phone."
    }

    /** A warning (not a refusal) when the model will fit but leave little room. */
    fun tightWarning(ctx: Context, fileBytes: Long): String? {
        if (fileBytes <= 0L) return null
        val ram = totalRamBytes(ctx)
        if (fileBytes <= ram * 6 / 10) return null
        return "Heads up: ${gb(fileBytes)} on a ${gb(ram)} phone is tight. Close other apps; answers will be slow."
    }

    /** A smaller window when the model already fills most of the memory (the window itself costs memory). */
    fun contextFor(ctx: Context, fileBytes: Long): Int =
        if (fileBytes > totalRamBytes(ctx) * 6 / 10) 4096 else CONTEXT_TOKENS

    private fun defaultThreads(): Int = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 6)

    suspend fun open(path: String, contextSize: Int = CONTEXT_TOKENS): GgufHandle {
        val model = Llama.loadModel(
            modelPath = path,
            config = LlamaConfig(contextSize = contextSize, threads = defaultThreads())
        )
        return GgufHandle(
            path,
            { system, user, maxTokens -> "${Llama.complete(model, prompt = user, systemPrompt = system, maxTokens = maxTokens).text}".trim() },
            { Llama.releaseModel(model) }
        )
    }
}
