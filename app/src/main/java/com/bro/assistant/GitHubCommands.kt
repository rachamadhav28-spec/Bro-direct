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
    private val builder = AppBuilder(prefs, client)

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)
    private val repoRef = "([\\w.-]+(?:/[\\w.-]+)?)"

    private val listRepos = rx("^(?:list|show)\\s+(?:all\\s+)?(?:my\\s+)?(?:github\\s+)?repo(?:sitorie)?s$")
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

    private val createApp = Regex(
        "^(?:create|build|make|generate|develop)\\s+(?:me\\s+)?(?:an?\\s+|the\\s+)?(?:[\\w-]+\\s+){0,3}?(?:android\\s+app|apk)\\b" +
            "(?:\\s+(?:in|into)\\s+(?:repo\\s+)?([\\w.-]+))?\\s*(?:that|which|for|of|to|called|named|:|-)?\\s*(.+)$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val buildStatus = rx("^(?:check\\s+)?(?:the\\s+)?build\\s+(?:status\\s+)?(?:of|for|in)\\s+$repoRef$")
    private val whyFailed = rx("^why\\s+(?:did\\s+)?(?:the\\s+)?build\\s+fail(?:ed)?\\s+(?:of|for|in)\\s+$repoRef$")
    private val fixBuild = rx("^fix\\s+(?:the\\s+)?build\\s+(?:of|for|in)\\s+$repoRef$")
    private val downloadApk = rx("^(?:download|get)\\s+(?:the\\s+)?apk\\s+(?:of|for|from)\\s+$repoRef$")

    suspend fun tryHandle(
        rawText: String,
        lastCode: String?,
        confirm: suspend (String) -> Boolean
    ): String? {
        val text = TeluguNormalizer.clause(TeluguNormalizer.preprocess(rawText))
            .trim().trimEnd('.', '!', '?')
        val isGithub = listOf(listRepos, createRepo, listFiles, readFile, issue, push, createApp, buildStatus, whyFailed, fixBuild, downloadApk,
            listIssues, commentIssue, listCommits, listPulls, newBranch, openPr, mergePr, editFile).any { it.matches(text) }
        if (!isGithub) return null
        if (prefs.githubToken.isBlank()) {
            return "I need your GitHub token first. Add it in Menu, Settings, GitHub token."
        }
        return try {
            run(text, lastCode, confirm)
        } catch (e: GitHubException) {
            when (e.code) {
                401 -> "GitHub rejected the token. Check it in Settings."
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
        } catch (e: java.io.IOException) {
            "I couldn't reach GitHub. Check your internet connection."
        }
    }

    private suspend fun run(text: String, lastCode: String?, confirm: suspend (String) -> Boolean): String {
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
            return builder.create(m.groupValues[2].trim(), m.groupValues[1].ifBlank { null }, confirm)
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
        return "I didn't understand that GitHub command."
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
}
