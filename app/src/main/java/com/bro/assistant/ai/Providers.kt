package com.bro.assistant.ai

import com.bro.assistant.AiException
import com.bro.assistant.ErrorHandler
import com.bro.assistant.memory.PreferencesStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** What kind of job a piece of work is. The router picks the best free AI for each kind. */
enum class Skill(val label: String) {
    CODE("coding"), FAST("quick answer"), LONG("long text"), REASON("thinking"), GENERAL("general")
}

/**
 * The 6 free AI services BRO can use. Keys are typed in Settings and stored encrypted on the phone,
 * never in the source code (this repository is public).
 */
enum class Provider(
    val id: String,
    val label: String,
    val signup: String,
    val url: String,
    val general: List<String>,
    val code: List<String>,
    val strong: String
) {
    GEMINI(
        "gemini", "Gemini", "https://aistudio.google.com/apikey", "",
        emptyList(), emptyList(), "long text, images, big context"
    ),
    GROQ(
        "groq", "Groq", "https://console.groq.com/keys",
        "https://api.groq.com/openai/v1/chat/completions",
        listOf("openai/gpt-oss-120b", "llama-3.3-70b-versatile", "llama-3.1-8b-instant"),
        emptyList(), "fastest answers, quick commands"
    ),
    NVIDIA(
        "nvidia", "NVIDIA NIM", "https://build.nvidia.com/settings/api-keys",
        "https://integrate.api.nvidia.com/v1/chat/completions",
        listOf("openai/gpt-oss-120b", "meta/llama-3.3-70b-instruct", "nvidia/llama-3.3-nemotron-super-49b-v1"),
        emptyList(), "biggest daily allowance, thinking tasks"
    ),
    OPENROUTER(
        "openrouter", "OpenRouter", "https://openrouter.ai/keys",
        "https://openrouter.ai/api/v1/chat/completions",
        listOf("openrouter/free", "meta-llama/llama-3.3-70b-instruct:free"),
        listOf("qwen/qwen3-coder:free", "openrouter/free"), "many free models, spare key"
    ),
    GITHUB(
        "githubmodels", "GitHub Models", "https://github.com/settings/personal-access-tokens/new",
        "https://models.github.ai/inference/chat/completions",
        listOf("openai/gpt-4.1", "openai/gpt-4o-mini"),
        emptyList(), "strong GPT models, uses a GitHub token with Models access"
    ),
    CLOUDFLARE(
        "cloudflare", "Cloudflare AI", "https://dash.cloudflare.com/profile/api-tokens",
        "https://api.cloudflare.com/client/v4/accounts/{ACCOUNT}/ai/v1/chat/completions",
        listOf("@cf/openai/gpt-oss-120b", "@cf/meta/llama-3.3-70b-instruct-fp8-fast"),
        emptyList(), "small jobs; key format is ACCOUNTID:TOKEN"
    );
}

/** Sends one request to a provider that speaks the OpenAI chat format (everything except Gemini). */
object MultiAi {

    suspend fun call(
        prefs: PreferencesStore, p: Provider, skill: Skill,
        system: String, user: String, json: Boolean, timeoutMs: Int
    ): String {
        val key = prefs.providerKey(p.id)
        if (key.isBlank()) throw AiException(401, "no key for ${p.label}")
        val remembered = prefs.providerModel(p.id)
        val models = buildList {
            if (skill == Skill.CODE) addAll(p.code)
            if (remembered.isNotBlank() && skill != Skill.CODE) add(remembered)
            addAll(p.general)
        }.distinct()
        var last: AiException? = null
        for (m in models) {
            try {
                val out = once(p, key, m, system, user, json, timeoutMs)
                if (skill != Skill.CODE && m != remembered) prefs.setProviderModel(p.id, m)
                return out
            } catch (e: AiException) {
                last = e
                // wrong or retired model name: try the next one; any other problem goes to the router
                if (e.code == 404 || e.code == 400 || e.code == 422) continue
                throw e
            }
        }
        throw last ?: AiException(502, "${p.label}: no model to try")
    }

