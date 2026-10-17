package com.bro.assistant

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.bro.assistant.ai.GeminiClient
import com.bro.assistant.ai.GitHubClient
import com.bro.assistant.ai.GitHubException
import com.bro.assistant.memory.PreferencesStore
import com.bro.assistant.memory.SessionMemory
import org.json.JSONException

/**
 * Plain-language GitHub commands. Anything that changes GitHub (create repo, issue, push code)
 * asks for confirmation first. Returns null when the text is not a GitHub command.
 */
class GitHubCommands(private val context: Context, private val prefs: PreferencesStore) {

    private val client = GitHubClient(prefs)
    private val extras = GitHubExtras(client)
    private val builder = AppBuilder(prefs, client)

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)
    private val repoRef = "([\\w.-]+(?:/[\\w.-]+)?)"

    private val listRepos = rx("^(?:please\\s+)?(?:list|show|check|see|view|open|get|what\\s+are)\\s+(?:all\\s+)?(?:my\\s+|the\\s+)?(?:github\\s+)?repo(?:sitorie)?s?(?:\\s+(?:on|in|from)\\s+github)?$")
    private val createRepo = rx(
        "^create\\s+(?:a\\s+)?(?:new\\s+)?(private\\s+|public\\s+)?(?:github\\s+)?repo(?:sitory)?\\s+(?:called\\s+|named\\s+)?([\\w.-]+)$"
    )
    private val listFiles = rx("^(?:list|show)\\s+files\\s+(?:in|of)\\s+$repoRef(?:\\s+(?:folder|path)\\s+(\\S+))?$")
    private val readFile = rx("^(?:read|show|open)\\s+file\\s+(\\S+)\\s+(?:in|from|of)\\s+$repoRef$")
    private val issue = rx("^create\\s+(?:an\\s+)?issue\\s+(?:in|on)\\s+$repoRef\\s*[:\\-]\\s*(.+)$")
    private val push = rx(
        "^(?:push|upload|save|commit)\\s+(?:that\\s+|this\\s+|the\\s+|my\\s+|last\\s+)*code\\s+(?:to|in|into)\\s+$repoRef\\s+(?:as|in|at)\\s+(\\S+)$"
    )

    private val inRepo = "(?:in|on|of|for|from|to|into)\\s+$repoRef"
    private val listIssues = rx("^(?:list|show)\\s+(?:open\\s+)?issues\\s+$inRepo$")
    private val commentIssue = rx("^comment\\s+on\\s+issue\\s+#?(\\d+)\\s+$inRepo\\s*[:\\-]\\s*(.+)$")
    private val listCommits = rx("^(?:list|show)\\s+(?:recent\\s+)?commits\\s+$inRepo$")
    private val listPulls = rx("^(?:list|show)\\s+(?:open\\s+)?(?:pull\\s+requests|prs)\\s+$inRepo$")
    private val newBranch = rx("^create\\s+branch\\s+([\\w./-]+)\\s+$inRepo$")
    private val openPr = rx(
        "^(?:open|create)\\s+(?:a\\s+)?(?:pr|pull\\s+request)\\s+from\\s+([\\w./-]+)\\s+(?:to|into)\\s+([\\w./-]+)\\s+$inRepo(?:\\s*[:\\-]\\s*(.+))?$"
    )
    private val mergePr = rx("^merge\\s+(?:pr|pull\\s+request)\\s+#?(\\d+)\\s+$inRepo$")
    private val editFile = rx("^edit\\s+(?:file\\s+)?(\\S+)\\s+$inRepo\\s*[:\\-]\\s*(.+)$")

    private val renameRepo = rx("^rename\\s+repo(?:sitory)?\\s+$repoRef\\s+to\\s+([\\w.-]+)$")
    private val deleteRepo = rx("^delete\\s+repo(?:sitory)?\\s+$repoRef$")
    private val renameBranch = rx("^rename\\s+branch\\s+([\\w./-]+)\\s+to\\s+([\\w./-]+)\\s+$inRepo$")
    private val deleteBranch = rx("^delete\\s+branch\\s+([\\w./-]+)\\s+$inRepo$")
    private val renameFile = rx("^(?:rename|move)\\s+(?:file\\s+)?(\\S+)\\s+to\\s+(\\S+)\\s+$inRepo$")
    private val deleteFile = rx("^delete\\s+file\\s+(\\S+)\\s+$inRepo$")

    private val engineRx = rx("^(?:please\\s+)?(?:use|set|switch\\s+to|build\\s+apps?\\s+with)\\s+(?:the\\s+)?(all(?:\\s+three)?(?:\\s+(?:modes|at\\s+once|at\\s+a\\s+time|at\\s+the\\s+same\\s+time))?|api(?:\\s+keys?)?|cloud|offline(?:\\s+brain)?|both(?:\\s+as\\s+a\\s+team)?|team|api\\s+keys?\\s+and\\s+offline(?:\\s+brain)?(?:\\s+as\\s+a\\s+team)?)(?:\\s+(?:for|when)\\s+(?:building|making|creating)\\s+apps?)?$")
    private val engineAsk = rx("^(?:which|what)\\s+(?:brain|engine|mode)\\s+(?:do\\s+you\\s+use\\s+)?(?:for\\s+)?(?:building|making|creating)\\s+apps?$")

    private val createApp = Regex(
        "^(?:create|build|make|generate|develop)\\s+(?:me\\s+)?(?:an?\\s+|the\\s+)?(?:[\\w-]+\\s+){0,3}?(?:android\\s+app|apk|app|application)\\b" +
            "(?:\\s+(?:in|into)\\s+(?:repo\\s+)?([\\w.-]+))?\\s*(?:that|which|for|of|to|called|named|with|having|:|-)?\\s*(?:(?:these|this|the)\\s+features?\\s*[:\\-]?\\s*)?(.{12,})$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val buildStatus = rx("^(?:check\\s+)?(?:the\\s+)?build\\s+(?:status\\s+)?(?:of|for|in)\\s+$repoRef$")
    private val whyFailed = rx("^why\\s+(?:did\\s+)?(?:the\\s+)?build\\s+fail(?:ed)?\\s+(?:of|for|in)\\s+$repoRef$")
    private val fixBuild = rx("^fix\\s+(?:the\\s+)?build\\s+(?:of|for|in)\\s+$repoRef$")
    private val downloadApk = rx("^(?:download|get)\\s+(?:the\\s+)?apk\\s+(?:of|for|from)\\s+$repoRef$")

    private val all get() = listOf(engineRx, engineAsk, listRepos, createRepo, listFiles, readFile, issue, push, createApp, buildStatus, whyFailed, fixBuild, downloadApk,
        listIssues, commentIssue, listCommits, listPulls, newBranch, openPr, mergePr, editFile,
        renameRepo, deleteRepo, renameBranch, deleteBranch, renameFile, deleteFile)

    private val gitWords = rx(".*\\b(?:github|git\\s*hub|repo|repos|repository|repositories|branch|branches|commit|commits|pull\\s*request|prs?|issue|issues|workflow|actions|apk|merge)\\b.*")
    private val buildVerb = rx(".*\\b(?:build|make|create|generate|develop|write|code|design)\\b.*")
    private val appWord = rx(".*\\b(?:app|application|apk|android)\\b.*")
    private val ai = GeminiClient(prefs)

    private fun matchesAny(t: String) = all.any { it.matches(t) } || extras.matches(t)

    /** Free-form wording that mentions GitHub or app building: the AI rewrites it as one command BRO knows. */
    private suspend fun normalize(raw: String): String? {
        if (!prefs.hasBrain() || prefs.githubToken.isBlank()) return null
        if (!(gitWords.matches(raw) || (buildVerb.matches(raw) && appWord.matches(raw)))) return null
        return try {
            val out = ai.generate(NORMALIZE_PROMPT, "Message: $raw", json = true, timeoutMs = 25000)
            val cmd = org.json.JSONObject(out.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()).optString("command").trim()
            if (cmd.isBlank() || cmd.equals("none", true)) null else cmd.trimEnd('.', '!', '?')
        } catch (e: Exception) { null }
    }

    suspend fun tryHandle(
        rawText: String,
        lastCode: String?,
        confirm: suspend (String) -> Boolean
    ): String? {
        // people often type or paste the command inside quote marks
        var text = TeluguNormalizer.clause(TeluguNormalizer.preprocess(rawText.trim().trim('"', '\u201C', '\u201D', '\'', '\u2018', '\u2019')))
            .trim().trimEnd('.', '!', '?')
        if (!matchesAny(text)) {
            // any wording: short, long, casual, Telugu, Tenglish
            val canon = normalize(rawText)
            if (canon != null && matchesAny(canon)) text = canon
        }
        val isGithub = matchesAny(text)
        if (!isGithub) return null
        com.bro.assistant.Activity.step("Talking to GitHub")
        if (prefs.githubToken.isBlank()) {
            return "I need your GitHub token first. Add it in Menu, Settings, GitHub token."
        }
        return try {
            run(text, lastCode, confirm)
        } catch (e: GitHubException) {
            when (e.code) {
                401 -> "GitHub rejected the token (it starts with \"${prefs.githubToken.take(4)}\", ${prefs.githubToken.length} characters). It may be expired, deleted or copied wrongly. Make a new one at github.com/settings/tokens and paste it in Settings, GitHub token."
                403 -> "GitHub says no: ${e.message}. The token may be missing a permission. " +
                    "For apps and builds it needs both \"repo\" and \"workflow\"."
                404 -> "GitHub couldn't find that. Check the repo name and the token's access."
                422 -> "GitHub refused it: ${e.message}."
                else -> "GitHub error ${e.code}: ${e.message}"
            }
        } catch (e: AiException) {
            ErrorHandler.friendly(e)
        } catch (e: JSONException) {
            "The AI's answer wasn't valid. Please try again."
        } catch (e: IllegalStateException) {
            e.message ?: "Something went wrong. Please try again."
        } catch (e: java.net.SocketTimeoutException) {
            "The AI took too long to write that. Ask for a smaller first version, then add features one step at a time."
        } catch (e: java.net.UnknownHostException) {
            "I couldn't reach the internet. Check your connection and try again."
        } catch (e: java.io.IOException) {
            "A network problem stopped it (${e.javaClass.simpleName}). Please try again."
        }
    }

    private suspend fun run(text: String, lastCode: String?, confirm: suspend (String) -> Boolean): String {
        engineRx.matchEntire(text)?.let { m ->
            val w = m.groupValues[1].lowercase()
            val mode = when { w.startsWith("offline") -> "offline"; w.startsWith("api") && !w.contains("offline") -> "api"; w == "cloud" -> "api"; w.contains("all") || w.contains("three") || w.contains("same") -> "all"; else -> "team" }
            prefs.appBuildMode = mode
            return when (mode) {
                "api" -> "Okay. Apps will be written by your API keys only."
                "all" -> "Okay. Every app will be built in all three modes at the same time (API keys, offline brain, team), each in its own repo so you can compare."
                "offline" -> "Okay. Apps will be written by the offline brain only. It is small, so only simple one-screen apps will work."
                else -> "Okay. Apps will be built by API keys and the offline brain as a team: the cloud AI writes, the offline brain reviews, and each covers for the other if one is missing or fails."
            }
        }
        engineAsk.matchEntire(text)?.let {
            return "App building is set to: " + when (prefs.appBuildMode) { "all" -> "all three at the same time"; "api" -> "API keys only"; "offline" -> "offline brain only"; else -> "API keys and offline brain as a team" } +
                ". Say \"use api keys / use offline brain / use both as a team for building apps\" to change it."
        }
        listRepos.matchEntire(text)?.let {
            val list = client.repos()
            return if (list.isEmpty()) "You have no repositories." else "Your repositories:\n" + list.joinToString("\n")
        }
        createRepo.matchEntire(text)?.let { m ->
            val private = m.groupValues[1].trim().equals("private", true)
            val name = m.groupValues[2]
            if (!confirm("Create ${if (private) "private" else "public"} repo \"$name\" on GitHub?")) return "Okay, I didn't create it."
            return "Created: " + client.createRepo(name, private)
        }
        listFiles.matchEntire(text)?.let { m ->
            val files = client.listFiles(m.groupValues[1], m.groupValues[2])
            return if (files.isEmpty()) "That folder is empty." else files.joinToString("\n")
        }
        readFile.matchEntire(text)?.let { m ->
            val content = client.readFile(m.groupValues[2], m.groupValues[1])
            return "```\n" + content.take(3000) + "\n```" + if (content.length > 3000) "\n(shortened)" else ""
        }
        issue.matchEntire(text)?.let { m ->
            val repo = m.groupValues[1]
            val title = m.groupValues[2].trim()
            if (!confirm("Create issue \"$title\" in $repo?")) return "Okay, I didn't create it."
            return "Issue created: " + client.createIssue(repo, title)
        }
        push.matchEntire(text)?.let { m ->
            val code = lastCode ?: return "I don't have any code to push yet. Ask me to write some first."
            val repo = m.groupValues[1]
            val path = m.groupValues[2]
            if (!confirm("Commit this code to $repo as $path?")) return "Okay, I didn't push it."
            val what = client.putFile(repo, path, code, "Add $path via BRO")
            return "File $what in $repo: $path"
        }
        createApp.matchEntire(text)?.let { m ->
            val msg = builder.create(m.groupValues[2].trim(), m.groupValues[1].ifBlank { null }, confirm)
            val repos = Regex("github\\.com/([\\w.-]+/[\\w.-]+)").findAll(msg).map { it.groupValues[1] }.filter { !it.endsWith("/actions") }.distinct().toList()
            if (repos.isNotEmpty() && msg.startsWith("Done")) {
                // keep working after the answer: wait for each build, fix errors, tell the user when the APK is ready
                repos.forEachIndexed { i, repoFull ->
                    val w = com.bro.assistant.watch.Watch(
                        id = "appbuild-" + System.currentTimeMillis() + "-" + i, kind = "appbuild", goal = repoFull,
                        intervalMin = 3, nextRun = System.currentTimeMillis() + 150_000L + i * 20_000L, maxRuns = 25
                    )
                    com.bro.assistant.watch.WatchStore.put(context, w)
                    com.bro.assistant.watch.WatchScheduler.schedule(context, w)
                }
                return msg + "\n\nI'll keep working in the background: I'll watch ${if (repos.size > 1) "all ${repos.size} builds" else "the build"}, repair any compile errors myself (up to 3 times each) and send a notification when each APK is ready."
            }
            return msg
        }
        buildStatus.matchEntire(text)?.let { return builder.status(it.groupValues[1]) }
        whyFailed.matchEntire(text)?.let { return builder.whyFailed(it.groupValues[1]) }
        fixBuild.matchEntire(text)?.let { return builder.fix(it.groupValues[1], confirm) }
        downloadApk.matchEntire(text)?.let { m ->
            val repo = m.groupValues[1]
            val bytes = client.latestApk(repo) ?: return "There's no successful build with an APK yet. Say \"build status of $repo\"."
            val name = repo.substringAfter('/') + ".apk"
            return if (saveToDownloads(name, bytes)) {
                "Saved $name in your Downloads folder. Open Files, tap it, and allow the install."
            } else "I couldn't save the file on this phone."
        }
        listIssues.matchEntire(text)?.let { m ->
            val l = client.issues(m.groupValues[1])
            return if (l.isEmpty()) "No open issues." else "Open issues:\n" + l.joinToString("\n")
        }
        commentIssue.matchEntire(text)?.let { m ->
            val n = m.groupValues[1].toInt()
            val repo = m.groupValues[2]
            val body = m.groupValues[3].trim()
            if (!confirm("Comment \"$body\" on issue #$n in $repo?")) return "Okay, I didn't comment."
            client.commentOnIssue(repo, n, body)
            return "Comment added to issue #$n."
        }
        listCommits.matchEntire(text)?.let { m ->
            val l = client.commits(m.groupValues[1])
            return if (l.isEmpty()) "No commits." else "Recent commits:\n" + l.joinToString("\n")
        }
        listPulls.matchEntire(text)?.let { m ->
            val l = client.pulls(m.groupValues[1])
            return if (l.isEmpty()) "No open pull requests." else "Open pull requests:\n" + l.joinToString("\n")
        }
        newBranch.matchEntire(text)?.let { m ->
            val name = m.groupValues[1]
            val repo = m.groupValues[2]
            if (!confirm("Create branch \"$name\" in $repo?")) return "Okay, I didn't create it."
            return "Branch $name created from ${client.createBranch(repo, name)}."
        }
        openPr.matchEntire(text)?.let { m ->
            val head = m.groupValues[1]
            val base = m.groupValues[2]
            val repo = m.groupValues[3]
            val title = m.groupValues[4].trim().ifEmpty { "Merge $head into $base" }
            if (!confirm("Open pull request \"$title\" ($head into $base) in $repo?")) return "Okay, I didn't open it."
            return "Pull request opened: " + client.createPull(repo, head, base, title)
        }
        mergePr.matchEntire(text)?.let { m ->
            val n = m.groupValues[1].toInt()
            val repo = m.groupValues[2]
            if (!confirm("Merge pull request #$n in $repo?")) return "Okay, I didn't merge it."
            return "PR #$n: " + client.mergePull(repo, n)
        }
        editFile.matchEntire(text)?.let { m ->
            return editWithAi(m.groupValues[1], m.groupValues[2], m.groupValues[3].trim(), confirm)
        }
        renameRepo.matchEntire(text)?.let { m ->
            if (!confirm("Rename repo \"${m.groupValues[1]}\" to \"${m.groupValues[2]}\"? Old links redirect, but check apps that use the old name.")) return "Okay, I didn't rename it."
            return "Renamed: " + client.renameRepo(m.groupValues[1], m.groupValues[2])
        }
        deleteRepo.matchEntire(text)?.let { m ->
            if (!confirm("DELETE repo \"${m.groupValues[1]}\" forever? Its code, history and builds are lost and this cannot be undone.")) return "Okay, I didn't delete it."
            if (!confirm("Last check: really delete \"${m.groupValues[1]}\"?")) return "Okay, I didn't delete it."
            client.deleteRepo(m.groupValues[1])
            return "Deleted ${m.groupValues[1]}."
        }
        renameBranch.matchEntire(text)?.let { m ->
            if (!confirm("Rename branch ${m.groupValues[1]} to ${m.groupValues[2]} in ${m.groupValues[3]}?")) return "Okay."
            client.renameBranch(m.groupValues[3], m.groupValues[1], m.groupValues[2])
            return "Renamed branch ${m.groupValues[1]} to ${m.groupValues[2]}."
        }
        deleteBranch.matchEntire(text)?.let { m ->
            if (!confirm("Delete branch ${m.groupValues[1]} in ${m.groupValues[2]}?")) return "Okay, I didn't delete it."
            client.deleteBranch(m.groupValues[2], m.groupValues[1])
            return "Deleted branch ${m.groupValues[1]}."
        }
        renameFile.matchEntire(text)?.let { m ->
            if (!confirm("Rename ${m.groupValues[1]} to ${m.groupValues[2]} in ${m.groupValues[3]}?")) return "Okay."
            client.renameFile(m.groupValues[3], m.groupValues[1], m.groupValues[2])
            return "Renamed ${m.groupValues[1]} to ${m.groupValues[2]}."
        }
        deleteFile.matchEntire(text)?.let { m ->
            if (!confirm("Delete file ${m.groupValues[1]} from ${m.groupValues[2]}?")) return "Okay, I didn't delete it."
            client.deleteFile(m.groupValues[2], m.groupValues[1])
            return "Deleted ${m.groupValues[1]}."
        }
        extras.run(text, lastCode, confirm)?.let { return it }
        return "I didn't understand that GitHub command."
    }

    private suspend fun editWithAi(path: String, repo: String, instruction: String, confirm: suspend (String) -> Boolean): String {
        if (!prefs.hasBrain()) return "Add your Gemini API key or install the offline brain in Settings first."
        val original = client.readFile(repo, path)
        val answer = GeminiClient(prefs).generate(
            "You edit one source file. Apply the user's instruction and return the COMPLETE updated file in a single " +
                "fenced code block, with no other text. Keep everything else unchanged. The code must be correct and complete.",
            "File: $path\n\n```\n$original\n```\n\nInstruction: $instruction",
            json = false
        )
        val updated = SessionMemory.extractLastCode(answer) ?: return "I couldn't produce the updated file. Try rephrasing."
        if (updated.trim() == original.trim()) return "The file would stay the same, so I changed nothing."
        val delta = updated.lines().size - original.lines().size
        if (!confirm("Update $path in $repo ($instruction)? The file changes by ${if (delta >= 0) "+" else ""}$delta lines.")) {
            return "Okay, I didn't change it."
        }
        client.putFile(repo, path, updated + "\n", "Edit $path via BRO: ${instruction.take(60)}")
        return "Updated $path in $repo."
    }

    private fun saveToDownloads(name: String, bytes: ByteArray): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return true
    }

    companion object {
        private val NORMALIZE_PROMPT = """
            You turn a user's message (English, Telugu or Tenglish; short or long; any wording) into ONE command for a GitHub helper.
            Reply ONLY with JSON: {"command": "<command or none>"}.
            The command must follow exactly one of these forms (replace the <...>; repo names without spaces; "me" = the user's own account):
            list my repos
            create a private repo <name>
            create a public repo <name>
            list files in <repo>
            read file <path> in <repo>
            edit <path> in <repo>: <what to change>
            push code to <repo> as <filename>
            create branch <name> in <repo>
            list commits in <repo>
            list pull requests in <repo>
            open pr from <branch> to <branch> in <repo>: <title>
            merge pr <number> in <repo>
            create issue in <repo>: <title>
            list issues in <repo>
            comment on issue <number> in <repo>: <text>
            check build of <repo>
            why did build fail in <repo>
            fix build of <repo>
            download apk of <repo>
            build an android app that <full description of the app and every feature the user listed>
            rename repo <old> to <new>
            rename branch <old> to <new> in <repo>
            rename file <old> to <new> in <repo>
            delete repo <name>
            delete branch <name> in <repo>
            delete file <path> in <repo>
            fork repo <owner/repo>
            fork repo <owner/repo> as <new name>
            make repo <repo> private
            make repo <repo> public
            star repo <repo>
            unstar repo <repo>
            list starred repos
            create gist <filename>: <content>
            create gist from last code
            list gists
            delete gist <id>
            show notifications
            mark notifications read
            show my github profile
            follow <username>
            unfollow <username>
            list followers
            list following
            list ssh keys
            list gpg keys
            list webhooks in <repo>
            delete webhook <id> in <repo>
            list codespaces
            stop codespace <name>
            delete codespace <name>
            list packages
            list projects
            list discussions in <repo>
            rerun build in <repo>
            list workflows in <repo>
            run workflow <file> in <repo>
            Rules:
            - If the user wants an app made (any wording, e.g. "I need an app for tracking expenses", "make me a quiz app with timer"), use the
              "build an android app that ..." form and keep ALL their details.
            - If they only say "check my repo/github" without a name, use "list my repos".
            - If the message is not about GitHub, repos, builds or making an app, reply {"command": "none"}.
            - Never invent repo names; if a needed name is missing, reply {"command": "list my repos"}.
        """.trimIndent()
    }
}
