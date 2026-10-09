package com.bro.assistant

import com.bro.assistant.ai.GeminiClient
import com.bro.assistant.ai.GitHubClient
import com.bro.assistant.ai.GitHubException
import com.bro.assistant.memory.PreferencesStore
import org.json.JSONObject

/**
 * Builds real Android apps from a sentence: Gemini writes the Kotlin code, BRO adds the proven
 * Gradle scaffolding, pushes everything to a new GitHub repo, and GitHub Actions builds the APK.
 */
class AppBuilder(private val prefs: PreferencesStore, private val gh: GitHubClient) {

    private val gemini = GeminiClient(prefs)

    data class GenFile(val path: String, val content: String)
    data class Spec(val name: String, val repo: String, val files: List<GenFile>)

    // ---------- create ----------

    suspend fun create(description: String, repoOverride: String?, confirm: suspend (String) -> Boolean): String {
        val spec = generate(description)
        val repo = repoOverride ?: spec.repo
        val q = "Create repo \"$repo\" with the Android app \"${spec.name}\" (${spec.files.size} Kotlin files) " +
            "and start the APK build?"
        if (!confirm(q)) return "Okay, I didn't create it."

        try {
            gh.createRepo(repo, private = false, autoInit = true)
        } catch (e: GitHubException) {
            if (e.code != 422) throw e // 422 = already exists: reuse it
        }

        val owner = gh.login()
        val full = if ("/" in repo) repo else "$owner/$repo"
        val scaffold = AppTemplate.files(spec.name, AppTemplate.slug(repo))
        // code and scaffolding first, the workflow last so exactly one build starts
        for ((path, content) in scaffold.filter { it.first != AppTemplate.WORKFLOW_PATH }) {
            gh.putFile(full, path, content, "Add $path via BRO")
        }
        for (f in spec.files) gh.putFile(full, f.path, f.content, "Add ${f.path.substringAfterLast('/')} via BRO")
        val wf = scaffold.first { it.first == AppTemplate.WORKFLOW_PATH }
        gh.putFile(full, wf.first, wf.second, "Add build workflow via BRO")

        return "Done. Repo: https://github.com/$full\n" +
            "The build takes about 2 minutes. Then say \"build status of ${full.substringAfter('/')}\", " +
            "and \"download apk of ${full.substringAfter('/')}\"."
    }

    private suspend fun generate(description: String): Spec {
        val raw = gemini.generate(CREATE_PROMPT, "Build this app: $description", json = true, timeoutMs = 150000)
        val obj = parseJson(raw)
        val files = readFiles(obj)
        val name = obj.optString("app_name").ifBlank { "My App" }.take(40)
        val repo = AppTemplate.slug(obj.optString("repo").ifBlank { name })
        return Spec(name, repo, files)
    }

    // ---------- status / errors ----------

    suspend fun status(repo: String): String {
        val run = gh.latestRun(repo) ?: return "There are no builds yet for $repo."
        val name = repo.substringAfter('/')
        return when {
            run.status != "completed" -> "Still building. Try again in a minute."
            run.conclusion == "success" -> "The build passed. Say \"download apk of $name\"."
            else -> "The build failed. Say \"why did the build fail in $name\" or \"fix the build of $name\"."
        }
    }

    suspend fun whyFailed(repo: String): String {
        val run = gh.latestRun(repo) ?: return "There are no builds yet for $repo."
        if (run.status != "completed") return "It's still building."
        if (run.conclusion == "success") return "The latest build passed. Nothing failed."
        val errors = extractErrors(gh.failedLog(repo, run.id))
        return if (errors.isBlank()) "I couldn't read the error log. See ${run.url}"
        else "The build failed because:\n" + errors.take(1500)
    }

    private fun extractErrors(log: String): String =
        log.lines()
            .map { it.replace(Regex("^\\S+Z\\s"), "").trim() }
            .filter { it.startsWith("e: ") || it.contains("error:", true) || it.contains("What went wrong", true) }
            .distinct()
            .take(40)
            .joinToString("\n")
            .take(4000)

    // ---------- fix ----------