    private suspend fun once(
        p: Provider, key: String, model: String,
        system: String, user: String, json: Boolean, timeoutMs: Int
    ): String = withContext(Dispatchers.IO) {
        var token = key
        var url = p.url
        if (p == Provider.CLOUDFLARE) {
            val i = key.indexOf(':')
            if (i < 1) throw AiException(401, "Cloudflare key must look like ACCOUNTID:TOKEN")
            url = url.replace("{ACCOUNT}", key.substring(0, i).trim())
            token = key.substring(i + 1).trim()
        }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = timeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (p == Provider.OPENROUTER) {
                conn.setRequestProperty("HTTP-Referer", "https://github.com/rachamadhav28-spec")
                conn.setRequestProperty("X-Title", "BRO")
            }
            val body = JSONObject()
                .put("model", model)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", system))
                        .put(JSONObject().put("role", "user").put("content", user))
                )
                .put("temperature", if (json) 0.2 else 0.3)
                .put("max_tokens", if (timeoutMs > 60000) 8000 else if (json) 2048 else 4096)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw AiException(
                code,
                "${p.label}; model=$model; " + (runCatching {
                    JSONObject(text).getJSONObject("error").getString("message")
                }.getOrDefault(text)).take(120)
            )
            val content = JSONObject(text).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content", "")
                .replace(Regex("(?s)<think>.*?</think>"), "").trim()
            if (content.isBlank()) throw AiException(502, "${p.label}; model=$model; empty answer")
            content
        } finally {
            conn.disconnect()
        }
    }
}

/** Chooses which free AI does which kind of job, and moves to the next one when a key is used up. */
object Router {

    // Best first. Order comes from what each free service is good at.
    private val order: Map<Skill, List<Provider>> = mapOf(
        Skill.CODE to listOf(Provider.OPENROUTER, Provider.GITHUB, Provider.NVIDIA, Provider.GEMINI, Provider.GROQ, Provider.CLOUDFLARE),
        Skill.FAST to listOf(Provider.GROQ, Provider.GEMINI, Provider.NVIDIA, Provider.OPENROUTER, Provider.GITHUB, Provider.CLOUDFLARE),
        Skill.LONG to listOf(Provider.GEMINI, Provider.OPENROUTER, Provider.NVIDIA, Provider.GITHUB, Provider.GROQ, Provider.CLOUDFLARE),
        Skill.REASON to listOf(Provider.NVIDIA, Provider.GEMINI, Provider.GROQ, Provider.GITHUB, Provider.OPENROUTER, Provider.CLOUDFLARE),
        Skill.GENERAL to listOf(Provider.GROQ, Provider.GEMINI, Provider.NVIDIA, Provider.GITHUB, Provider.OPENROUTER, Provider.CLOUDFLARE)
    )

    // a provider that said "too many requests" rests for a minute
    private val cooldown = HashMap<String, Long>()

    fun hasKey(prefs: PreferencesStore, p: Provider): Boolean =
        if (p == Provider.GEMINI) prefs.apiKey.isNotBlank() else prefs.providerKey(p.id).isNotBlank()

    fun keyedProviders(prefs: PreferencesStore): List<Provider> = Provider.entries.filter { hasKey(prefs, it) }

    fun available(prefs: PreferencesStore, skill: Skill, exclude: Set<Provider> = emptySet()): List<Provider> {
        val all = (order[skill] ?: emptyList()).filter { hasKey(prefs, it) && it !in exclude }
        val now = System.currentTimeMillis()
        val rested = all.filter { (cooldown[it.id] ?: 0L) <= now }
        return rested.ifEmpty { all }
    }

    suspend fun run(
        prefs: PreferencesStore, skill: Skill, system: String, user: String, json: Boolean, timeoutMs: Int,
        gemini: GeminiClient, exclude: Set<Provider> = emptySet()
    ): Pair<Provider, String> {
        var last: Exception? = null
        for (p in available(prefs, skill, exclude)) {
            try {
                val text = if (p == Provider.GEMINI) gemini.remoteOnly(system, user, json, timeoutMs)
                else MultiAi.call(prefs, p, skill, system, user, json, timeoutMs)
                return p to text
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ErrorHandler.log("Router", "${p.label} failed for ${skill.label}", e)
                if (e is AiException && e.code == 429) cooldown[p.id] = System.currentTimeMillis() + 60_000
                last = e
            }
        }
        throw last ?: AiException(401, "no AI key is set")
    }

    /** Short text for "ai status": which keys are set and who does what. */
    fun statusText(prefs: PreferencesStore): String {
        val have = keyedProviders(prefs)
        if (have.isEmpty()) return "No AI keys are set yet. Open Settings and add your free keys."
        return buildString {
            append("AI keys set (").append(have.size).append(" of ").append(Provider.entries.size).append("): ")
            append(have.joinToString(", ") { it.label }).append(".\n")
            val missing = Provider.entries.filter { it !in have }
            if (missing.isNotEmpty()) append("Missing: ").append(missing.joinToString(", ") { it.label }).append(".\n")
            append("\nWho does what right now:\n")
            for (s in Skill.entries) {
                val list = available(prefs, s)
                append("• ").append(s.label).append(": ")
                append(if (list.isEmpty()) "-" else list.take(3).joinToString(" → ") { it.label }).append("\n")
            }
        }.trim()
    }
}
