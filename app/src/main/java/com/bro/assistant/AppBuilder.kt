package com.bro.assistant

import com.bro.assistant.ai.DesignGuide
import com.bro.assistant.ai.GeminiClient
import com.bro.assistant.ai.GitHubClient
import com.bro.assistant.ai.GitHubException
import com.bro.assistant.memory.PreferencesStore
import com.bro.assistant.memory.SessionMemory
import kotlinx.coroutines.async
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
        if (mode == "all") return createAll(description, repoOverride, confirm)
        com.bro.assistant.Activity.step("Writing the app code")
        val spec = produce(description)
        val repo = repoOverride ?: spec.repo
        val q = "Create repo \"$repo\" with the Android app \"${spec.name}\" (${spec.files.size} Kotlin files) " +
            "and start the APK build?"
        if (!confirm(q)) return "Okay, I didn't create it."

        val full = publish(spec, repo)
        val how = when (mode) { "api" -> "API keys"; "offline" -> "offline brain"; else -> "API keys + offline brain as a team" }
        return "Done. I wrote your app (built with $how) and pushed it to GitHub:\nhttps://github.com/$full\n\n" +
            "Please check GitHub Actions now: https://github.com/$full/actions\n" +
            "It builds the APK in about 2 minutes. When it is green, say \"download apk of ${full.substringAfter('/')}\". " +
            "If it fails, say \"fix the build of ${full.substringAfter('/')}\" and I will repair it."
    }

    /** Creates (or reuses) the repo, pushes scaffolding and code, and the workflow last so exactly one build starts. Returns owner/name. */
    private suspend fun publish(spec: Spec, repo: String): String {
        com.bro.assistant.Activity.step("Pushing ${repo.substringAfter('/')} to GitHub")
        try {
            gh.createRepo(repo, private = false, autoInit = true)
        } catch (e: GitHubException) {
            if (e.code != 422) throw e // 422 = already exists: reuse it
        }
        val owner = gh.login()
        val full = if ("/" in repo) repo else "$owner/$repo"
        val scaffold = AppTemplate.files(spec.name, AppTemplate.slug(repo))
        for ((path, content) in scaffold.filter { it.first != AppTemplate.WORKFLOW_PATH }) {
            gh.putFile(full, path, content, "Add $path via BRO")
        }
        for (f in spec.files) gh.putFile(full, f.path, CodeLint.fix(f.content), "Add ${f.path.substringAfterLast('/')} via BRO")
        val wf = scaffold.first { it.first == AppTemplate.WORKFLOW_PATH }
        gh.putFile(full, wf.first, wf.second, "Add build workflow via BRO")
        return full
    }

    /** All three modes at the same time: API keys, offline brain and the team each build their own version in their own repo. */
    private suspend fun createAll(description: String, repoOverride: String?, confirm: suspend (String) -> Boolean): String {
        val cloud = cloudReady(); val off = offlineReady()
        if (!cloud && !off) throw IllegalStateException("I need an API key or the offline brain to build an app. Add one in Settings.")
        com.bro.assistant.Activity.step("All three at once: API keys and offline brain start together")
        val notes = ArrayList<String>()
        var specA: Spec? = null
        var specB: Spec? = null
        kotlinx.coroutines.coroutineScope {
            val a = if (cloud) async { try { generate(description) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { notes.add("API keys version failed: ${e.message?.take(80)}"); null } } else null
            val b = if (off) async { try { generateOffline(description) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { notes.add("Offline version failed: ${e.message?.take(80)}"); null } } else null
            specA = a?.await(); specB = b?.await()
        }
        if (!cloud) notes.add("No API key, so there is no API keys or team version.")
        if (!off) notes.add("No offline brain installed, so there is no offline or team version.")
        val variants = ArrayList<Pair<String, Spec>>()
        specA?.let { variants.add("api" to it) }
        specB?.let { variants.add("offline" to it) }
        if (specA != null && off) {
            com.bro.assistant.Activity.step("Team: offline brain reviews the API keys version")
            variants.add("team" to review(specA!!))
        } else if (specA == null && specB != null && cloud) notes.add("Team version skipped: the cloud AI failed, so the team would equal the offline version.")
        if (variants.isEmpty()) throw IllegalStateException("None of the three could write the app. " + notes.joinToString(" "))
        val base = repoOverride ?: variants.first().second.repo
        val names = variants.map { (k, _) -> "${AppTemplate.slug(base)}-$k" }
        if (!confirm("Create ${variants.size} repos (${names.joinToString()}) with the three versions of the app and start their builds?")) return "Okay, I didn't create them."
        val links = ArrayList<String>()
        variants.forEachIndexed { i, (k, spec) ->
            val full = publish(spec, names[i])
            links.add("${when (k) { "api" -> "API keys"; "offline" -> "Offline brain"; else -> "Team" }}: https://github.com/$full")
        }
        return "Done. I built the app in all three modes at the same time and pushed each to its own repo:\n" + links.joinToString("\n") +
            (if (notes.isNotEmpty()) "\n\nNote: " + notes.joinToString(" ") else "") +
            "\n\nCheck GitHub Actions in each repo. Compare them and keep the best."
    }

    private val mode get() = prefs.appBuildMode
    private fun offlineReady() = com.bro.assistant.ai.LocalLlm.isInstalled(prefs.appContext)
    private fun cloudReady() = prefs.apiKey.isNotBlank() || prefs.hasProviderKeys()

    /** Part 1 = API keys, part 2 = offline brain, part 3 = both as a team. */
    private suspend fun produce(description: String): Spec {
        when (mode) {
            "offline" -> return generateOffline(description)
            "api" -> {
                if (!cloudReady()) throw IllegalStateException("App building is set to API keys, but no API key is added. Add one in Settings, or say \"use both as a team for building apps\".")
                return generate(description)
            }
        }
        // team: the cloud AI writes, the offline brain reviews; each covers for the other when one is missing or fails
        val spec = if (cloudReady()) {
            try {
                com.bro.assistant.Activity.step("Team: API keys write the app")
                generate(description)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (!offlineReady()) throw e
                com.bro.assistant.Activity.step("Team: cloud AI failed, offline brain takes over")
                generateOffline(description)
            }
        } else if (offlineReady()) generateOffline(description)
        else throw IllegalStateException("I need an API key or the offline brain to build an app. Add one in Settings.")
        return if (offlineReady()) review(spec) else spec
    }

    /** Offline brain writes one small file. It has little memory, so only simple apps work in this mode. */
    private suspend fun generateOffline(description: String): Spec {
        if (!offlineReady()) throw IllegalStateException("The offline brain isn't installed. Add it in Settings, or use API keys.")
        com.bro.assistant.Activity.step("Offline brain writes a small app")
        val raw = gemini.generate(OFFLINE_PROMPT, "Write this app as ONE file: $description", json = false, engine = "offline")
        val code = SessionMemory.extractLastCode(raw)
        if (code == null || !code.contains("class MainActivity") || !code.contains("setContent")) {
            throw IllegalStateException("The offline brain couldn't write a working app (it is small and has little memory). Try API keys or team mode, or ask for something simpler.")
        }
        val name = description.split(Regex("\\s+")).filter { it.length > 2 }.take(3).joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            .replace(Regex("[^A-Za-z0-9 ]"), "").ifBlank { "My App" }.take(30)
        return Spec(name, AppTemplate.slug(name), listOf(GenFile(AppTemplate.SOURCE_DIR + "MainActivity.kt", CodeLint.fix(code))))
    }

    /** Second pair of eyes: the offline brain checks each small file and may correct it. Its change is kept only if it still looks like the same file. */
    private suspend fun review(spec: Spec): Spec {
        val out = ArrayList<GenFile>()
        var checked = 0
        for (f in spec.files) {
            if (checked >= 4 || f.content.length > 5000) { out.add(f); continue }
            checked++
            val fname = f.path.substringAfterLast('/')
            com.bro.assistant.Activity.step("Team: offline brain reviews $fname")
            val fixed = try {
                val raw = gemini.generate(REVIEW_PROMPT, "FILE $fname:\n```kotlin\n${f.content}\n```", json = false, engine = "offline")
                SessionMemory.extractLastCode(raw)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }
            val pkg = Regex("(?m)^package\\s+[\\w.]+").find(f.content)?.value
            val funs = Regex("\\bfun\\s+\\w+").findAll(f.content).count()
            val ok = fixed != null && (pkg == null || fixed.contains(pkg)) &&
                fixed.length in (f.content.length * 8 / 10)..(f.content.length * 125 / 100) &&
                Regex("\\bfun\\s+\\w+").findAll(fixed).count() >= funs &&
                fixed.count { it == '{' } == fixed.count { it == '}' }
            out.add(if (ok) GenFile(f.path, CodeLint.fix(fixed!!)) else f)
        }
        return spec.copy(files = out)
    }

    private suspend fun generate(description: String): Spec {
        // A long JSON answer can come back damaged (cut off or bad escaping), so one clean retry is allowed.
        var obj: JSONObject? = null
        var files: List<GenFile> = emptyList()
        var lastError: Exception? = null
        for (attempt in 1..2) {
            try {
                val raw = gemini.generate(
                    CREATE_PROMPT,
                    "Build this app: $description" + if (attempt == 2) "\n\nIMPORTANT: your last answer was not valid JSON. Reply with ONE complete JSON object only, " +
                        "escape every quote and newline inside file contents, and keep the app small (at most 6 files)." else "",
                    json = true, timeoutMs = 150000, engine = "cloud"
                )
                obj = parseJson(raw)
                files = readFiles(obj)
                break
            } catch (e: org.json.JSONException) {
                lastError = e
            }
        }
        if (obj == null) throw IllegalStateException("The AI's app answer was cut off or damaged twice. Try a smaller app, for example the main screen first, then add features one by one.")
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
        val engine = if (mode == "offline") "offline" else if (mode == "team" && !cloudReady()) "offline" else "cloud"
        val orig = LinkedHashMap<String, String>()
        for (n in names) orig[AppTemplate.SOURCE_DIR + n] = gh.readFile(repo, AppTemplate.SOURCE_DIR + n)
        // Fix only the files the compiler complained about, one at a time: a short plain-code answer each,
        // instead of one huge JSON with every file (that is what broke big apps).
        val errorLines = errors.lines()
        val targets = names.filter { n -> errorLines.any { it.contains(n) } }.ifEmpty { names }
        val declRx = Regex("(?m)^(?:(?:sealed|data|enum|abstract|open|private|internal)\\s+)*(?:class|object|interface|fun|val|var|typealias)\\s+[^\\n{=]{1,90}")
        val ai = mutableMapOf<String, String>()
        for (n in targets) {
            val path = AppTemplate.SOURCE_DIR + n
            val old = orig.getValue(path)
            val mine = errorLines.filter { it.contains(n) }.joinToString("\n").ifBlank { errors }.take(3500)
            val others = orig.filterKeys { it != path }.entries.joinToString("\n") { (k, v) ->
                k.substringAfterLast('/') + ": " + declRx.findAll(v).map { it.value.trim() }.take(14).joinToString("; ")
            }.take(1800)
            com.bro.assistant.Activity.step("Fixing $n")
            val reply = try {
                gemini.generate(
                    FIX_FILE_PROMPT, "Compile errors:\n$mine\n\nOther files in the project (never declare these names again here):\n$others\n\nFile $n:\n$old",
                    json = false, timeoutMs = 150000, engine = engine
                )
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { continue }
            val fenced = Regex("```[A-Za-z]*\\n([\\s\\S]*?)```").find(reply)?.groupValues?.get(1) ?: reply.trim().removePrefix("```kotlin").removeSuffix("```")
            val code = fenced.trim()
            // never push a half answer: it must be a whole Kotlin file of about the same size
            if (!code.contains("package com.bro.app") || code.length < old.length * 0.6) continue
            ai[path] = CodeLint.fix(code)
        }
        // certain repairs on every file, whatever the AI changed: same class declared in two files, missing imports, opt-ins
        val all = LinkedHashMap<String, String>()
        for ((path, content) in orig) all[path] = ai[path] ?: content
        for ((path, content) in CodeLint.dedupe(all)) { ai[path] = CodeLint.fix(content); all[path] = ai.getValue(path) }
        for ((path, content) in all) {
            if (path in ai) continue
            val fixed = CodeLint.fix(content)
            if (fixed != content) ai[path] = fixed
        }
        if (ai.isEmpty()) return "I couldn't find anything to change. See the errors with \"why did the build fail in ${repo.substringAfter('/')}\"."
        if (!confirm("Push a fix for ${ai.keys.joinToString { it.substringAfterLast('/') }} in $repo?")) {
            return "Okay, I didn't push it."
        }
        gh.commitFiles(repo, ai, "Fix build errors via BRO")
        return "Pushed the fix. A new build started. Say \"build status of ${repo.substringAfter('/')}\" in 2 minutes."
    }

    // ---------- helpers ----------

    private fun parseJson(raw: String): JSONObject {
        val t = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val a = t.indexOf('{'); val b = t.lastIndexOf('}')
        return JSONObject(if (a >= 0 && b > a) t.substring(a, b + 1) else t)
    }

    private fun readFiles(obj: JSONObject, requireMain: Boolean = true): List<GenFile> {
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
        if (out.isEmpty() || out.size > 25) throw IllegalStateException("The AI returned an unusable file list. Try again.")
        if (requireMain && out.none { it.path.endsWith("/MainActivity.kt") }) {
            throw IllegalStateException("The AI forgot MainActivity.kt. Try again.")
        }
        return out
    }

    companion object {
        private const val FIX_FILE_PROMPT =
            "You fix Kotlin / Jetpack Compose (Material3, Kotlin 2.0) compile errors. You get the compiler errors and ONE file. " +
                "Reply with ONLY the complete corrected file in a single ```kotlin block: keep everything that works, change only what the errors need, " +
                "add any missing imports, keep the first line 'package com.bro.app'. Do not shorten the file and do not explain. " +
                "Icons are NOT available (no material-icons): replace any Icon(...) or Icons.* with a Text symbol such as \"\u2190\". " +
                "Never import weight or align (they are members of Row/Column/Box). If a class, object or function is already declared in another file, delete it here and use it."
        private val RULES = """
            Target: Android, Kotlin 2.0, Jetpack Compose with Material3, compileSdk 35, minSdk 26.
            Rules (the code must compile on the first try):
            - Every file path must be app/src/main/java/com/bro/app/<Name>.kt and start with: package com.bro.app
            - There must be a MainActivity.kt with: class MainActivity : ComponentActivity(), calling setContent { ... }.
            - Write all imports explicitly: every Row, Column, Box, Modifier, padding, dp, remember, getValue/setValue (for `by`) etc.
              you use needs its import line. TopAppBar and other experimental Material3 APIs need @file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class) as the first line.
            - The ONLY libraries available: androidx.core:core-ktx, androidx.activity:activity-compose,
              kotlinx-coroutines-android, Compose ui, ui-graphics, foundation, animation, material3 (BOM 2024.10.01),
              lifecycle-viewmodel-compose and lifecycle-runtime-compose 2.8.7 (viewModel(), collectAsStateWithLifecycle()),
              and Room 2.6.1 with KSP (room-runtime, room-ktx, room-compiler).
            - Do NOT use: material-icons (any), navigation-compose, Retrofit, Coil, Hilt, WorkManager, or any other library.
              For icons use Text symbols. For screens use a simple enum/sealed class and `when`.
            - Room rules: @Database(entities = [...], version = 1, exportSchema = false); create it once with
              Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "name").build(); DAO functions are
              `suspend` or return Flow; never allowMainThreadQueries; run writes inside viewModelScope.launch / rememberCoroutineScope.
              Store dates as epoch-day Long or ISO text. java.time is available (minSdk 26).
            - To export or save a file (CSV etc.) use rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv"))
              and write with contentResolver.openOutputStream. Do not use file permissions.
            - NEVER invent facts, dates or numbers that the user did not give you (for example holiday dates, exam dates, fees).
              Put only user-given data in the code, and give the user an in-app screen to add, edit and delete such data.
            - Calculations must be exact. Keep them in a separate pure Kotlin file with no Android imports so they are easy to check.
            - Material3 APIs marked experimental need @OptIn(ExperimentalMaterial3Api::class).
            - Do not use the INTERNET permission or anything that needs other permissions.
            - Make the UI work on a phone screen: use fillMaxSize, verticalScroll where content may overflow, and
              safeDrawingPadding() or systemBarsPadding().
            - Use a dark theme with darkColorScheme() unless the user asks otherwise.
            - ${DesignGuide.GUIDE.replace("\n", "\n            ")}
            - Use as many files as the app needs (at most 20), each under 800 lines. No placeholders, no TODO, no "...".
            - Double-check types, imports and braces before answering.
        """

        private val CREATE_PROMPT = """
            You write small Android apps. Reply ONLY with JSON in this shape:
            {"app_name": "Short Name", "repo": "kebab-case-repo-name", "files": [{"path": "app/src/main/java/com/bro/app/MainActivity.kt", "content": "full file text"}]}
            $RULES
        """.trimIndent()

        private val OFFLINE_PROMPT = """
            You write a tiny Android app in Kotlin with Jetpack Compose Material3, as ONE file in ONE ```kotlin code block and nothing else.
            Start with: package com.bro.app. Include every import. Define class MainActivity : ComponentActivity() that calls setContent { }.
            Use only androidx.activity.compose, androidx.compose.foundation, androidx.compose.material3, androidx.compose.runtime, androidx.compose.ui.
            No other libraries, no icons, no network, no database. Keep it under 120 lines, dark theme, simple and correct.
        """.trimIndent()

        private val REVIEW_PROMPT = """
            You review one Kotlin Compose file for compile errors (missing imports, wrong types, unclosed braces).
            If it is correct, return it UNCHANGED. If you fix something, change as little as possible and return the COMPLETE file.
            Reply with ONE ```kotlin code block and nothing else.
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