    suspend fun fix(repo: String, confirm: suspend (String) -> Boolean): String {
        val run = gh.latestRun(repo) ?: return "There are no builds yet for $repo."
        if (run.status != "completed") return "It's still building. Wait for it to finish first."
        if (run.conclusion == "success") return "The latest build passed. Nothing to fix."
        val errors = extractErrors(gh.failedLog(repo, run.id))
        if (errors.isBlank()) return "I couldn't read the error log. See ${run.url}"

        val names = gh.listFiles(repo, AppTemplate.SOURCE_DIR).filter { it.endsWith(".kt") }
        if (names.isEmpty()) return "I only know how to fix apps that BRO created."
        val current = buildString {
            for (n in names) {
                if (isNotEmpty()) append("\n\n")
                append("FILE: ").append(AppTemplate.SOURCE_DIR).append(n).append('\n')
                append(gh.readFile(repo, AppTemplate.SOURCE_DIR + n))
            }
        }
        val raw = gemini.generate(
            FIX_PROMPT, "Build errors:\n$errors\n\nCurrent files:\n$current", json = true, timeoutMs = 150000
        )
        val files = readFiles(parseJson(raw))
        if (!confirm("Push a fix for ${files.joinToString { it.path.substringAfterLast('/') }} in $repo?")) {
            return "Okay, I didn't push it."
        }
        for (f in files) gh.putFile(repo, f.path, f.content, "Fix build errors via BRO")
        return "Pushed the fix. A new build started. Say \"build status of ${repo.substringAfter('/')}\" in 2 minutes."
    }

    // ---------- helpers ----------

    private fun parseJson(raw: String): JSONObject =
        JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())

    private fun readFiles(obj: JSONObject): List<GenFile> {
        val arr = obj.optJSONArray("files") ?: throw IllegalStateException("The AI didn't return any files. Try again.")
        val out = mutableListOf<GenFile>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val path = o.getString("path").trim().trimStart('/')
            val content = o.getString("content")
            if (!path.startsWith(AppTemplate.SOURCE_DIR) || !path.endsWith(".kt") || ".." in path) {
                throw IllegalStateException("The AI used a file path I don't allow ($path). Try again.")
            }
            if (content.length > 60_000) throw IllegalStateException("The AI wrote a file that is too big. Try a simpler app.")
            out.add(GenFile(path, content))
        }
        if (out.isEmpty() || out.size > 12) throw IllegalStateException("The AI returned an unusable file list. Try again.")
        if (out.none { it.path.endsWith("/MainActivity.kt") }) {
            throw IllegalStateException("The AI forgot MainActivity.kt. Try again.")
        }
        return out
    }

    companion object {
        private const val RULES = """
            Target: Android, Kotlin 2.0, Jetpack Compose with Material3, compileSdk 35, minSdk 26.
            Rules (the code must compile on the first try):
            - Every file path must be app/src/main/java/com/bro/app/<Name>.kt and start with: package com.bro.app
            - There must be a MainActivity.kt with: class MainActivity : ComponentActivity(), calling setContent { ... }.
            - Write all imports explicitly. Do not use wildcard-only assumptions.
            - The ONLY libraries available: androidx.core:core-ktx, androidx.activity:activity-compose,
              kotlinx-coroutines-android, Compose ui, ui-graphics, foundation, animation, material3 (BOM 2024.10.01).
            - Do NOT use: material-icons (any), navigation-compose, viewModel(), Room, Retrofit, Coil, Hilt, or any other library.
              Use remember / mutableStateOf for state. For icons use Text symbols.
            - Material3 APIs marked experimental need @OptIn(ExperimentalMaterial3Api::class).
            - Do not use the INTERNET permission or anything that needs other permissions.
            - Make the UI work on a phone screen: use fillMaxSize, verticalScroll where content may overflow, and
              safeDrawingPadding() or systemBarsPadding().
            - Use a dark theme with darkColorScheme() unless the user asks otherwise.
            - Keep it small and simple (usually 1-3 files). No placeholders, no TODO, no "...".
            - Double-check types, imports and braces before answering.
        """

        private val CREATE_PROMPT = """
            You write small Android apps. Reply ONLY with JSON in this shape:
            {"app_name": "Short Name", "repo": "kebab-case-repo-name", "files": [{"path": "app/src/main/java/com/bro/app/MainActivity.kt", "content": "full file text"}]}
            $RULES
        """.trimIndent()

        private val FIX_PROMPT = """
            You fix Kotlin compile errors in a small Android Compose app. You get the build errors and the
            current source files. Reply ONLY with JSON in this shape:
            {"files": [{"path": "app/src/main/java/com/bro/app/MainActivity.kt", "content": "complete corrected file text"}]}
            Return only the files you changed, each one complete. Fix the root cause of every error listed.
            $RULES
        """.trimIndent()
    }
}
