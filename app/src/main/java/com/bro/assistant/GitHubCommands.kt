package com.bro.assistant

import com.bro.assistant.ai.GeminiClient
import com.bro.assistant.ai.GitHubClient
import com.bro.assistant.ai.GitHubException
import com.bro.assistant.memory.PreferencesStore
import com.bro.assistant.memory.SessionMemory

/**
 * Plain-language GitHub commands. Anything that changes GitHub asks for confirmation first.
 * Returns null when the text is not a GitHub command.
 */
class GitHubCommands(private val prefs: PreferencesStore) {

    private val client = GitHubClient(prefs)

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)
    private val repoRef = "([\\w.-]+(?:/[\\w.-]+)?)"
    private val inRepo = "(?:in|on|of|for|from|to|into)\\s+$repoRef"

    private val listRepos = rx("^(?:list|show)\\s+(?:all\\s+)?(?:my\\s+)?(?:github\\s+)?repo(?:sitorie)?s$")
    private val createRepo = rx(
        "^create\\s+(?:a\\s+)?(?:new\\s+)?(private\\s+|public\\s+)?(?:github\\s+)?repo(?:sitory)?\\s+(?:called\\s+|named\\s+)?([\\w.-]+)$"
    )
    private val listFiles = rx("^(?:list|show)\\s+files\\s+$inRepo(?:\\s+(?:folder|path)\\s+(\\S+))?$")
    private val readFile = rx("^(?:read|show|open)\\s+file\\s+(\\S+)\\s+$inRepo$")
    private val issue = rx("^create\\s+(?:an\\s+)?issue\\s+$inRepo\\s*[:\\-]\\s*(.+)$")
    private val push = rx(
        "^(?:push|upload|save|commit)\\s+(?:that\\s+|this\\s+|the\\s+|my\\s+|last\\s+)*code\\s+(?:to|in|into)\\s+$repoRef\\s+(?:as|in|at)\\s+(\\S+)$"
    )
    private val makeApk = rx(
        "^(?:make|build|create|generate)\\s+(?:an?\\s+)?(?:apk|android\\s+app)(?:\\s+from\\s+(?:that|this|the|last)\\s+(?:code|app|html))?\\s+$inRepo$"
    )
    private val buildStatus = rx("^(?:check|show|get)\\s+(?:the\\s+)?(?:build|apk)(?:\\s+status)?\\s+$inRepo$")
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

    private val all = listOf(
        listRepos, createRepo, listFiles, readFile, issue, push, makeApk, buildStatus, listIssues,
        commentIssue, listCommits, listPulls, newBranch, openPr, mergePr, editFile
    )

    suspend fun tryHandle(
        rawText: String,
        lastCode: String?,
        confirm: suspend (String) -> Boolean
    ): String? {
        val text = TeluguNormalizer.clause(TeluguNormalizer.preprocess(rawText))
            .trim().trimEnd('.', '!', '?')
        if (all.none { it.matches(text) }) return null
        if (prefs.githubToken.isBlank()) {
            return "I need your GitHub token first. Add it in Menu, Settings, GitHub token."
        }
        return try {
            run(text, lastCode, confirm)
        } catch (e: GitHubException) {
            when (e.code) {
                401 -> "GitHub rejected the token. Check it in Settings."
                403 -> "GitHub says no: ${e.message}. The token may be missing a permission."
                404 -> "GitHub couldn't find that. Check the repo name, and that the token has access."
                422 -> "GitHub refused it: ${e.message}."
                else -> "GitHub error ${e.code}: ${e.message}"
            }
        } catch (e: java.io.IOException) {
            "I couldn't reach GitHub. Check your internet connection."
        }
    }

    private fun lines(title: String, items: List<String>, empty: String) =
        if (items.isEmpty()) empty else title + "\n" + items.joinToString("\n")

    private suspend fun run(text: String, lastCode: String?, confirm: suspend (String) -> Boolean): String {
        listRepos.matchEntire(text)?.let { return lines("Your repositories:", client.repos(), "You have no repositories.") }

        createRepo.matchEntire(text)?.let { m ->
            val private = m.groupValues[1].trim().equals("private", true)
            val name = m.groupValues[2]
            if (!confirm("Create ${if (private) "private" else "public"} repo \"$name\" on GitHub?")) return "Okay, I didn't create it."
            return "Created: " + client.createRepo(name, private)
        }

        listFiles.matchEntire(text)?.let { m ->
            return lines("Files:", client.listFiles(m.groupValues[1], m.groupValues[2]), "That folder is empty.")
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

        makeApk.matchEntire(text)?.let { m -> return buildApk(m.groupValues[1], lastCode, confirm) }

        buildStatus.matchEntire(text)?.let { m ->
            val b = client.latestBuild(m.groupValues[1]) ?: return "No build has run in ${m.groupValues[1]} yet."
            return when {
                b.status != "completed" -> "Still building (${b.status}). Ask again in a minute.\n${b.url}"
                b.conclusion == "success" ->
                    "Build succeeded. Open this link in your browser and download " +
                        (b.artifacts.firstOrNull() ?: "the APK") + " under Artifacts, then unzip it and install the .apk:\n" + b.url
                else -> "The build failed (${b.conclusion}). Details:\n${b.url}"
            }
        }

        listIssues.matchEntire(text)?.let { m -> return lines("Open issues:", client.issues(m.groupValues[1]), "No open issues.") }

        commentIssue.matchEntire(text)?.let { m ->
            val n = m.groupValues[1].toInt()
            val repo = m.groupValues[2]
            val body = m.groupValues[3].trim()
            if (!confirm("Comment \"$body\" on issue #$n in $repo?")) return "Okay, I didn't comment."
            client.commentOnIssue(repo, n, body)
            return "Comment added to issue #$n."
        }

        listCommits.matchEntire(text)?.let { m -> return lines("Recent commits:", client.commits(m.groupValues[1]), "No commits.") }

        listPulls.matchEntire(text)?.let { m -> return lines("Open pull requests:", client.pulls(m.groupValues[1]), "No open pull requests.") }

        newBranch.matchEntire(text)?.let { m ->
            val name = m.groupValues[1]
            val repo = m.groupValues[2]
            if (!confirm("Create branch \"$name\" in $repo?")) return "Okay, I didn't create it."
            val base = client.createBranch(repo, name)
            return "Branch $name created from $base."
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

        editFile.matchEntire(text)?.let { m -> return editWithAi(m.groupValues[1], m.groupValues[2], m.groupValues[3].trim(), confirm) }

        return "I didn't understand that GitHub command."
    }

    private suspend fun buildApk(repo: String, lastCode: String?, confirm: suspend (String) -> Boolean): String {
        val html = lastCode ?: return "I don't have any code yet. Ask me to write an HTML app first."
        if (!html.contains("<html", true) && !html.contains("<!doctype", true)) {
            return "I can turn HTML apps into an APK. The last code isn't HTML. Ask me for an HTML version first."
        }
        if (!confirm("Create/update $repo with an Android project for this app and start the APK build?")) {
            return "Okay, I didn't build it."
        }
        val name = repo.substringAfter('/')
        try {
            client.createRepo(name, false)
        } catch (e: GitHubException) {
            if (e.code != 422) throw e // 422 = it already exists, which is fine
        }
        val files = ApkTemplate.files(repo, html)
        var done = 0
        for ((path, content) in files) {
            try {
                client.putFile(repo, path, content, "Add $path via BRO")
            } catch (e: GitHubException) {
                if (path.startsWith(".github/") && (e.code == 403 || e.code == 404)) {
                    return "I pushed the app files, but GitHub blocked the build file. Your token needs the \"workflow\" " +
                        "permission too. Edit the token on github.com (Settings, Developer settings, Tokens), tick " +
                        "workflow, save it, then say this command again."
                }
                throw e
            }
            done++
        }
        return "Pushed $done files to $repo. GitHub is now building the APK (about 2 minutes). " +
            "Say \"check build in $name\" to get the download link."
    }

    private suspend fun editWithAi(path: String, repo: String, instruction: String, confirm: suspend (String) -> Boolean): String {
        if (prefs.apiKey.isBlank()) return "Add your Gemini API key in Settings first."
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
}
